package com.stockscope.utils;

import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Every number the user sees goes through these methods, so each format is pinned down here.
 */
class FinancialFormatterTest {

    @Test
    void moneyPicksTheRightSuffix() {
        assertEquals("$4.42T", FinancialFormatter.money(4_420_000_000_000L));
        assertEquals("$478.8B", FinancialFormatter.money(478_834_000_000L));
        assertEquals("$774.0M", FinancialFormatter.money(774_000_000L));
        assertEquals("$12,345", FinancialFormatter.money(12_345L));
        assertEquals("$0", FinancialFormatter.money(0L));
    }

    @Test
    void moneyPutsTheMinusSignBeforeTheDollarSign() {
        assertEquals("-$2.7B", FinancialFormatter.money(-2_700_000_000L));
        assertEquals("-$435.1M", FinancialFormatter.money(-435_100_000L));
    }

    @Test
    void missingValuesShowAsNA() {
        assertEquals("N/A", FinancialFormatter.money(null));
        assertEquals("N/A", FinancialFormatter.percent(null));
        assertEquals("N/A", FinancialFormatter.ratio(null));
    }

    @Test
    void percentAndRatioRoundCorrectly() {
        assertEquals("27.6%", FinancialFormatter.percent(27.6094));
        assertEquals("-67.7%", FinancialFormatter.percent(-67.68));
        assertEquals("2.61", FinancialFormatter.ratio(2.6094));
        assertEquals("11.21", FinancialFormatter.ratio(11.2063));
    }

    @Test
    void formatIsTheSameOnAComputerSetToAnotherLanguage() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.GERMANY); // would otherwise give "$478,8B"
            assertEquals("$478.8B", FinancialFormatter.money(478_834_000_000L));
            assertEquals("27.6%", FinancialFormatter.percent(27.6094));
        } finally {
            Locale.setDefault(original);
        }
    }
}
