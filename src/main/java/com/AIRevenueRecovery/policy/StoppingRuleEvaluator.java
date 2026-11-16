
package com.AIRevenueRecovery.policy;

import org.springframework.stereotype.Component;

@Component
public class StoppingRuleEvaluator {

    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final int MAX_RECOVERY_ATTEMPTS = 5;

    /**
     * Check whether payment retry should stop.
     */
    public boolean shouldStopRetry(int retryAttempts) {
        return retryAttempts >= MAX_RETRY_ATTEMPTS;
    }

    /**
     * Check whether revenue recovery should stop.
     */
    public boolean shouldStopRecovery(
            int recoveryAttempts,
            String paymentStatus) {

        if (paymentStatus == null) {
            return true;
        }

        if ("SUCCESS".equalsIgnoreCase(paymentStatus)
                || "RECOVERED".equalsIgnoreCase(paymentStatus)) {
            return true;
        }

        return recoveryAttempts >= MAX_RECOVERY_ATTEMPTS;
    }
    public boolean isTerminalStatus(String paymentStatus) {

        if (paymentStatus == null) {
            return false;
        }

        return "SUCCESS".equalsIgnoreCase(paymentStatus)
                || "RECOVERED".equalsIgnoreCase(paymentStatus)
                || "CANCELLED".equalsIgnoreCase(paymentStatus);
    }
}
