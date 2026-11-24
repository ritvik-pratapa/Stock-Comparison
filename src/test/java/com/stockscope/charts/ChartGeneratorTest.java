package com.stockscope.charts;

import com.stockscope.models.Company;
import com.stockscope.models.CompanyFinancials;
import com.stockscope.models.FinancialData;
import org.jfree.data.category.DefaultCategoryDataset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the chart data preparation, and that the PNG files are actually written.
 * (Whether a chart LOOKS right is checked by opening the images.)
 */
class ChartGeneratorTest {

    private static final ChartGenerator.MoneyUnit BILLIONS = new ChartGenerator.MoneyUnit(1e9, "B", "billions");

    @Test
    void trendDatasetPutsYearsInOrderEvenWhenCompaniesDiffer() {
        // A's years are 2022-2023, B's are 2021-2022: the x-axis must be 2021, 2022, 2023.
        CompanyFinancials a = company("A", year(2022, 10_000_000_000L), year(2023, 12_000_000_000L));
        CompanyFinancials b = company("B", year(2021, 5_000_000_000L), year(2022, 6_000_000_000L));

        DefaultCategoryDataset dataset = ChartGenerator.trendDataset(List.of(a, b), BILLIONS);

        assertEquals(List.of("FY2021", "FY2022", "FY2023"), dataset.getColumnKeys());
        assertEquals(List.of("A", "B"), dataset.getRowKeys()); // rows stay in the order entered
        assertEquals(12.0, dataset.getValue("A", "FY2023").doubleValue(), 1e-9); // in billions
        assertNull(dataset.getValue("A", "FY2021")); // A has no 2021 data: a gap, not a zero
        assertNull(dataset.getValue("B", "FY2023"));
    }

    @Test
    void trendDatasetFlagsACompanyWithNoRevenueAtAll() {
        CompanyFinancials preRevenue = company("PRE", year(2022, null), year(2023, null));
        CompanyFinancials normal = company("NORM", year(2022, 5L), year(2023, 6L));

        DefaultCategoryDataset dataset = ChartGenerator.trendDataset(List.of(preRevenue, normal), BILLIONS);

        // Without the note, the legend would show "PRE" with no line, which looks like a bug.
        assertEquals(List.of("PRE (no revenue data)", "NORM"), dataset.getRowKeys());
    }

    @Test
    void trendDatasetUsesOnlyTheFiveMostRecentYears() {
        CompanyFinancials longHistory = company("LONG",
                year(2018, 1L), year(2019, 1L), year(2020, 1L), year(2021, 1L), year(2022, 1L), year(2023, 1L));

        DefaultCategoryDataset dataset = ChartGenerator.trendDataset(List.of(longHistory), BILLIONS);

        assertEquals(5, dataset.getColumnCount());
        assertEquals("FY2019", dataset.getColumnKey(0));
    }

    @Test
    void moneyUnitUsesMillionsForSmallerCompanies() {
        assertEquals("billions", ChartGenerator.MoneyUnit.choose(List.of(2_500_000_000L, 900_000_000L)).name());
        assertEquals("millions", ChartGenerator.MoneyUnit.choose(List.of(250_000_000L, 90_000_000L)).name());
        // A big loss counts as "big" too.
        assertEquals("billions", ChartGenerator.MoneyUnit.choose(List.of(-3_000_000_000L, 10L)).name());
    }

    @Test
    void moneyUnitIgnoresMissingValues() {
        assertEquals("billions", ChartGenerator.MoneyUnit.choose(Arrays.asList(null, 4_000_000_000L)).name());
        assertNull(BILLIONS.scale(null));
    }

    @Test
    void generatesFourPngFiles(@TempDir Path outputDir) throws IOException {
        // @TempDir gives each test a fresh empty folder that JUnit deletes afterwards.
        CompanyFinancials a = company("AAA", year(2022, 10_000_000_000L), year(2023, 12_000_000_000L));
        CompanyFinancials b = company("BBB", year(2022, 8_000_000_000L), year(2023, null)); // missing revenue

        List<Path> files = new ChartGenerator(outputDir).generateAll(List.of(a, b));

        assertEquals(4, files.size());
        for (Path file : files) {
            assertTrue(Files.size(file) > 1000, file + " should be a real image");
            assertTrue(file.getFileName().toString().startsWith("AAA-BBB_"));
            byte[] header = Arrays.copyOf(Files.readAllBytes(file), 4);
            assertEquals("\u0089PNG", new String(header, java.nio.charset.StandardCharsets.ISO_8859_1));
        }
    }

    // ----- Helpers -----

    private static CompanyFinancials company(String ticker, FinancialData... years) {
        return new CompanyFinancials(new Company(ticker, ticker.hashCode(), ticker + " Corp"), List.of(years));
    }

    /** A year with the given revenue; net income is 10% of it, other values fixed. */
    private static FinancialData year(int fiscalYear, Long revenue) {
        Long netIncome = revenue == null ? null : revenue / 10;
        return new FinancialData(fiscalYear, LocalDate.of(fiscalYear, 12, 31),
                revenue, netIncome, null, 1000L, 600L, 400L, null, false);
    }
}
