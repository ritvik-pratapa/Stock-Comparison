package com.stockscope.models;

import java.time.LocalDate;

/**
 * One fiscal year of financial figures for one company, exactly as reported to the SEC
 * (in whole US dollars).
 *
 * Every amount is a Long (the wrapper class) rather than a long (the primitive) so it can
 * be null, which means "the company didn't report this". For example, banks usually don't
 * report operating income. Code that uses these values must check for null first.
 *
 * Calculated ratios are deliberately NOT stored here - this record only holds reported
 * numbers, which keeps reported data and our own calculations clearly separate.
 * The one exception is flagged: if a company doesn't report total liabilities, we work it
 * out from its balance sheet and set liabilitiesCalculated to true so the UI can say so.
 *
 * @param fiscalYear         the fiscal year label, e.g. 2025
 * @param periodEnd          the last day of the fiscal year (Apple's ends in late September)
 * @param revenue            total revenue for the year (income statement)
 * @param netIncome          profit after all expenses and taxes (income statement)
 * @param operatingIncome    profit from core business operations (income statement)
 * @param totalAssets        everything the company owns, at year end (balance sheet)
 * @param totalLiabilities   everything the company owes, at year end (balance sheet)
 * @param stockholdersEquity assets minus liabilities - the owners' stake (balance sheet)
 * @param cash               cash and cash equivalents, at year end (balance sheet)
 * @param liabilitiesCalculated true if totalLiabilities was calculated as
 *                              Total Assets - Total Equity instead of being reported
 */
public record FinancialData(
        int fiscalYear,
        LocalDate periodEnd,
        Long revenue,
        Long netIncome,
        Long operatingIncome,
        Long totalAssets,
        Long totalLiabilities,
        Long stockholdersEquity,
        Long cash,
        boolean liabilitiesCalculated) {
}
