package com.stockscope.export;

import com.stockscope.models.CompanyFinancials;
import com.stockscope.models.FinancialData;
import com.stockscope.models.FinancialRatios;
import com.stockscope.services.FinancialRatioService;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Exports a comparison to a CSV file that opens in Excel, Google Sheets, etc.
 *
 * Choices that make the file useful for further analysis:
 *   - One row per company per fiscal year (up to five years).
 *   - Raw numbers in whole dollars (478834000000, not "$478.8B"), so formulas work.
 *   - Missing values are empty cells, not "N/A", so numeric columns stay numeric.
 *   - Calculated columns are labeled "(calculated)" to keep them apart from reported data.
 */
public class CsvExporter {

    private static final int YEARS_TO_EXPORT = 5;

    private static final List<String> HEADER = List.of(
            "Company", "Ticker", "Fiscal Year", "Period End",
            "Revenue (USD)", "Net Income (USD)", "Operating Income (USD)",
            "Total Assets (USD)", "Total Liabilities (USD)", "Liabilities Calculated",
            "Stockholders Equity (USD)", "Cash and Equivalents (USD)",
            "Net Profit Margin % (calculated)", "Operating Margin % (calculated)",
            "Return on Assets % (calculated)", "Debt-to-Equity (calculated)");

    private final Path outputDir;

    public CsvExporter(Path outputDir) {
        this.outputDir = outputDir;
    }

    /** Writes the CSV file and returns its path, e.g. output/AAPL-MSFT_comparison.csv. */
    public Path export(List<CompanyFinancials> companies) throws IOException {
        Files.createDirectories(outputDir);
        String tickers = companies.stream()
                .map(company -> company.company().ticker())
                .collect(Collectors.joining("-"));
        Path file = outputDir.resolve(tickers + "_comparison.csv");

        // "try-with-resources": the writer is closed automatically when the block ends,
        // even if an exception is thrown halfway through. Forgetting to close a writer
        // can leave a file half-written.
        try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            for (String line : toCsvLines(companies)) {
                writer.write(line);
                writer.newLine();
            }
        }
        return file;
    }

    /** Builds every line of the file (header first). Separate from writing so tests can check it. */
    static List<String> toCsvLines(List<CompanyFinancials> companies) {
        List<String> lines = new ArrayList<>();
        lines.add(toCsvRow(HEADER));

        for (CompanyFinancials company : companies) {
            for (FinancialData year : company.recentYears(YEARS_TO_EXPORT)) {
                FinancialRatios ratios = FinancialRatioService.calculate(company, year);
                lines.add(toCsvRow(List.of(
                        company.company().name(),
                        company.company().ticker(),
                        String.valueOf(year.fiscalYear()),
                        year.periodEnd().toString(),
                        wholeNumber(year.revenue()),
                        wholeNumber(year.netIncome()),
                        wholeNumber(year.operatingIncome()),
                        wholeNumber(year.totalAssets()),
                        wholeNumber(year.totalLiabilities()),
                        year.liabilitiesCalculated() ? "Yes" : "No",
                        wholeNumber(year.stockholdersEquity()),
                        wholeNumber(year.cash()),
                        decimal(ratios.netProfitMargin()),
                        decimal(ratios.operatingMargin()),
                        decimal(ratios.returnOnAssets()),
                        decimal(ratios.debtToEquity()))));
            }
        }
        return lines;
    }

    private static String toCsvRow(List<String> fields) {
        return fields.stream().map(CsvExporter::escape).collect(Collectors.joining(","));
    }

    /**
     * CSV rule (RFC 4180): a field containing a comma, quote, or line break must be wrapped
     * in double quotes, and any quote inside it doubled. Without this, a name like
     * "Rivian Automotive, Inc." would be split across two columns.
     */
    static String escape(String field) {
        if (field.contains(",") || field.contains("\"") || field.contains("\n") || field.contains("\r")) {
            return "\"" + field.replace("\"", "\"\"") + "\"";
        }
        return field;
    }

    private static String wholeNumber(Long value) {
        return value == null ? "" : value.toString();
    }

    /** Two decimal places, always with a "." - a "," decimal (used in e.g. Germany) would break the CSV. */
    private static String decimal(Double value) {
        return value == null ? "" : String.format(Locale.US, "%.2f", value);
    }
}
