package com.stockscope.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockscope.models.Company;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Converts ticker symbols like "AAPL" into SEC company records (name + CIK number).
 *
 * The SEC publishes one file listing every ticker. It looks like this:
 *
 *   {"0": {"cik_str": 320193, "ticker": "AAPL", "title": "Apple Inc."},
 *    "1": {"cik_str": 789019, "ticker": "MSFT", "title": "MICROSOFT CORP"}, ...}
 *
 * We download it once, then keep it in a HashMap keyed by ticker, so every lookup
 * after the first is instant instead of scanning ~10,000 entries.
 */
public class CompanyLookup {

    // One ObjectMapper can be reused for the whole program, and creating one is fairly slow.
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final SecApiClient secClient;
    private Map<String, Company> companiesByTicker; // null until the first lookup

    public CompanyLookup(SecApiClient secClient) {
        this.secClient = secClient;
    }

    /**
     * Finds a company by ticker. Input is forgiving: " aapl " and "brk.b" both work.
     *
     * Returns Optional.empty() if the ticker doesn't exist - that's a normal outcome,
     * not an error. A SecApiException means something actually went wrong
     * (e.g. no internet), so the two situations stay clearly separate.
     */
    public Optional<Company> findByTicker(String ticker) throws SecApiException {
        if (companiesByTicker == null) {
            companiesByTicker = parseTickers(secClient.fetchCompanyTickersJson());
        }
        return Optional.ofNullable(companiesByTicker.get(normalize(ticker)));
    }

    /**
     * Converts user input into the SEC's ticker format: uppercase, no spaces, and a
     * dash instead of a dot for share classes (people type "BRK.B", the SEC uses "BRK-B").
     */
    static String normalize(String ticker) {
        return ticker.trim().toUpperCase().replace('.', '-');
    }

    /** Reads the SEC ticker file into a map. Kept separate from downloading so tests can use sample JSON. */
    static Map<String, Company> parseTickers(String json) throws SecApiException {
        JsonNode root;
        try {
            root = MAPPER.readTree(json);
        } catch (JsonProcessingException e) {
            throw new SecApiException("The SEC ticker list was not valid JSON.", e);
        }

        Map<String, Company> companies = new HashMap<>();
        // Looping over a JSON object with Jackson gives you each of its values.
        for (JsonNode entry : root) {
            String ticker = entry.path("ticker").asText();
            int cik = entry.path("cik_str").asInt();
            String name = entry.path("title").asText();

            if (!ticker.isEmpty() && cik > 0) {
                companies.put(ticker.toUpperCase(), new Company(ticker.toUpperCase(), cik, name));
            }
        }

        if (companies.isEmpty()) {
            throw new SecApiException("The SEC ticker list was empty or in an unexpected format.", null);
        }
        return companies;
    }
}
