package com.stockscope;

import com.stockscope.api.CompanyLookup;
import com.stockscope.api.SecApiClient;
import com.stockscope.api.SecApiException;
import com.stockscope.models.Company;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Scanner;

/**
 * Entry point for StockScope.
 *
 * Phase 2 version: a simple loop that looks up tickers and downloads each company's
 * financial facts, to prove the SEC connection works. Later phases replace this
 * with the full comparison menu.
 */
public class Main {

    public static void main(String[] args) {
        printBanner();

        SecApiClient secClient = new SecApiClient(Path.of("cache"));
        CompanyLookup lookup = new CompanyLookup(secClient);
        Scanner scanner = new Scanner(System.in);

        while (true) {
            System.out.print("\nEnter a ticker to look up (or press Enter to quit): ");
            if (!scanner.hasNextLine()) {
                break; // input was closed (e.g. Ctrl+D)
            }
            String input = scanner.nextLine().trim();
            if (input.isEmpty()) {
                break;
            }
            lookUpAndDownload(input, lookup, secClient);
        }
        System.out.println("Goodbye!");
    }

    private static void lookUpAndDownload(String ticker, CompanyLookup lookup, SecApiClient secClient) {
        try {
            Optional<Company> result = lookup.findByTicker(ticker);
            if (result.isEmpty()) {
                System.out.println("  No company found with ticker \"" + ticker + "\".");
                return;
            }

            Company company = result.get();
            System.out.println("  Company: " + company.name());
            System.out.println("  Ticker:  " + company.ticker());
            System.out.println("  CIK:     " + company.paddedCik());

            long start = System.currentTimeMillis();
            String factsJson = secClient.fetchCompanyFactsJson(company);
            long elapsedMillis = System.currentTimeMillis() - start;

            double megabytes = factsJson.getBytes(StandardCharsets.UTF_8).length / 1_000_000.0;
            System.out.printf("  Financial facts: %.1f MB of JSON (loaded in %d ms)%n", megabytes, elapsedMillis);
        } catch (SecApiException e) {
            // One failed lookup shouldn't end the program - show the message and keep going.
            System.out.println("  Error: " + e.getMessage());
        }
    }

    private static void printBanner() {
        System.out.println("========================================");
        System.out.println("               STOCKSCOPE");
        System.out.println("       Financial Comparison Tool");
        System.out.println("========================================");
    }
}
