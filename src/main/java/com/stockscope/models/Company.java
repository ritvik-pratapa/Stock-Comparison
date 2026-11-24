package com.stockscope.models;

/**
 * A publicly traded company, as listed in the SEC's ticker file.
 *
 * This is a Java "record": a short way to write a class that just holds data.
 * Java automatically generates the constructor, the getters (ticker(), cik(), name()),
 * and equals/hashCode/toString. Records are immutable - their fields can't change.
 *
 * @param ticker the stock symbol, e.g. "AAPL"
 * @param cik    the SEC's Central Index Key, a unique number for each company, e.g. 320193
 * @param name   the company name as the SEC lists it, e.g. "Apple Inc."
 */
public record Company(String ticker, int cik, String name) {

    /**
     * SEC API addresses need the CIK as exactly 10 digits, padded with leading zeros.
     * For example 320193 becomes "0000320193".
     */
    public String paddedCik() {
        return String.format("%010d", cik);
    }
}
