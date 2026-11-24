package com.stockscope.export;

import com.stockscope.models.Company;
import com.stockscope.models.CompanyFinancials;
import com.stockscope.models.FinancialData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the CSV content and the escaping rules. Most tests check the lines in memory;
 * one writes a real file into a temporary folder.
 */
class CsvExporterTest {

    private static final int COLUMNS = 16;

    // 2022: revenue 1000, net income 100, op income 200, assets 900, liabilities 600 (reported)
    // 2023: revenue 1200, net income 150, op income null, assets 1100, liabilities 700 (calculated)
    private static final CompanyFinancials SAMPLE = new CompanyFinancials(
            new Company("SMPL", 1, "Sample Corp"),
            List.of(new FinancialData(2022, LocalDate.of(2022, 12, 31), 1000L, 100L, 200L, 900L, 600L, 300L, 50L, false),
                    new FinancialData(2023, LocalDate.of(2023, 12, 31), 1200L, 150L, null, 1100L, 700L, 400L, 60L, true)));

    @Test
    void firstLineIsTheHeader() {
        String header = CsvExporter.toCsvLines(List.of(SAMPLE)).get(0);
        assertTrue(header.startsWith("Company,Ticker,Fiscal Year,Period End,Revenue (USD)"));
        assertEquals(COLUMNS, header.split(",").length);
    }

    @Test
    void writesOneRowPerYearWithRawNumbers() {
        List<String> lines = CsvExporter.toCsvLines(List.of(SAMPLE));

        assertEquals(3, lines.size()); // header + 2 years
        assertEquals("Sample Corp,SMPL,2022,2022-12-31,1000,100,200,900,600,No,300,50,10.00,20.00,,2.00",
                lines.get(1));
    }

    @Test
    void missingValuesAreEmptyCellsAndCalculatedValuesAreFlagged() {
        String row2023 = CsvExporter.toCsvLines(List.of(SAMPLE)).get(2);
        String[] cells = row2023.split(",", -1); // -1 keeps empty cells at the end

        assertEquals(COLUMNS, cells.length);
        assertEquals("", cells[6]);        // operating income not reported
        assertEquals("Yes", cells[9]);     // liabilities were calculated
        assertEquals("", cells[13]);       // so operating margin can't be calculated either
        assertEquals("15.00", cells[14]);  // ROA = 150 / ((900 + 1100) / 2) = 15%
        assertEquals("1.75", cells[15]);   // D/E = 700 / 400
    }

    @Test
    void exportsOnlyTheFiveMostRecentYears() {
        List<FinancialData> years = new ArrayList<>();
        for (int year = 2016; year <= 2023; year++) {
            years.add(new FinancialData(year, LocalDate.of(year, 12, 31), 1L, 1L, 1L, 1L, 1L, 1L, 1L, false));
        }
        CompanyFinancials longHistory = new CompanyFinancials(new Company("LONG", 2, "Long Corp"), years);

        List<String> lines = CsvExporter.toCsvLines(List.of(longHistory));

        assertEquals(6, lines.size()); // header + 5 years
        assertTrue(lines.get(1).contains(",2019,"));
        assertTrue(lines.get(5).contains(",2023,"));
    }

    @Test
    void escapesCommasQuotesAndLineBreaks() {
        assertEquals("Apple Inc.", CsvExporter.escape("Apple Inc."));
        assertEquals("\"Rivian Automotive, Inc. / DE\"", CsvExporter.escape("Rivian Automotive, Inc. / DE"));
        assertEquals("\"The \"\"Best\"\" Co\"", CsvExporter.escape("The \"Best\" Co"));
        assertEquals("\"Line\nBreak\"", CsvExporter.escape("Line\nBreak"));
    }

    @Test
    void companyNameWithCommaStaysInOneColumn() {
        CompanyFinancials rivian = new CompanyFinancials(new Company("RIVN", 3, "Rivian Automotive, Inc. / DE"),
                SAMPLE.years());

        String row = CsvExporter.toCsvLines(List.of(rivian)).get(1);

        assertTrue(row.startsWith("\"Rivian Automotive, Inc. / DE\",RIVN,2022,"));
    }

    @Test
    void decimalsUseAPointEvenOnAComputerSetToAnotherLanguage() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.GERMANY); // Germany writes 10,00 instead of 10.00
            String row = CsvExporter.toCsvLines(List.of(SAMPLE)).get(1);
            assertTrue(row.contains(",10.00,20.00,"), row);
        } finally {
            Locale.setDefault(original); // always restore, so other tests aren't affected
        }
    }

    @Test
    void writesTheFileNamedAfterTheTickers(@TempDir Path outputDir) throws IOException {
        CompanyFinancials other = new CompanyFinancials(new Company("OTHR", 4, "Other Corp"), SAMPLE.years());

        Path file = new CsvExporter(outputDir).export(List.of(SAMPLE, other));

        assertEquals("SMPL-OTHR_comparison.csv", file.getFileName().toString());
        List<String> written = Files.readAllLines(file);
        assertEquals(5, written.size()); // header + 2 years x 2 companies
        assertEquals(CsvExporter.toCsvLines(List.of(SAMPLE, other)), written);
    }
}
