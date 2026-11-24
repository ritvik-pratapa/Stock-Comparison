package com.stockscope.ui;

import com.stockscope.models.CompanyFinancials;
import com.stockscope.models.FinancialData;
import com.stockscope.services.ComparisonService;
import com.stockscope.services.FinancialRatioService;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import static com.stockscope.services.ComparisonService.latestRatios;
import static com.stockscope.utils.FinancialFormatter.money;
import static com.stockscope.utils.FinancialFormatter.percent;
import static com.stockscope.utils.FinancialFormatter.ratio;

/**
 * Prints StockScope's reports to the console. Keeping all the printing here means
 * Main only has to deal with menus and user input.
 */
public final class ReportPrinter {

    private static final int YEARS_IN_DETAIL_VIEW = 5;

    private ReportPrinter() {
    }

    /** The side-by-side comparison of each company's latest fiscal year. */
    public static void printComparison(List<CompanyFinancials> companies) {
        System.out.println();
        System.out.println("========== COMPANY COMPARISON ==========");
        for (CompanyFinancials company : companies) {
            System.out.printf("  %-7s %s%n", company.company().ticker(), company.company().name());
        }
        System.out.println();

        printRow("Metric", companies, company -> company.company().ticker());
        printRow("Latest Fiscal Year", companies, company -> "FY" + company.latestYear().fiscalYear());
        printRow("Period Ended", companies, company -> company.latestYear().periodEnd().toString());
        System.out.println("  --- Reported in 10-K filings ---");
        printRow("Revenue", companies, company -> money(company.latestYear().revenue()));
        printRow("Net Income", companies, company -> money(company.latestYear().netIncome()));
        printRow("Operating Income", companies, company -> money(company.latestYear().operatingIncome()));
        printRow("Total Assets", companies, company -> money(company.latestYear().totalAssets()));
        printRow("Total Liabilities", companies, company -> liabilities(company.latestYear()));
        System.out.println("  --- Calculated by StockScope ---");
        printRow("Net Profit Margin", companies, company -> percent(latestRatios(company).netProfitMargin()));
        printRow("Operating Margin", companies, company -> percent(latestRatios(company).operatingMargin()));
        printRow("Return on Assets", companies, company -> percent(latestRatios(company).returnOnAssets()));
        printRow("Debt-to-Equity", companies, company -> ratio(latestRatios(company).debtToEquity()));

        List<FinancialData> latestYears = companies.stream().map(CompanyFinancials::latestYear).toList();
        printLiabilitiesFootnote(latestYears);
        long distinctYearEnds = latestYears.stream().map(FinancialData::periodEnd).distinct().count();
        if (distinctYearEnds > 1) {
            System.out.println("  Note: these companies' fiscal years end on different dates (see \"Period Ended\"),");
            System.out.println("        so each column covers a slightly different 12-month period.");
        }

        printKeyFindings(companies);
        printMetricGuide();
        System.out.println();
        System.out.println("StockScope is an educational tool. These figures describe past results and are");
        System.out.println("not investment recommendations.");
    }

    /** Five years of reported figures and ratios for one company. */
    public static void printCompanyDetails(CompanyFinancials financials) {
        List<FinancialData> years = financials.recentYears(YEARS_IN_DETAIL_VIEW);

        System.out.println();
        System.out.println("========== " + financials.company().name() + " (" + financials.company().ticker()
                + ") ==========");
        printRow("Fiscal Year", years, year -> "FY" + year.fiscalYear());
        printRow("Period Ended", years, year -> year.periodEnd().toString());
        System.out.println("  --- Reported in 10-K filings ---");
        printRow("Revenue", years, year -> money(year.revenue()));
        printRow("Net Income", years, year -> money(year.netIncome()));
        printRow("Operating Income", years, year -> money(year.operatingIncome()));
        printRow("Total Assets", years, year -> money(year.totalAssets()));
        printRow("Total Liabilities", years, ReportPrinter::liabilities);
        printRow("Stockholders' Equity", years, year -> money(year.stockholdersEquity()));
        printRow("Cash & Equivalents", years, year -> money(year.cash()));
        System.out.println("  --- Calculated by StockScope ---");
        printRow("Net Profit Margin", years,
                year -> percent(FinancialRatioService.calculate(financials, year).netProfitMargin()));
        printRow("Operating Margin", years,
                year -> percent(FinancialRatioService.calculate(financials, year).operatingMargin()));
        printRow("Return on Assets", years,
                year -> percent(FinancialRatioService.calculate(financials, year).returnOnAssets()));
        printRow("Debt-to-Equity", years,
                year -> ratio(FinancialRatioService.calculate(financials, year).debtToEquity()));
        printLiabilitiesFootnote(years);
    }

    private static void printKeyFindings(List<CompanyFinancials> companies) {
        System.out.println();
        System.out.println("KEY FINDINGS (latest fiscal year)");
        printFinding("Highest revenue", ComparisonService.highestRevenue(companies),
                company -> money(company.latestYear().revenue()));
        printFinding("Highest net income", ComparisonService.highestNetIncome(companies),
                company -> money(company.latestYear().netIncome()));
        printFinding("Highest net profit margin", ComparisonService.highestNetProfitMargin(companies),
                company -> percent(latestRatios(company).netProfitMargin()));
        printFinding("Lowest debt-to-equity", ComparisonService.lowestDebtToEquity(companies),
                company -> ratio(latestRatios(company).debtToEquity()));
    }

    private static void printFinding(String label, Optional<CompanyFinancials> winner,
                                     Function<CompanyFinancials, String> valueOf) {
        String result = winner
                .map(company -> company.company().ticker() + " (" + valueOf.apply(company) + ")")
                .orElse("Not enough data to compare");
        System.out.printf("  %-27s %s%n", label + ":", result);
    }

    private static void printMetricGuide() {
        System.out.println();
        System.out.println("WHAT THESE METRICS MEAN");
        explain("Revenue", "Total money earned from selling products and services.");
        explain("Net Income", "Profit left after all expenses, interest, and taxes.");
        explain("Operating Income", "Profit from the core business, before interest and taxes.");
        explain("Total Assets", "Everything the company owns (cash, buildings, investments...).");
        explain("Total Liabilities", "Everything the company owes (debt, unpaid bills, deposits...).");
        explain("Net Profit Margin", "Cents of profit kept from each dollar of revenue.");
        explain("Operating Margin", "Core-business profit as a share of revenue.");
        explain("Return on Assets", "Profit earned per dollar of assets (averaged over the year).");
        explain("Debt-to-Equity", "Liabilities per dollar of owners' equity. Higher means more");
        explain("", "reliance on borrowing. Banks are normally high, since customer");
        explain("", "deposits count as liabilities. N/A when equity is negative.");
    }

    private static void explain(String metric, String meaning) {
        System.out.printf("  %-19s %s%n", metric, meaning);
    }

    private static String liabilities(FinancialData year) {
        return money(year.totalLiabilities()) + (year.liabilitiesCalculated() ? "*" : "");
    }

    private static void printLiabilitiesFootnote(List<FinancialData> years) {
        // anyMatch is a Stream method: "is this true for at least one year?"
        if (years.stream().anyMatch(FinancialData::liabilitiesCalculated)) {
            System.out.println("  * Not reported directly by the company; calculated as Total Assets - Total Equity.");
        }
    }

    /**
     * Prints one table row: a label, then one value per column.
     *
     * This is a generic method - {@code <T>} means it works for any type of column.
     * The comparison table passes companies; the detail table passes fiscal years.
     */
    private static <T> void printRow(String label, List<T> columns, Function<T, String> valueOf) {
        StringBuilder row = new StringBuilder(String.format("  %-22s", label));
        for (T column : columns) {
            row.append(String.format("%14s", valueOf.apply(column)));
        }
        System.out.println(row);
    }
}
