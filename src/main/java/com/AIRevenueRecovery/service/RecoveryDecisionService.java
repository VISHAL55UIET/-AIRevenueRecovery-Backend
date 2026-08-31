package com.AIRevenueRecovery.service;

import com.AIRevenueRecovery.entity.FailureReason;
import com.AIRevenueRecovery.entity.RecoveryAttempt;
import com.AIRevenueRecovery.repository.RecoveryAttemptRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RecoveryDecisionService {
    private final RecoveryAttemptRepository recoveryAttemptRepository;
    public RecoveryDecisionService(RecoveryAttemptRepository recoveryAttemptRepository) {
        this.recoveryAttemptRepository = recoveryAttemptRepository;
    }
    public String decideAction(FailureReason failureReason) {
        return decideAction(failureReason, 0);
    }
    public String decideAction(FailureReason failureReason, Integer retryCount) {

        if (failureReason == null) {
            return "MANUAL_REVIEW";
        }

        int retries = retryCount != null ? retryCount : 0;
        if (failureReason == FailureReason.FRAUD_DETECTED) {
            return "BLOCK_RECOVERY";
        }

        if (failureReason == FailureReason.EXPIRED_CARD) {
            return "REQUEST_CARD_UPDATE";
        }
        if (retries >= 5) {
            return "BLOCK_RECOVERY";
        }
        List<RecoveryAttempt> attempts = recoveryAttemptRepository.findAll();
        int totalAttempts = 0;
        int successfulAttempts = 0;
        for (RecoveryAttempt attempt : attempts) {
            if (attempt == null) {
                continue;
            }
            if (attempt.getFailureReason()
                    != failureReason) {
                continue;
            }

            totalAttempts++;
            String result = attempt.getResult();
            if ("SUCCESS".equalsIgnoreCase(result)) {successfulAttempts++;
            }
        }
        double successRate = 0.0;
        if (totalAttempts > 0) {successRate = (successfulAttempts * 100.0) / totalAttempts;
        }
        switch (failureReason) {
            case INSUFFICIENT_FUNDS: if (retries <= 1) {
                return "RETRY_AFTER_BALANCE_CHECK";
                }
                if (totalAttempts >= 5 && successRate >= 30.0) {
                    return "RETRY_AFTER_BALANCE_CHECK";
                }
                if (totalAttempts >= 5 && successRate < 20.0) {
                    return "REQUEST_ALTERNATE_PAYMENT";
                }return "RETRY_AFTER_BALANCE_CHECK";
            case CARD_DECLINED: if (retries >= 2) {
                    return "REQUEST_ALTERNATE_PAYMENT";
                }
                if (totalAttempts >= 5 && successRate < 15.0) {
                    return "REQUEST_ALTERNATE_PAYMENT";
                }
                return "REQUEST_ALTERNATE_PAYMENT";
            case NETWORK_ERROR:
                if (retries <= 2) {return "RETRY_PAYMENT";
                }
                if (totalAttempts >= 5 && successRate < 10.0) {return "REQUEST_ALTERNATE_PAYMENT";
                }
                return "RETRY_AFTER_DELAY";
            case BANK_ERROR:
                if (retries <= 2) {return "RETRY_AFTER_DELAY";
                }
                if (totalAttempts >= 5 && successRate < 15.0) {return "REQUEST_ALTERNATE_PAYMENT";
                }
                return "RETRY_AFTER_DELAY";
            case FRAUD_DETECTED: return "BLOCK_RECOVERY";
            case UNKNOWN: return "MANUAL_REVIEW";

            default:
                return "MANUAL_REVIEW";
        }
    }
}