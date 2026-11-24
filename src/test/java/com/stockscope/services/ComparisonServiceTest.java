package com.stockscope.services;

import com.stockscope.models.Company;
import com.stockscope.models.CompanyFinancials;
import com.stockscope.models.FinancialData;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the comparison logic with made-up companies, so each test knows the right answer.
 */
class ComparisonServiceTest {

    // Revenue, net income, liabilities, equity
    private static final CompanyFinancials BIG = company("BIG", 1000L, 100L, 600L, 400L);     // 10% margin, D/E 1.5
    private static final CompanyFinancials LEAN = company("LEAN", 500L, 150L, 100L, 400L);    // 30% margin, D/E 0.25
    private static final CompanyFinancials MID = company("MID", 700L, 70L, 1400L, 700L);      // 10% margin, D/E 2.0

    @Test
    void findsHighestRevenue() {
        assertEquals("BIG", ticker(ComparisonService.highestRevenue(List.of(LEAN, BIG, MID))));
    }

    @Test
    void findsHighestNetIncome() {
        assertEquals("LEAN", ticker(ComparisonService.highestNetIncome(List.of(BIG, LEAN, MID))));
    }

    @Test
    void findsHighestNetProfitMargin() {
        assertEquals("LEAN", ticker(ComparisonService.highestNetProfitMargin(List.of(BIG, LEAN, MID))));
    }

    @Test
    void findsLowestDebtToEquity() {
        assertEquals("LEAN", ticker(ComparisonService.lowestDebtToEquity(List.of(BIG, MID, LEAN))));
    }

    @Test
    void companyWithMissingRevenueIsSkippedNotTreatedAsZero() {
        CompanyFinancials noRevenue = company("NONE", null, 100L, 600L, 400L);
        assertEquals("LEAN", ticker(ComparisonService.highestRevenue(List.of(noRevenue, LEAN))));
    }

    @Test
    void negativeEquityCompanyCannotWinLowestDebtToEquity() {
        // Liabilities 900 / equity -100 would be -9.0, which looks "lowest" but isn't meaningful.
        CompanyFinancials negativeEquity = company("NEG", 1000L, 100L, 900L, -100L);
        assertEquals("BIG", ticker(ComparisonService.lowestDebtToEquity(List.of(negativeEquity, BIG))));
    }

    @Test
    void returnsEmptyWhenNoCompanyHasTheData() {
        CompanyFinancials a = company("A", null, 100L, 600L, 400L);
        CompanyFinancials b = company("B", null, 200L, 600L, 400L);
        assertTrue(ComparisonService.highestRevenue(List.of(a, b)).isEmpty());
        assertTrue(ComparisonService.highestNetProfitMargin(List.of(a, b)).isEmpty());
    }

    @Test
    void highestNetIncomeAmongLossesIsTheSmallestLoss() {
        CompanyFinancials smallLoss = company("SMALL", 1000L, -10L, 600L, 400L);
        CompanyFinancials bigLoss = company("BIGLOSS", 1000L, -500L, 600L, 400L);
        assertEquals("SMALL", ticker(ComparisonService.highestNetIncome(List.of(bigLoss, smallLoss))));
    }

    @Test
    void exactTieGoesToTheCompanyEnteredFirst() {
        // BIG and MID both have a 10% net margin.
        assertEquals("BIG", ticker(ComparisonService.highestNetProfitMargin(List.of(BIG, MID))));
        assertEquals("MID", ticker(ComparisonService.highestNetProfitMargin(List.of(MID, BIG))));
    }

    @Test
    void usesTheLatestFiscalYear() {
        FinancialData oldYear = year(2022, 9999L, 1L, 600L, 400L);   // huge revenue, but old
        FinancialData newYear = year(2023, 100L, 1L, 600L, 400L);
        CompanyFinancials shrinking = new CompanyFinancials(
                new Company("SHRINK", 99, "Shrink Corp"), List.of(oldYear, newYear));

        assertEquals("BIG", ticker(ComparisonService.highestRevenue(List.of(shrinking, BIG))));
    }

    // ----- Helpers -----

    private static CompanyFinancials company(String ticker, Long revenue, Long netIncome,
                                             Long liabilities, Long equity) {
        return new CompanyFinancials(new Company(ticker, ticker.hashCode(), ticker + " Corp"),
                List.of(year(2023, revenue, netIncome, liabilities, equity)));
    }

    private static FinancialData year(int fiscalYear, Long revenue, Long netIncome, Long liabilities, Long equity) {
        return new FinancialData(fiscalYear, LocalDate.of(fiscalYear, 12, 31),
                revenue, netIncome, null, 1000L, liabilities, equity, null, false);
    }

    private static String ticker(Optional<CompanyFinancials> result) {
        return result.map(company -> company.company().ticker()).orElse("none");
    }
}
