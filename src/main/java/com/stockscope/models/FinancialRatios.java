package com.stockscope.models;

/**
 * Ratios StockScope calculates from reported figures. Kept separate from FinancialData,
 * which only holds numbers the company reported, so the two are never confused.
 *
 * Any ratio can be null, meaning "can't be calculated" - for example when an input is
 * missing, or when dividing by zero.
 *
 * @param netProfitMargin net income as a percentage of revenue (e.g. 25.3 means 25.3%)
 * @param operatingMargin operating income as a percentage of revenue
 * @param returnOnAssets  net income as a percentage of average total assets
 * @param debtToEquity    total liabilities divided by stockholders' equity (e.g. 1.5)
 */
public record FinancialRatios(
        Double netProfitMargin,
        Double operatingMargin,
        Double returnOnAssets,
        Double debtToEquity) {
}
