package com.stockscope;

import com.stockscope.api.CompanyLookup;
import com.stockscope.api.SecApiClient;
import com.stockscope.api.SecApiException;
import com.stockscope.models.Company;
import com.stockscope.models.CompanyFinancials;
import com.stockscope.services.FinancialDataService;
import com.stockscope.ui.ReportPrinter;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Scanner;
import java.util.regex.Pattern;

import static com.stockscope.services.ComparisonService.MAX_COMPANIES;
import static com.stockscope.services.ComparisonService.MIN_COMPANIES;

/**
 * StockScope's console interface: asks for tickers, shows the comparison, and runs the menu.
 *
 * main() creates a Main object and calls run(). Using an object (instead of all-static
 * methods) lets the scanner, services, and current comparison be fields that every
 * method can use, instead of passing them around as parameters.
 */
public class Main {

    private static final String[] ORDINALS = {"first", "second", "third", "fourth"};
    // Letters, digits, dots, and dashes, 1-10 characters: AAPL, BRK.B, BRK-B, MOG-A...
    private static final Pattern TICKER_FORMAT = Pattern.compile("[A-Za-z0-9.\\-]{1,10}");

    private final Scanner scanner = new Scanner(System.in);
    private final CompanyLookup lookup;
    private final FinancialDataService dataService;
    private List<CompanyFinancials> companies = List.of(); // the current comparison

    public static void main(String[] args) {
        new Main().run();
    }

    private Main() {
        SecApiClient secClient = new SecApiClient(Path.of("cache"));
        lookup = new CompanyLookup(secClient);
        dataService = new FinancialDataService(secClient);
    }

    private void run() {
        printBanner();
        if (!startNewComparison()) {
            System.out.println("Goodbye!");
            return;
        }

        while (true) {
            printMenu();
            int choice = readNumber("Select an option: ", 1, 5);
            // A "switch" with arrows (Java 14+) runs only the matching case - no "break" needed.
            switch (choice) {
                case 1 -> showCompanyDetails();
                case 2 -> System.out.println("\nCharts will be added in Phase 6.");
                case 3 -> System.out.println("\nCSV export will be added in Phase 7.");
                case 4 -> startNewComparison(); // if cancelled, the old comparison is kept
                default -> {                    // 5 (Exit), or -1 if the input stream ended
                    System.out.println("Goodbye!");
                    return;
                }
            }
        }
    }

    /**
     * Asks for 2-4 tickers, loads each company's data, and prints the comparison.
     * Returns false if the user cancelled, in which case the current comparison is unchanged.
     */
    private boolean startNewComparison() {
        System.out.println("\nCompare " + MIN_COMPANIES + " to " + MAX_COMPANIES
                + " companies by ticker symbol (e.g. AAPL, MSFT, NVDA). Type 'quit' to cancel.");
        List<CompanyFinancials> chosen = new ArrayList<>();

        while (chosen.size() < MAX_COMPANIES) {
            boolean canStopHere = chosen.size() >= MIN_COMPANIES;
            String prompt = "Enter the " + ORDINALS[chosen.size()] + " company ticker"
                    + (canStopHere ? " (or press Enter to compare now): " : ": ");
            String input = readLine(prompt);

            if (input == null || input.equalsIgnoreCase("quit")) {
                return false;
            }
            if (input.isEmpty()) {
                if (canStopHere) {
                    break;
                }
                System.out.println("  Please enter a ticker symbol.");
                continue;
            }

            CompanyFinancials loaded = loadCompany(input, chosen);
            if (loaded != null) {
                chosen.add(loaded);
            }
        }

        companies = chosen;
        System.out.println("\nSuccessfully retrieved financial information.");
        ReportPrinter.printComparison(companies);
        return true;
    }

    /**
     * Validates a ticker, looks it up, and downloads its financial data.
     * Returns null (after printing why) if anything goes wrong, so the user can try again.
     */
    private CompanyFinancials loadCompany(String input, List<CompanyFinancials> alreadyChosen) {
        if (!TICKER_FORMAT.matcher(input).matches()) {
            System.out.println("  \"" + input + "\" isn't a valid ticker format. Use symbols like AAPL or BRK.B.");
            return null;
        }

        try {
            Optional<Company> found = lookup.findByTicker(input);
            if (found.isEmpty()) {
                System.out.println("  No company found with ticker \"" + input.toUpperCase() + "\". Please try again.");
                return null;
            }
            Company company = found.get();

            // Compare by CIK, not ticker: GOOG and GOOGL are different tickers for the same company.
            for (CompanyFinancials existing : alreadyChosen) {
                if (existing.company().cik() == company.cik()) {
                    System.out.println("  " + company.ticker() + " is the same company as "
                            + existing.company().ticker() + ", which is already in this comparison.");
                    return null;
                }
            }

            System.out.println("  Fetching financial information for " + company.ticker() + "...");
            CompanyFinancials financials = dataService.getFinancials(company);
            System.out.println("  Loaded " + company.name() + " (latest fiscal year: FY"
                    + financials.latestYear().fiscalYear() + ")");
            return financials;
        } catch (SecApiException e) {
            System.out.println("  Error: " + e.getMessage());
            return null;
        }
    }

    private void showCompanyDetails() {
        System.out.println("\nWhich company?");
        for (int i = 0; i < companies.size(); i++) {
            Company company = companies.get(i).company();
            System.out.println("  " + (i + 1) + ". " + company.ticker() + " - " + company.name());
        }
        int choice = readNumber("Select a company: ", 1, companies.size());
        if (choice != -1) {
            ReportPrinter.printCompanyDetails(companies.get(choice - 1));
        }
    }

    private void printMenu() {
        System.out.println();
        System.out.println("1. View detailed company information");
        System.out.println("2. View financial charts");
        System.out.println("3. Export comparison to CSV");
        System.out.println("4. Compare different companies");
        System.out.println("5. Exit");
    }

    /** Keeps asking until the user types a whole number in range. Returns -1 if input ends. */
    private int readNumber(String prompt, int min, int max) {
        while (true) {
            String input = readLine(prompt);
            if (input == null) {
                return -1;
            }
            try {
                int number = Integer.parseInt(input);
                if (number >= min && number <= max) {
                    return number;
                }
            } catch (NumberFormatException e) {
                // Not a number (e.g. "abc") - fall through to the message below.
            }
            System.out.println("  Please enter a number from " + min + " to " + max + ".");
        }
    }

    /** Reads one trimmed line, or returns null if the input has ended (e.g. Ctrl+D). */
    private String readLine(String prompt) {
        System.out.print(prompt);
        if (!scanner.hasNextLine()) {
            System.out.println();
            return null;
        }
        return scanner.nextLine().trim();
    }

    private static void printBanner() {
        System.out.println("========================================");
        System.out.println("               STOCKSCOPE");
        System.out.println("       Financial Comparison Tool");
        System.out.println("========================================");
    }
}
