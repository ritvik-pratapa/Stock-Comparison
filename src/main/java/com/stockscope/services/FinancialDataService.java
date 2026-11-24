package com.stockscope.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockscope.api.SecApiClient;
import com.stockscope.api.SecApiException;
import com.stockscope.models.Company;
import com.stockscope.models.CompanyFinancials;
import com.stockscope.models.FinancialData;

import java.time.LocalDate;
import java.time.Month;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Turns the SEC's raw "Company Facts" JSON into clean, one-row-per-year financial data.
 *
 * The SEC JSON is organized by XBRL tag (the accounting name for each number):
 *
 *   facts -> us-gaap -> "Revenues" -> units -> USD -> [ list of reported values ]
 *
 * Each reported value looks like:
 *   {"start": "2022-09-25", "end": "2023-09-30", "val": 383285000000,
 *    "form": "10-K", "filed": "2023-11-03", "fy": 2023, "fp": "FY"}
 *
 * Three things make this tricky, and each one is handled below:
 *
 *   1. The same number appears many times. A 10-K includes the two previous years too,
 *      so Apple's 2023 revenue shows up in three different filings. Worse, the "fy" field
 *      describes the FILING, not the number - all three copies have a different "fy".
 *      Fix: group values by their own period end date, and keep the most recently filed.
 *
 *   2. Annual reports also contain quarterly numbers (e.g. "Q4 revenue").
 *      Fix: for income statement items, only keep periods lasting about one year.
 *
 *   3. Companies use different tags for the same idea, and sometimes switch tags.
 *      Fix: for each metric, try a list of tags in priority order, year by year.
 */
public class FinancialDataService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // Tags to try for each metric, best first. "Revenues" is first because it's the total;
    // RevenueFromContractWithCustomer... can leave out things like insurance premiums.
    private static final List<String> REVENUE_TAGS = List.of(
            "Revenues",
            "RevenueFromContractWithCustomerExcludingAssessedTax",
            "SalesRevenueNet",
            "RevenueFromContractWithCustomerIncludingAssessedTax");
    private static final List<String> NET_INCOME_TAGS = List.of("NetIncomeLoss", "ProfitLoss");
    private static final List<String> OPERATING_INCOME_TAGS = List.of("OperatingIncomeLoss");
    private static final List<String> ASSETS_TAGS = List.of("Assets");
    private static final List<String> LIABILITIES_TAGS = List.of("Liabilities");
    private static final List<String> EQUITY_TAGS = List.of(
            "StockholdersEquity",
            "StockholdersEquityIncludingPortionAttributableToNoncontrollingInterest");
    // Total equity (including minority owners' share) is only used to work out
    // liabilities for companies that don't report total liabilities.
    private static final List<String> TOTAL_EQUITY_TAGS = List.of(
            "StockholdersEquityIncludingPortionAttributableToNoncontrollingInterest",
            "StockholdersEquity");
    private static final List<String> CASH_TAGS = List.of(
            "CashAndCashEquivalentsAtCarryingValue",
            "CashCashEquivalentsRestrictedCashAndRestrictedCashEquivalents",
            "CashAndDueFromBanks");

    // A fiscal year is 52 or 53 weeks (364 or 371 days), so allow a little slack.
    private static final int MIN_DAYS_IN_YEAR = 350;
    private static final int MAX_DAYS_IN_YEAR = 380;

    private final SecApiClient secClient;

    public FinancialDataService(SecApiClient secClient) {
        this.secClient = secClient;
    }

    /** Downloads (or loads from cache) a company's facts and parses them. */
    public CompanyFinancials getFinancials(Company company) throws SecApiException {
        String json;
        try {
            json = secClient.fetchCompanyFactsJson(company);
        } catch (SecApiException e) {
            if (e.getStatusCode() == 404) {
                throw new SecApiException("The SEC has no financial statement data for " + company.ticker()
                        + ". This is common for foreign companies and funds that don't file 10-K reports.", 404);
            }
            throw e;
        }
        return parse(company, json);
    }

    /** Parses Company Facts JSON. Kept separate from downloading so tests can use sample JSON. */
    static CompanyFinancials parse(Company company, String json) throws SecApiException {
        JsonNode usGaap;
        try {
            usGaap = MAPPER.readTree(json).path("facts").path("us-gaap");
        } catch (JsonProcessingException e) {
            throw new SecApiException("The SEC data for " + company.ticker() + " was not valid JSON.", e);
        }

        // Each map goes from "period end date" to "value reported for that period".
        Map<LocalDate, Long> revenue = annualValues(usGaap, REVENUE_TAGS, true);
        Map<LocalDate, Long> netIncome = annualValues(usGaap, NET_INCOME_TAGS, true);
        Map<LocalDate, Long> operatingIncome = annualValues(usGaap, OPERATING_INCOME_TAGS, true);
        Map<LocalDate, Long> assets = annualValues(usGaap, ASSETS_TAGS, false);
        Map<LocalDate, Long> liabilities = annualValues(usGaap, LIABILITIES_TAGS, false);
        Map<LocalDate, Long> equity = annualValues(usGaap, EQUITY_TAGS, false);
        Map<LocalDate, Long> totalEquity = annualValues(usGaap, TOTAL_EQUITY_TAGS, false);
        Map<LocalDate, Long> cash = annualValues(usGaap, CASH_TAGS, false);

        // The fiscal years are the periods covered by the income statement. Balance sheet
        // values are then matched to each year's end date. (A TreeSet keeps dates sorted.)
        TreeSet<LocalDate> yearEnds = new TreeSet<>(revenue.keySet());
        yearEnds.addAll(netIncome.keySet());
        if (yearEnds.isEmpty()) {
            throw new SecApiException("No annual (10-K) financial data was found for " + company.ticker() + ".");
        }

        // Keyed by fiscal year, so if two dates ever map to the same year, the later one wins.
        TreeMap<Integer, FinancialData> byYear = new TreeMap<>();

        for (LocalDate end : yearEnds) {
            Long yearLiabilities = liabilities.get(end);
            boolean liabilitiesCalculated = false;
            // Some companies (e.g. Amazon, Walmart) don't report a "total liabilities" line.
            // Every balance sheet satisfies Assets = Liabilities + Equity, so we can work it out.
            if (yearLiabilities == null && assets.get(end) != null && totalEquity.get(end) != null) {
                yearLiabilities = assets.get(end) - totalEquity.get(end);
                liabilitiesCalculated = true;
            }

            int fiscalYear = fiscalYearOf(end);
            byYear.put(fiscalYear, new FinancialData(fiscalYear, end,
                    revenue.get(end), netIncome.get(end), operatingIncome.get(end),
                    assets.get(end), yearLiabilities, equity.get(end), cash.get(end),
                    liabilitiesCalculated));
        }

        return new CompanyFinancials(company, new ArrayList<>(byYear.values()));
    }

    /**
     * Collects one value per period end date for a metric, trying each tag in priority
     * order. A lower-priority tag only fills in dates the earlier tags didn't have.
     */
    private static Map<LocalDate, Long> annualValues(JsonNode usGaap, List<String> tags, boolean isDuration) {
        Map<LocalDate, Long> result = new HashMap<>();
        for (String tag : tags) {
            Map<LocalDate, Long> tagValues = valuesForTag(usGaap.path(tag), isDuration);
            for (Map.Entry<LocalDate, Long> entry : tagValues.entrySet()) {
                result.putIfAbsent(entry.getKey(), entry.getValue());
            }
        }
        return result;
    }

    /** A reported value plus the date it was filed, used to pick the newest copy. */
    private record ReportedValue(long value, String filed) {
    }

    /**
     * Reads one tag's values from annual reports only, keeping the most recently filed
     * value for each period (in case a later report corrected an earlier number).
     *
     * @param isDuration true for income statement items, which cover a period of time
     *                   (start to end); false for balance sheet items, which are a
     *                   snapshot on a single date
     */
    private static Map<LocalDate, Long> valuesForTag(JsonNode tagNode, boolean isDuration) {
        Map<LocalDate, ReportedValue> newest = new HashMap<>();

        for (JsonNode entry : tagNode.path("units").path("USD")) {
            String form = entry.path("form").asText();
            if (!form.equals("10-K") && !form.equals("10-K/A")) {
                continue; // skip quarterly reports (10-Q) and other forms
            }

            LocalDate end = parseDate(entry.path("end").asText());
            if (end == null || !entry.hasNonNull("val")) {
                continue; // skip malformed entries instead of crashing
            }

            if (isDuration) {
                LocalDate start = parseDate(entry.path("start").asText());
                if (start == null) {
                    continue;
                }
                long days = ChronoUnit.DAYS.between(start, end);
                if (days < MIN_DAYS_IN_YEAR || days > MAX_DAYS_IN_YEAR) {
                    continue; // a quarterly or partial-year number inside an annual report
                }
            }

            ReportedValue candidate = new ReportedValue(entry.path("val").asLong(), entry.path("filed").asText());
            ReportedValue current = newest.get(end);
            // Dates in "YYYY-MM-DD" format sort correctly as plain text.
            if (current == null || candidate.filed().compareTo(current.filed()) > 0) {
                newest.put(end, candidate);
            }
        }

        Map<LocalDate, Long> values = new HashMap<>();
        for (Map.Entry<LocalDate, ReportedValue> entry : newest.entrySet()) {
            values.put(entry.getKey(), entry.getValue().value());
        }
        return values;
    }

    /**
     * The fiscal year is the year the period ends in: Apple's year ending Sept 2025 is
     * fiscal 2025. One exception: some companies use 52/53-week years that end a few days
     * into January, so a year ending Jan 2, 2022 is really fiscal 2021.
     */
    static int fiscalYearOf(LocalDate periodEnd) {
        if (periodEnd.getMonth() == Month.JANUARY && periodEnd.getDayOfMonth() <= 7) {
            return periodEnd.getYear() - 1;
        }
        return periodEnd.getYear();
    }

    /** Returns null instead of throwing if the text isn't a valid date. */
    private static LocalDate parseDate(String text) {
        try {
            return LocalDate.parse(text);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
