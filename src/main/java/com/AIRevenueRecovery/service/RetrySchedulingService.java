package com.AIRevenueRecovery.service;

import com.AIRevenueRecovery.entity.FailureReason;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class RetrySchedulingService {

    public LocalDateTime calculateNextRetry(FailureReason failureReason, int attemptNumber) {
        if (failureReason == null) {
            return null;
        }
        LocalDateTime now = LocalDateTime.now();
        switch (failureReason) {
            case NETWORK_ERROR:
                return now.plusMinutes(30);
            case BANK_ERROR:
                return now.plusHours(2);
            case INSUFFICIENT_FUNDS:
                return now.plusHours(24);
            case CARD_DECLINED:
                return null;
            case EXPIRED_CARD:
                return null;
            case FRAUD_DETECTED:
                return null;
            case UNKNOWN:
                return now.plusHours(6);

            default:
                return null;
        }
    }
}