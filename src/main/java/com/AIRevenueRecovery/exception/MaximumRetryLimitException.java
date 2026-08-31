package com.AIRevenueRecovery.exception;

public class MaximumRetryLimitException extends RuntimeException {

    public MaximumRetryLimitException(String message) {
        super(message);
    }
}