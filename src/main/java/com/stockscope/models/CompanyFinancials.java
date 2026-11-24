package com.stockscope.models;

import java.util.List;
import java.util.Optional;

/**
 * Everything StockScope knows about one company: who it is and its yearly figures.
 *
 * @param company the company (ticker, CIK, name)
 * @param years   one entry per fiscal year, sorted oldest to newest; never empty
 */
public record CompanyFinancials(Company company, List<FinancialData> years) {

    /**
     * This "compact constructor" runs whenever a CompanyFinancials is created.
     * List.copyOf makes an unchangeable copy, so no other code can modify the list later.
     */
    public CompanyFinancials {
        if (years.isEmpty()) {
            throw new IllegalArgumentException("A company must have at least one year of data");
        }
        years = List.copyOf(years);
    }

    /** The most recent fiscal year we have data for. */
    public FinancialData latestYear() {
        return years.get(years.size() - 1);
    }

    /** Up to {@code count} of the most recent years, oldest first (e.g. for a 5-year trend). */
    public List<FinancialData> recentYears(int count) {
        int start = Math.max(0, years.size() - count);
        return years.subList(start, years.size());
    }

    /**
     * The fiscal year right before the given one, if we have it. Return on assets needs it,
     * because the assets a company had at the START of a year are last year's ending assets.
     */
    public Optional<FinancialData> previousYear(FinancialData year) {
        for (FinancialData candidate : years) {
            if (candidate.fiscalYear() == year.fiscalYear() - 1) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }
}
