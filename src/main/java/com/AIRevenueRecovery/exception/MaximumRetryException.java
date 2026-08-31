package com.AIRevenueRecovery.exception;

public class MaximumRetryException extends RuntimeException {
    public MaximumRetryException(String message) {
        super(message);
    }
}