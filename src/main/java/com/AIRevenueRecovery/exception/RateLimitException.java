package com.AIRevenueRecovery.exception;

public class RateLimitException extends RuntimeException {

    private final long retryAfterSeconds;

    public RateLimitException(long retryAfterSeconds) {
        super("Too many requests. Please try again later.");
        this.retryAfterSeconds = retryAfterSeconds;
    }
    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}