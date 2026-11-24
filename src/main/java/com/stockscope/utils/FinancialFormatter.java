package com.stockscope.utils;

import java.util.Locale;

/**
 * Turns raw numbers into short, readable text for the console, e.g. 394328000000 -> "$394.3B".
 *
 * This is a "utility class": it only has static methods, so it's never instantiated.
 * The private constructor enforces that.
 */
public final class FinancialFormatter {

    private FinancialFormatter() {
    }

    /** Formats a dollar amount with T/B/M suffixes. Missing values (null) become "N/A". */
    public static String money(Long dollars) {
        if (dollars == null) {
            return "N/A";
        }
        String sign = dollars < 0 ? "-" : "";
        double abs = Math.abs((double) dollars);

        // Locale.US guarantees a "." decimal point, whatever country the computer is set to.
        if (abs >= 1e12) {
            return sign + String.format(Locale.US, "$%.2fT", abs / 1e12);
        }
        if (abs >= 1e9) {
            return sign + String.format(Locale.US, "$%.1fB", abs / 1e9);
        }
        if (abs >= 1e6) {
            return sign + String.format(Locale.US, "$%.1fM", abs / 1e6);
        }
        return sign + String.format(Locale.US, "$%,.0f", abs);
    }

    /** Formats a percentage with one decimal place, e.g. 24.31 -> "24.3%". */
    public static String percent(Double value) {
        if (value == null) {
            return "N/A";
        }
        return String.format(Locale.US, "%.1f%%", value); // "%%" prints a literal % sign
    }

    /** Formats a plain ratio with two decimal places, e.g. 1.847 -> "1.85". */
    public static String ratio(Double value) {
        if (value == null) {
            return "N/A";
        }
        return String.format(Locale.US, "%.2f", value);
    }
}
