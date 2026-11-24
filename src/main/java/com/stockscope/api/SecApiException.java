package com.stockscope.api;

/**
 * Thrown when StockScope cannot get usable data from the SEC.
 *
 * The message is written for the user (for example "Could not connect to the SEC"),
 * so the console can print it directly instead of showing a stack trace.
 *
 * This is a checked exception (it extends Exception, not RuntimeException), so the
 * compiler forces every caller to decide what to do when the SEC can't be reached.
 */
public class SecApiException extends Exception {

    /** The HTTP status code (e.g. 404), or 0 if the problem wasn't an HTTP error. */
    private final int statusCode;

    /** For problems with no HTTP status, like no internet connection or unreadable JSON. */
    public SecApiException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = 0;
    }

    /** For when the SEC answered, but with an error status code. */
    public SecApiException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
