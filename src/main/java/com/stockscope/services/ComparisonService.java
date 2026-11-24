package com.stockscope.services;

import com.stockscope.models.CompanyFinancials;
import com.stockscope.models.FinancialRatios;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Compares companies using each one's latest fiscal year.
 *
 * It answers factual questions like "which company had the highest revenue?". It does
 * NOT decide which company is the better investment - that depends on many things these
 * numbers don't capture (price, growth, risk, industry).
 *
 * A company with a missing value is skipped for that question rather than treated as 0,
 * so missing data can never make a company "win" or "lose".
 */
public final class ComparisonService {

    public static final int MIN_COMPANIES = 2;
    public static final int MAX_COMPANIES = 4;

    private ComparisonService() {
    }

    /** All four ratios for a company's most recent fiscal year. */
    public static FinancialRatios latestRatios(CompanyFinancials company) {
        return FinancialRatioService.calculate(company, company.latestYear());
    }

    public static Optional<CompanyFinancials> highestRevenue(List<CompanyFinancials> companies) {
        return find(companies, company -> toDouble(company.latestYear().revenue()), true);
    }

    public static Optional<CompanyFinancials> highestNetIncome(List<CompanyFinancials> companies) {
        return find(companies, company -> toDouble(company.latestYear().netIncome()), true);
    }

    public static Optional<CompanyFinancials> highestNetProfitMargin(List<CompanyFinancials> companies) {
        return find(companies, company -> latestRatios(company).netProfitMargin(), true);
    }

    public static Optional<CompanyFinancials> lowestDebtToEquity(List<CompanyFinancials> companies) {
        return find(companies, company -> latestRatios(company).debtToEquity(), false);
    }

    /**
     * Finds the company with the highest (or lowest) value of a metric.
     *
     * @param metric      a function that pulls one number out of a company, or null if missing
     * @param wantHighest true to find the highest value, false to find the lowest
     * @return the matching company, or Optional.empty() if no company has this value.
     *         If two companies tie exactly, the one entered first is returned.
     */
    private static Optional<CompanyFinancials> find(List<CompanyFinancials> companies,
                                                    Function<CompanyFinancials, Double> metric,
                                                    boolean wantHighest) {
        CompanyFinancials winner = null;
        Double winningValue = null;

        for (CompanyFinancials company : companies) {
            Double value = metric.apply(company);
            if (value == null) {
                continue; // missing data: this company can't be compared on this metric
            }
            // "||" stops at the first true condition, so winningValue is never read while null.
            if (winner == null
                    || (wantHighest && value > winningValue)
                    || (!wantHighest && value < winningValue)) {
                winner = company;
                winningValue = value;
            }
        }
        return Optional.ofNullable(winner);
    }

    private static Double toDouble(Long value) {
        return value == null ? null : value.doubleValue();
    }
}
