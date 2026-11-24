package com.stockscope.services;

import com.stockscope.api.SecApiException;
import com.stockscope.models.Company;
import com.stockscope.models.CompanyFinancials;
import com.stockscope.models.FinancialData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the SEC JSON parsing using a small hand-made sample in the SEC's real format.
 * Each entry in the sample is there to check one specific rule (see the comments).
 */
class FinancialDataServiceTest {

    private static final Company SAMPLE_COMPANY = new Company("SMPL", 1, "Sample Corp");

    private static final String SAMPLE_FACTS = """
            {"cik": 1, "entityName": "Sample Corp", "facts": {"us-gaap": {
              "Revenues": {"units": {"USD": [
                {"start": "2022-01-01", "end": "2022-12-31", "val": 1000, "form": "10-K", "filed": "2023-02-01"},
                {"start": "2022-01-01", "end": "2022-12-31", "val": 1100, "form": "10-K", "filed": "2024-02-01"},
                {"start": "2022-10-01", "end": "2022-12-31", "val": 300,  "form": "10-K", "filed": "2023-02-01"},
                {"start": "2023-01-01", "end": "2023-03-31", "val": 280,  "form": "10-Q", "filed": "2023-05-01"}
              ]}},
              "RevenueFromContractWithCustomerExcludingAssessedTax": {"units": {"USD": [
                {"start": "2022-01-01", "end": "2022-12-31", "val": 999,  "form": "10-K", "filed": "2024-02-01"},
                {"start": "2023-01-01", "end": "2023-12-31", "val": 1200, "form": "10-K", "filed": "2024-02-01"}
              ]}},
              "NetIncomeLoss": {"units": {"USD": [
                {"start": "2022-01-01", "end": "2022-12-31", "val": 100, "form": "10-K", "filed": "2023-02-01"},
                {"start": "2023-01-01", "end": "2023-12-31", "val": -50, "form": "10-K", "filed": "2024-02-01"}
              ]}},
              "Assets": {"units": {"USD": [
                {"end": "2021-12-31", "val": 900,  "form": "10-K", "filed": "2023-02-01"},
                {"end": "2022-12-31", "val": 1000, "form": "10-K", "filed": "2023-02-01"},
                {"end": "2023-12-31", "val": 1200, "form": "10-K", "filed": "2024-02-01"}
              ]}},
              "StockholdersEquity": {"units": {"USD": [
                {"end": "2022-12-31", "val": 400, "form": "10-K", "filed": "2023-02-01"},
                {"end": "2023-12-31", "val": 500, "form": "10-K", "filed": "2024-02-01"}
              ]}},
              "Liabilities": {"units": {"USD": [
                {"end": "2023-12-31", "val": 700, "form": "10-K", "filed": "2024-02-01"}
              ]}}
            }}}
            """;

    private static List<FinancialData> years;
    private static CompanyFinancials financials;

    @BeforeAll
    static void parseSample() throws SecApiException {
        financials = FinancialDataService.parse(SAMPLE_COMPANY, SAMPLE_FACTS);
        years = financials.years();
    }

    @Test
    void fiscalYearsComeFromTheIncomeStatement() {
        // Assets has a 2021 value, but there's no 2021 income statement, so no 2021 row.
        assertEquals(2, years.size());
        assertEquals(2022, years.get(0).fiscalYear());
        assertEquals(2023, years.get(1).fiscalYear());
        assertEquals(LocalDate.of(2023, 12, 31), financials.latestYear().periodEnd());
    }

    @Test
    void keepsMostRecentlyFiledValueAndIgnoresQuarters() {
        // 2022 revenue was 1000, later restated to 1100. The Q4-only value (300),
        // the 10-Q value (280), and the lower-priority tag (999) must all be ignored.
        assertEquals(1100L, years.get(0).revenue());
    }

    @Test
    void fallsBackToNextTagForMissingYears() {
        // "Revenues" has no 2023 value, so the second tag in the priority list is used.
        assertEquals(1200L, years.get(1).revenue());
    }

    @Test
    void keepsNegativeValues() {
        assertEquals(-50L, years.get(1).netIncome()); // a net loss
    }

    @Test
    void missingMetricIsNullNotZero() {
        assertNull(years.get(0).operatingIncome());
        assertNull(years.get(0).cash());
    }

    @Test
    void calculatesLiabilitiesWhenNotReported() {
        assertEquals(600L, years.get(0).totalLiabilities()); // 1000 assets - 400 equity
        assertTrue(years.get(0).liabilitiesCalculated());

        assertEquals(700L, years.get(1).totalLiabilities()); // reported directly
        assertFalse(years.get(1).liabilitiesCalculated());
    }

    @Test
    void recentYearsReturnsNewestYearsOldestFirst() {
        assertEquals(1, financials.recentYears(1).size());
        assertEquals(2023, financials.recentYears(1).get(0).fiscalYear());
        assertEquals(2, financials.recentYears(5).size()); // asking for more than exist is fine
    }

    @Test
    void companyWithNoAnnualDataGivesClearError() {
        String noData = """
                {"cik": 1, "entityName": "Empty Corp", "facts": {"dei": {}}}
                """;
        SecApiException error = assertThrows(SecApiException.class,
                () -> FinancialDataService.parse(SAMPLE_COMPANY, noData));
        assertTrue(error.getMessage().contains("No annual"));
    }

    @Test
    void invalidJsonGivesClearError() {
        assertThrows(SecApiException.class, () -> FinancialDataService.parse(SAMPLE_COMPANY, "not json"));
    }

    @Test
    void fiscalYearHandlesEarlyJanuaryYearEnds() {
        assertEquals(2025, FinancialDataService.fiscalYearOf(LocalDate.of(2025, 9, 27)));  // Apple-style
        assertEquals(2026, FinancialDataService.fiscalYearOf(LocalDate.of(2026, 1, 25)));  // NVIDIA-style
        assertEquals(2021, FinancialDataService.fiscalYearOf(LocalDate.of(2022, 1, 2)));   // 52/53-week year
    }
}
