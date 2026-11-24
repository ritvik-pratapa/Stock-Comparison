package com.stockscope.services;

import com.stockscope.models.CompanyFinancials;
import com.stockscope.models.FinancialData;
import com.stockscope.models.FinancialRatios;

/**
 * Calculates financial ratios from reported figures.
 *
 * Every method returns null when a ratio can't be calculated honestly - a missing input,
 * a zero denominator, or (for debt-to-equity) negative equity. Returning null instead of
 * 0 matters: a 0% margin is a real, meaningful number, but "unknown" is not 0.
 *
 * All methods are static because they are pure calculations: same inputs, same output,
 * no state stored anywhere. That also makes them very easy to unit test.
 */
public final class FinancialRatioService {

    private FinancialRatioService() {
    }

    /** Calculates all four ratios for one fiscal year of a company. */
    public static FinancialRatios calculate(CompanyFinancials company, FinancialData year) {
        // Assets at the start of this year = assets at the end of last year (if we have it).
        Long assetsAtStart = company.previousYear(year)
                .map(FinancialData::totalAssets)
                .orElse(null);

        return new FinancialRatios(
                netProfitMargin(year.netIncome(), year.revenue()),
                operatingMargin(year.operatingIncome(), year.revenue()),
                returnOnAssets(year.netIncome(), assetsAtStart, year.totalAssets()),
                debtToEquity(year.totalLiabilities(), year.stockholdersEquity()));
    }

    /**
     * Net Profit Margin = Net Income / Revenue x 100.
     * How many cents of profit the company keeps from each dollar of sales.
     */
    public static Double netProfitMargin(Long netIncome, Long revenue) {
        if (revenue == null || revenue <= 0) {
            return null; // no sales means a margin isn't meaningful
        }
        return percent(divide(netIncome, revenue));
    }

    /**
     * Operating Margin = Operating Income / Revenue x 100.
     * Profitability of the core business, before interest and taxes.
     */
    public static Double operatingMargin(Long operatingIncome, Long revenue) {
        if (revenue == null || revenue <= 0) {
            return null;
        }
        return percent(divide(operatingIncome, revenue));
    }

    /**
     * Return on Assets = Net Income / Average Total Assets x 100,
     * where Average Total Assets = (assets at start of year + assets at end of year) / 2.
     * How efficiently the company turns what it owns into profit.
     *
     * The average is used because profit is earned across the whole year, while assets
     * are a snapshot on one day - averaging the two snapshots is a fairer comparison.
     */
    public static Double returnOnAssets(Long netIncome, Long assetsAtStart, Long assetsAtEnd) {
        if (netIncome == null || assetsAtStart == null || assetsAtEnd == null) {
            return null;
        }
        double averageAssets = (assetsAtStart + assetsAtEnd) / 2.0;
        if (averageAssets <= 0) {
            return null;
        }
        return netIncome / averageAssets * 100;
    }

    /**
     * Debt-to-Equity = Total Liabilities / Stockholders' Equity.
     * How much the company relies on borrowed money compared with owners' money.
     *
     * Returns null when equity is zero or negative. Negative equity (which happens after
     * years of large losses or stock buybacks) would produce a negative ratio that looks
     * "better" than any real one - so we say N/A rather than show a misleading number.
     */
    public static Double debtToEquity(Long totalLiabilities, Long stockholdersEquity) {
        if (stockholdersEquity == null || stockholdersEquity <= 0) {
            return null;
        }
        return divide(totalLiabilities, stockholdersEquity);
    }

    /** Null-safe division: returns null if either value is missing or the denominator is 0. */
    private static Double divide(Long numerator, Long denominator) {
        if (numerator == null || denominator == null || denominator == 0) {
            return null;
        }
        // Cast to double first - otherwise Java does whole-number division (25 / 100 = 0).
        return (double) numerator / denominator;
    }

    private static Double percent(Double fraction) {
        return fraction == null ? null : fraction * 100;
    }
}
