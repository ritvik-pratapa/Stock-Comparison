package com.stockscope;

import com.stockscope.api.CompanyLookup;
import com.stockscope.api.SecApiClient;
import com.stockscope.api.SecApiException;
import com.stockscope.models.Company;
import com.stockscope.models.CompanyFinancials;
import com.stockscope.models.FinancialData;
import com.stockscope.services.FinancialDataService;
import com.stockscope.services.FinancialRatioService;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Scanner;
import java.util.function.Function;

import static com.stockscope.utils.FinancialFormatter.money;
import static com.stockscope.utils.FinancialFormatter.percent;
import static com.stockscope.utils.FinancialFormatter.ratio;

/**
 * Entry point for StockScope.
 *
 * Phase 4 version: look up a ticker and print five years of reported financial data
 * plus calculated ratios. Phase 5 replaces this with the comparison menu.
 */
public class Main {

    private static final int YEARS_TO_SHOW = 5;

    public static void main(String[] args) {
        printBanner();

        SecApiClient secClient = new SecApiClient(Path.of("cache"));
        CompanyLookup lookup = new CompanyLookup(secClient);
        FinancialDataService dataService = new FinancialDataService(secClient);
        Scanner scanner = new Scanner(System.in);

        while (true) {
            System.out.print("\nEnter a ticker (or press Enter to quit): ");
            if (!scanner.hasNextLine()) {
                break; // input was closed (e.g. Ctrl+D)
            }
            String input = scanner.nextLine().trim();
            if (input.isEmpty()) {
                break;
            }
            showFinancials(input, lookup, dataService);
        }
        System.out.println("Goodbye!");
    }

    private static void showFinancials(String ticker, CompanyLookup lookup, FinancialDataService dataService) {
        try {
            Optional<Company> company = lookup.findByTicker(ticker);
            if (company.isEmpty()) {
                System.out.println("  No company found with ticker \"" + ticker + "\".");
                return;
            }
            System.out.println("  Fetching financial information...");
            CompanyFinancials financials = dataService.getFinancials(company.get());
            printYearTable(financials);
        } catch (SecApiException e) {
            // One failed lookup shouldn't end the program - show the message and keep going.
            System.out.println("  Error: " + e.getMessage());
        }
    }

    private static void printYearTable(CompanyFinancials financials) {
        Company company = financials.company();
        List<FinancialData> years = financials.recentYears(YEARS_TO_SHOW);

        System.out.println();
        System.out.println(company.name() + " (" + company.ticker() + ") - reported figures from 10-K filings");
        // Each row passes a small function (a lambda) saying which value to show per year.
        printRow("Fiscal Year", years, year -> "FY" + year.fiscalYear());
        printRow("Period Ended", years, year -> year.periodEnd().toString());
        printRow("Revenue", years, year -> money(year.revenue()));
        printRow("Net Income", years, year -> money(year.netIncome()));
        printRow("Operating Income", years, year -> money(year.operatingIncome()));
        printRow("Total Assets", years, year -> money(year.totalAssets()));
        printRow("Total Liabilities", years,
                year -> money(year.totalLiabilities()) + (year.liabilitiesCalculated() ? "*" : ""));
        printRow("Stockholders' Equity", years, year -> money(year.stockholdersEquity()));
        printRow("Cash & Equivalents", years, year -> money(year.cash()));

        System.out.println("  --- Ratios calculated by StockScope ---");
        printRow("Net Profit Margin", years,
                year -> percent(FinancialRatioService.calculate(financials, year).netProfitMargin()));
        printRow("Operating Margin", years,
                year -> percent(FinancialRatioService.calculate(financials, year).operatingMargin()));
        printRow("Return on Assets", years,
                year -> percent(FinancialRatioService.calculate(financials, year).returnOnAssets()));
        printRow("Debt-to-Equity", years,
                year -> ratio(FinancialRatioService.calculate(financials, year).debtToEquity()));

        // anyMatch is a Stream method: "is this true for at least one year?"
        if (years.stream().anyMatch(FinancialData::liabilitiesCalculated)) {
            System.out.println("  * Not reported directly by the company; calculated as Total Assets - Total Equity.");
        }
    }

    private static void printRow(String label, List<FinancialData> years, Function<FinancialData, String> valueOf) {
        StringBuilder row = new StringBuilder(String.format("  %-22s", label));
        for (FinancialData year : years) {
            row.append(String.format("%13s", valueOf.apply(year)));
        }
        System.out.println(row);
    }

    private static void printBanner() {
        System.out.println("========================================");
        System.out.println("               STOCKSCOPE");
        System.out.println("       Financial Comparison Tool");
        System.out.println("========================================");
    }
}
