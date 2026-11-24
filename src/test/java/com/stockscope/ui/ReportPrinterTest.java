package com.stockscope.ui;

import com.stockscope.models.Company;
import com.stockscope.models.CompanyFinancials;
import com.stockscope.models.FinancialData;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks that the console reports print correctly - especially that missing data shows
 * as N/A instead of crashing. System.out is temporarily redirected into a buffer so the
 * test can read what was printed.
 */
class ReportPrinterTest {

    private static final CompanyFinancials NORMAL = company("GOOD",
            new FinancialData(2025, LocalDate.of(2025, 12, 31), 1000L, 100L, 200L, 900L, 600L, 300L, 50L, false));
    private static final CompanyFinancials EMPTY = company("EMPTY",
            new FinancialData(2025, LocalDate.of(2025, 9, 30), null, null, null, null, null, null, null, false));

    @Test
    void comparisonWithMissingDataPrintsNAInsteadOfCrashing() {
        String output = capture(() -> ReportPrinter.printComparison(List.of(EMPTY, NORMAL)));

        assertTrue(output.contains("N/A"));
        assertTrue(output.contains("Highest revenue:            GOOD ($1,000)"), output);
        assertTrue(output.contains("not investment recommendations"));
    }

    @Test
    void saysNotEnoughDataWhenNoCompanyHasAValue() {
        CompanyFinancials alsoEmpty = company("NONE", EMPTY.latestYear());
        String output = capture(() -> ReportPrinter.printComparison(List.of(EMPTY, alsoEmpty)));

        assertTrue(output.contains("Highest revenue:            Not enough data to compare"), output);
    }

    @Test
    void warnsWhenFiscalYearsEndOnDifferentDates() {
        String output = capture(() -> ReportPrinter.printComparison(List.of(EMPTY, NORMAL)));
        assertTrue(output.contains("fiscal years end on different dates"));
    }

    @Test
    void detailViewMarksCalculatedLiabilities() {
        CompanyFinancials calculated = company("CALC",
                new FinancialData(2025, LocalDate.of(2025, 12, 31), 1000L, 100L, 200L, 900L, 600L, 300L, 50L, true));

        String output = capture(() -> ReportPrinter.printCompanyDetails(calculated));

        assertTrue(output.contains("$600*"), output);
        assertTrue(output.contains("* Not reported directly"));
    }

    // ----- Helpers -----

    private static CompanyFinancials company(String ticker, FinancialData year) {
        return new CompanyFinancials(new Company(ticker, ticker.hashCode(), ticker + " Corp"), List.of(year));
    }

    /** Runs the code with System.out redirected, and returns everything it printed. */
    private static String capture(Runnable printing) {
        PrintStream original = System.out;
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        System.setOut(new PrintStream(buffer, true, StandardCharsets.UTF_8));
        try {
            printing.run();
        } finally {
            System.setOut(original); // always put the real console back
        }
        return buffer.toString(StandardCharsets.UTF_8);
    }
}
