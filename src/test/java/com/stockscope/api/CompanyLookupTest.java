package com.stockscope.api;

import com.stockscope.models.Company;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests ticker lookup with a small sample of the SEC's real format.
 * No network requests are made, so these tests are fast and always give the same result.
 */
class CompanyLookupTest {

    // A "text block" (triple quotes) lets us write multi-line JSON without escaping.
    private static final String SAMPLE_TICKERS = """
            {
              "0": {"cik_str": 320193, "ticker": "AAPL", "title": "Apple Inc."},
              "1": {"cik_str": 789019, "ticker": "MSFT", "title": "MICROSOFT CORP"},
              "2": {"cik_str": 1067983, "ticker": "BRK-B", "title": "BERKSHIRE HATHAWAY INC"}
            }
            """;

    @Test
    void parsesEachTickerIntoACompany() throws SecApiException {
        Map<String, Company> companies = CompanyLookup.parseTickers(SAMPLE_TICKERS);

        assertEquals(3, companies.size());
        Company apple = companies.get("AAPL");
        assertEquals("Apple Inc.", apple.name());
        assertEquals(320193, apple.cik());
    }

    @Test
    void padsCikToTenDigits() {
        Company apple = new Company("AAPL", 320193, "Apple Inc.");
        assertEquals("0000320193", apple.paddedCik());
    }

    @Test
    void normalizesUserInput() {
        assertEquals("AAPL", CompanyLookup.normalize("  aapl  "));
        assertEquals("BRK-B", CompanyLookup.normalize("brk.b"));
    }

    @Test
    void invalidJsonGivesAClearError() {
        SecApiException error = assertThrows(SecApiException.class,
                () -> CompanyLookup.parseTickers("<html>Service Unavailable</html>"));
        assertEquals("The SEC ticker list was not valid JSON.", error.getMessage());
    }

    @Test
    void emptyTickerListIsAnError() {
        assertThrows(SecApiException.class, () -> CompanyLookup.parseTickers("{}"));
    }

    @Test
    void findByTickerWorksEndToEndFromTheCache(@TempDir Path cacheDir) throws IOException, SecApiException {
        // A fresh cache file means no download happens, so this test works offline.
        Files.writeString(cacheDir.resolve("company_tickers.json"), SAMPLE_TICKERS);
        CompanyLookup lookup = new CompanyLookup(new SecApiClient(cacheDir));

        assertEquals("Apple Inc.", lookup.findByTicker(" aapl ").orElseThrow().name());
        assertEquals(1067983, lookup.findByTicker("brk.b").orElseThrow().cik());
        assertTrue(lookup.findByTicker("NOPE").isEmpty()); // unknown ticker: empty, not an error
    }
}
