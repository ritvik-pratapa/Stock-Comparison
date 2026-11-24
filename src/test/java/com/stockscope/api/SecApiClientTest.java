package com.stockscope.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the parts of SecApiClient that don't need the internet: the cache and the
 * error messages. (Real downloads are checked by running the app.)
 */
class SecApiClientTest {

    @Test
    void usesAFreshCacheFileInsteadOfDownloading(@TempDir Path cacheDir) throws IOException, SecApiException {
        // If the cache were ignored, the client would download the real SEC file instead,
        // and this made-up content would not come back.
        Files.writeString(cacheDir.resolve("company_tickers.json"), "{\"from\": \"cache\"}");

        String json = new SecApiClient(cacheDir).fetchCompanyTickersJson();

        assertEquals("{\"from\": \"cache\"}", json);
    }

    @Test
    void httpErrorMessagesTellTheUserWhatToDo() {
        assertTrue(SecApiClient.describeHttpError(403).contains("STOCKSCOPE_CONTACT_EMAIL"));
        assertTrue(SecApiClient.describeHttpError(404).contains("HTTP 404"));
        assertTrue(SecApiClient.describeHttpError(429).contains("Wait a minute"));
        assertTrue(SecApiClient.describeHttpError(503).contains("Try again later"));
        assertTrue(SecApiClient.describeHttpError(418).contains("Unexpected response"));
    }

    @Test
    void exceptionKeepsTheStatusCode() {
        assertEquals(404, new SecApiException("not found", 404).getStatusCode());
        assertEquals(0, new SecApiException("no internet", new IOException()).getStatusCode());
    }
}
