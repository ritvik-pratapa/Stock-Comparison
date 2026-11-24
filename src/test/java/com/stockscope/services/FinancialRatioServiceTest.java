package com.stockscope.services;

import com.stockscope.models.Company;
import com.stockscope.models.CompanyFinancials;
import com.stockscope.models.FinancialData;
import com.stockscope.models.FinancialRatios;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Tests each ratio formula with simple numbers, plus the edge cases:
 * missing data, division by zero, negative values.
 *
 * Doubles are compared with a small tolerance (DELTA) because floating-point math
 * can be off by a tiny amount, e.g. 0.1 + 0.2 = 0.30000000000000004.
 */
class FinancialRatioServiceTest {

    private static final double DELTA = 1e-9;

    // ----- Net profit margin -----

    @Test
    void netProfitMarginIsNetIncomeOverRevenueTimes100() {
        assertEquals(25.0, FinancialRatioService.netProfitMargin(25L, 100L), DELTA);
    }

    @Test
    void netProfitMarginIsNegativeForALoss() {
        assertEquals(-10.0, FinancialRatioService.netProfitMargin(-10L, 100L), DELTA);
    }

    @Test
    void netProfitMarginWorksWithRealSizedNumbers() {
        // Apple FY2023: $96,995M net income on $383,285M revenue = 25.31%
        assertEquals(25.306, FinancialRatioService.netProfitMargin(96_995_000_000L, 383_285_000_000L), 0.001);
    }

    @Test
    void netProfitMarginIsNullWhenRevenueIsZero() {
        assertNull(FinancialRatioService.netProfitMargin(25L, 0L)); // division by zero
    }

    @Test
    void netProfitMarginIsNullWhenDataIsMissing() {
        assertNull(FinancialRatioService.netProfitMargin(null, 100L));
        assertNull(FinancialRatioService.netProfitMargin(25L, null));
    }

    // ----- Operating margin -----

    @Test
    void operatingMarginIsOperatingIncomeOverRevenueTimes100() {
        assertEquals(30.0, FinancialRatioService.operatingMargin(30L, 100L), DELTA);
    }

    @Test
    void operatingMarginIsNullWhenOperatingIncomeNotReported() {
        // Banks usually don't report operating income.
        assertNull(FinancialRatioService.operatingMargin(null, 100L));
    }

    @Test
    void operatingMarginIsNullWhenRevenueIsZero() {
        assertNull(FinancialRatioService.operatingMargin(30L, 0L));
    }

    // ----- Return on assets -----

    @Test
    void returnOnAssetsUsesAverageAssets() {
        // Average assets = (900 + 1100) / 2 = 1000, so ROA = 100 / 1000 = 10%
        assertEquals(10.0, FinancialRatioService.returnOnAssets(100L, 900L, 1100L), DELTA);
    }

    @Test
    void returnOnAssetsIsNullWithoutPreviousYearAssets() {
        assertNull(FinancialRatioService.returnOnAssets(100L, null, 1100L));
    }

    @Test
    void returnOnAssetsIsNullWhenAssetsAreZero() {
        assertNull(FinancialRatioService.returnOnAssets(100L, 0L, 0L));
    }

    // ----- Debt-to-equity -----

    @Test
    void debtToEquityIsLiabilitiesOverEquity() {
        assertEquals(1.5, FinancialRatioService.debtToEquity(600L, 400L), DELTA);
    }

    @Test
    void debtToEquityIsNullWhenEquityIsZero() {
        assertNull(FinancialRatioService.debtToEquity(600L, 0L));
    }

    @Test
    void debtToEquityIsNullWhenEquityIsNegative() {
        // A negative ratio would look like "less debt", which is the opposite of the truth.
        assertNull(FinancialRatioService.debtToEquity(600L, -50L));
    }

    @Test
    void debtToEquityIsNullWhenLiabilitiesMissing() {
        assertNull(FinancialRatioService.debtToEquity(null, 400L));
    }

    // ----- Calculating all ratios for a company's year -----

    @Test
    void calculateUsesPreviousYearForReturnOnAssets() {
        FinancialData year2022 = sampleYear(2022, 900L);
        FinancialData year2023 = sampleYear(2023, 1100L);
        CompanyFinancials company = sampleCompany(List.of(year2022, year2023));

        FinancialRatios ratios = FinancialRatioService.calculate(company, year2023);

        assertEquals(10.0, ratios.netProfitMargin(), DELTA);   // 100 / 1000
        assertEquals(20.0, ratios.operatingMargin(), DELTA);   // 200 / 1000
        assertEquals(10.0, ratios.returnOnAssets(), DELTA);    // 100 / ((900 + 1100) / 2)
        assertEquals(1.5, ratios.debtToEquity(), DELTA);       // 600 / 400
    }

    @Test
    void calculateGivesNullReturnOnAssetsForTheFirstYear() {
        FinancialData year2022 = sampleYear(2022, 900L);
        CompanyFinancials company = sampleCompany(List.of(year2022, sampleYear(2023, 1100L)));

        assertNull(FinancialRatioService.calculate(company, year2022).returnOnAssets());
    }

    @Test
    void calculateDoesNotUseANonConsecutiveYear() {
        // If 2022 is missing, 2021's assets must NOT be used as the start of 2023.
        FinancialData year2023 = sampleYear(2023, 1100L);
        CompanyFinancials company = sampleCompany(List.of(sampleYear(2021, 900L), year2023));

        assertNull(FinancialRatioService.calculate(company, year2023).returnOnAssets());
    }

    // ----- Helpers for building sample data -----

    /** Revenue 1000, net income 100, operating income 200, liabilities 600, equity 400. */
    private static FinancialData sampleYear(int fiscalYear, Long totalAssets) {
        return new FinancialData(fiscalYear, LocalDate.of(fiscalYear, 12, 31),
                1000L, 100L, 200L, totalAssets, 600L, 400L, 50L, false);
    }

    private static CompanyFinancials sampleCompany(List<FinancialData> years) {
        return new CompanyFinancials(new Company("SMPL", 1, "Sample Corp"), years);
    }
}
