package com.stockscope.api;

import com.stockscope.models.Company;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;

/**
 * Downloads data from the SEC's free EDGAR APIs.
 *
 * This class has four jobs:
 *   1. Send every request with a User-Agent header (the SEC rejects requests without one).
 *   2. Space requests out so we stay well below the SEC's limit of 10 requests per second.
 *   3. Save responses in a local cache folder, so the same file isn't downloaded twice in a day.
 *   4. Turn network and HTTP failures into SecApiExceptions with readable messages.
 *
 * Other classes never build URLs or deal with HTTP - they just ask this class for JSON text.
 */
public class SecApiClient {

    private static final String TICKERS_URL = "https://www.sec.gov/files/company_tickers.json";
    private static final String COMPANY_FACTS_URL = "https://data.sec.gov/api/xbrl/companyfacts/CIK%s.json";

    // The SEC asks every app to identify itself with a contact email in the User-Agent.
    // Reading it from an environment variable keeps your real address out of the source code.
    private static final String CONTACT_ENV_VAR = "STOCKSCOPE_CONTACT_EMAIL";
    private static final String PLACEHOLDER_CONTACT = "your-email@example.com";

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration CACHE_MAX_AGE = Duration.ofHours(24);
    private static final long MIN_MILLIS_BETWEEN_REQUESTS = 200; // at most 5 requests per second

    private final HttpClient httpClient;
    private final String userAgent;
    private final Path cacheDir;
    private long lastRequestMillis = 0;

    public SecApiClient(Path cacheDir) {
        this.cacheDir = cacheDir;
        this.userAgent = "StockScope/1.0 (educational project) " + contactEmail();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /** The SEC's list of every ticker symbol with its company name and CIK number. */
    public String fetchCompanyTickersJson() throws SecApiException {
        return fetch(TICKERS_URL, "company_tickers.json");
    }

    /** Every financial fact a company has reported to the SEC (several MB of JSON). */
    public String fetchCompanyFactsJson(Company company) throws SecApiException {
        String cik = company.paddedCik();
        return fetch(String.format(COMPANY_FACTS_URL, cik), "CIK" + cik + ".json");
    }

    /** Returns the cached copy if it's less than a day old; otherwise downloads and caches it. */
    private String fetch(String url, String cacheFileName) throws SecApiException {
        Path cacheFile = cacheDir.resolve(cacheFileName);
        if (isFresh(cacheFile)) {
            try {
                return Files.readString(cacheFile);
            } catch (IOException e) {
                // The cache file couldn't be read - fall through and download a new copy.
            }
        }

        String body = download(url);
        saveToCache(cacheFile, body);
        return body;
    }

    private String download(String url) throws SecApiException {
        waitForRateLimit();

        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("User-Agent", userAgent)
                .timeout(REQUEST_TIMEOUT)
                .GET()
                .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (HttpTimeoutException e) {
            throw new SecApiException("The SEC took too long to respond. Please try again.", e);
        } catch (IOException e) {
            throw new SecApiException("Could not connect to the SEC. Check your internet connection.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // keep the "interrupted" flag set for the caller
            throw new SecApiException("The download was interrupted.", e);
        }

        int status = response.statusCode();
        if (status == 200) {
            return response.body();
        }
        throw new SecApiException(describeHttpError(status), status);
    }

    /** Turns an HTTP status code into a message that tells the user what to do. */
    private static String describeHttpError(int status) {
        if (status == 403) {
            return "The SEC refused the request (HTTP 403). If you sent many requests quickly, "
                    + "wait a few minutes. Also make sure " + CONTACT_ENV_VAR + " is set to your email.";
        }
        if (status == 404) {
            return "The SEC has no data at that address (HTTP 404).";
        }
        if (status == 429) {
            return "Too many requests to the SEC (HTTP 429). Wait a minute and try again.";
        }
        if (status >= 500) {
            return "The SEC's servers had a problem (HTTP " + status + "). Try again later.";
        }
        return "Unexpected response from the SEC (HTTP " + status + ").";
    }

    /** Sleeps just long enough that requests are at least MIN_MILLIS_BETWEEN_REQUESTS apart. */
    private void waitForRateLimit() throws SecApiException {
        long millisSinceLast = System.currentTimeMillis() - lastRequestMillis;
        if (millisSinceLast < MIN_MILLIS_BETWEEN_REQUESTS) {
            try {
                Thread.sleep(MIN_MILLIS_BETWEEN_REQUESTS - millisSinceLast);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new SecApiException("The download was interrupted.", e);
            }
        }
        lastRequestMillis = System.currentTimeMillis();
    }

    private static boolean isFresh(Path file) {
        try {
            if (!Files.exists(file)) {
                return false;
            }
            Instant lastModified = Files.getLastModifiedTime(file).toInstant();
            return lastModified.isAfter(Instant.now().minus(CACHE_MAX_AGE));
        } catch (IOException e) {
            return false;
        }
    }

    /** Caching is only a speed-up, so a failure here prints a warning instead of stopping the app. */
    private void saveToCache(Path cacheFile, String body) {
        try {
            Files.createDirectories(cacheDir);
            Files.writeString(cacheFile, body);
        } catch (IOException e) {
            System.err.println("Warning: could not save " + cacheFile + " to the cache: " + e.getMessage());
        }
    }

    private static String contactEmail() {
        String contact = System.getenv(CONTACT_ENV_VAR);
        if (contact == null || contact.isBlank()) {
            return PLACEHOLDER_CONTACT;
        }
        return contact.trim();
    }
}
