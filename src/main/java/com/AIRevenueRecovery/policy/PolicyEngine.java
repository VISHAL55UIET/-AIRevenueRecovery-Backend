
package com.AIRevenueRecovery.policy;

import com.AIRevenueRecovery.entity.FailureReason;
import com.AIRevenueRecovery.entity.Payment;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.LinkedHashSet;
import java.util.Set;

import static com.AIRevenueRecovery.policy.RecoveryActions.*;

@Component
public class PolicyEngine {

    public static final String RULE_ACCEPTED = "PROPOSAL_ACCEPTED";

    private static final LocalTime QUIET_HOURS_START =
            LocalTime.of(21, 0);

    private static final LocalTime QUIET_HOURS_END =
            LocalTime.of(9, 0);

    private final int maxRetries;
    private final double humanApprovalThreshold;

    public PolicyEngine(
            @Value("${recovery.policy.max-retries:3}") int maxRetries,
            @Value("${recovery.policy.human-approval-threshold:50000}")
            double humanApprovalThreshold) {

        this.maxRetries = maxRetries;
        this.humanApprovalThreshold = humanApprovalThreshold;
    }

    /**
     * Actions allowed for each failure reason.
     */
    public Set<String> allowedActions(
            FailureReason reason,
            int retryCount) {

        FailureReason r = reason == null
                ? FailureReason.UNKNOWN : reason;

        Set<String> allowed = new LinkedHashSet<>();

        switch (r) {

            case FRAUD_DETECTED ->
                    allowed.add(BLOCK_RECOVERY);

            case EXPIRED_CARD ->
                    allowed.add(REQUEST_ALTERNATE_PAYMENT);

            case CARD_DECLINED -> {
                allowed.add(REQUEST_ALTERNATE_PAYMENT);
                allowed.add(SEND_PAYMENT_REMINDER);
            }

            case NETWORK_ERROR, BANK_ERROR -> {
                if (retryCount < maxRetries) {
                    allowed.add(AUTOMATIC_RETRY);
                }
                allowed.add(SEND_PAYMENT_REMINDER);
                allowed.add(REQUEST_ALTERNATE_PAYMENT);
            }

            case INSUFFICIENT_FUNDS -> {
                allowed.add(SEND_PAYMENT_REMINDER);

                if (retryCount == 0) {
                    allowed.add(AUTOMATIC_RETRY);
                }

                allowed.add(REQUEST_ALTERNATE_PAYMENT);
            }

            case UNKNOWN -> {
                allowed.add(SEND_PAYMENT_REMINDER);
                allowed.add(REQUEST_ALTERNATE_PAYMENT);
            }
        }

        allowed.add(BLOCK_RECOVERY);

        if (r != FailureReason.FRAUD_DETECTED) {
            allowed.add(ESCALATE_TO_HUMAN);
        }

        return allowed;
    }

    /**
     * Deterministic default action.
     */
    public String defaultAction(
            FailureReason reason,
            int retryCount) {

        FailureReason r = reason == null
                ? FailureReason.UNKNOWN : reason;

        return switch (r) {

            case FRAUD_DETECTED -> BLOCK_RECOVERY;

            case EXPIRED_CARD, CARD_DECLINED ->
                    REQUEST_ALTERNATE_PAYMENT;

            case NETWORK_ERROR, BANK_ERROR ->
                    retryCount < maxRetries
                            ? AUTOMATIC_RETRY
                            : REQUEST_ALTERNATE_PAYMENT;

            case INCORRECT_CVC -> null;
            case PROCESSING_ERROR -> null;
            case AUTHENTICATION_REQUIRED -> null;
            case INSUFFICIENT_FUNDS, UNKNOWN ->
                    SEND_PAYMENT_REMINDER;
        };
    }

    /**
     * Evaluate proposed recovery action.
     */
    public PolicyDecision evaluate(
            Payment payment,
            String proposedAction) {

        if (payment == null) {
            return new PolicyDecision(
                    BLOCK_RECOVERY,
                    "PAYMENT_MISSING",
                    "Payment is missing."
            );
        }

        if (isOptedOut(payment)) {
            return new PolicyDecision(
                    BLOCK_RECOVERY,
                    "CUSTOMER_OPTED_OUT",
                    "Customer opted out. All recovery outreach is stopped."
            );
        }

        FailureReason reason = payment.getFailureReason() == null
                ? FailureReason.UNKNOWN
                : payment.getFailureReason();

        int retryCount = payment.getRetryCount() == null
                ? 0 : payment.getRetryCount();

        if (reason == FailureReason.FRAUD_DETECTED) {
            return new PolicyDecision(
                    BLOCK_RECOVERY,
                    "FRAUD_NEVER_RETRY",
                    "Fraud failures are never retried or chased."
            );
        }

        Set<String> allowed = allowedActions(reason, retryCount);
        String proposed = normalize(proposedAction);

        String candidate;
        String rule;
        String why;

        if (proposed == null) {

            candidate = defaultAction(reason, retryCount);
            rule = "NO_PROPOSAL_USED_DEFAULT";
            why = "No usable proposal. Default action for "
                    + reason + " applied.";

        } else if (!isKnown(proposed)) {

            candidate = defaultAction(reason, retryCount);
            rule = "UNKNOWN_ACTION_USED_DEFAULT";
            why = "Unknown action " + proposed
                    + ". Default action for " + reason + " applied.";

        } else if (!allowed.contains(proposed)) {

            candidate = defaultAction(reason, retryCount);

            boolean retryCap = AUTOMATIC_RETRY.equals(proposed)
                    && retryCount >= maxRetries;

            rule = retryCap
                    ? "RETRY_CAP_REACHED"
                    : "ACTION_NOT_ALLOWED_FOR_" + reason.name();

            why = proposed + " is not allowed for " + reason
                    + " at retry count " + retryCount
                    + ". Replaced with " + candidate + ".";

        } else {

            candidate = proposed;
            rule = RULE_ACCEPTED;
            why = proposed + " is allowed for " + reason + ".";
        }

        if (!BLOCK_RECOVERY.equals(candidate)
                && !ESCALATE_TO_HUMAN.equals(candidate)
                && exceedsApprovalThreshold(payment)) {

            return new PolicyDecision(
                    ESCALATE_TO_HUMAN,
                    "HIGH_VALUE_NEEDS_APPROVAL",
                    String.format(
                            "Amount %.2f is above the human-approval threshold of %.2f.",
                            payment.getAmount(),
                            humanApprovalThreshold
                    )
            );
        }

        return new PolicyDecision(candidate, rule, why);
    }

    /**
     * Evaluate whether recovery can start.
     */
    public PolicyDecision evaluateStart(Payment payment) {

        if (payment == null) {
            return new PolicyDecision(
                    BLOCK_RECOVERY,
                    "PAYMENT_MISSING",
                    "Payment is missing."
            );
        }

        if (isOptedOut(payment)) {
            return new PolicyDecision(
                    BLOCK_RECOVERY,
                    "CUSTOMER_OPTED_OUT",
                    "Customer opted out of recovery."
            );
        }

        FailureReason reason = payment.getFailureReason() == null
                ? FailureReason.UNKNOWN
                : payment.getFailureReason();

        int retryCount = payment.getRetryCount() == null
                ? 0 : payment.getRetryCount();

        if (reason == FailureReason.FRAUD_DETECTED) {
            return new PolicyDecision(
                    BLOCK_RECOVERY,
                    "FRAUD_NEVER_RETRY",
                    "Fraud failures cannot enter recovery."
            );
        }

        if (retryCount >= maxRetries) {
            return new PolicyDecision(
                    BLOCK_RECOVERY,
                    "RETRY_CAP_REACHED",
                    "Maximum retry limit has been reached."
            );
        }

        if (exceedsApprovalThreshold(payment)) {
            return new PolicyDecision(
                    ESCALATE_TO_HUMAN,
                    "HIGH_VALUE_NEEDS_APPROVAL",
                    "High-value payment requires human approval."
            );
        }

        return new PolicyDecision(
                AUTOMATIC_RETRY,
                "RECOVERY_START_ALLOWED",
                "Payment is eligible to start recovery."
        );
    }

    /**
     * Customer-facing action check.
     */
    public boolean isCustomerFacing(String action) {

        return action != null
                && (SEND_PAYMENT_REMINDER.equalsIgnoreCase(action)
                || REQUEST_ALTERNATE_PAYMENT.equalsIgnoreCase(action));
    }

    public boolean isQuietHour(LocalDateTime time) {

        LocalTime t = time.toLocalTime();

        return t.isBefore(QUIET_HOURS_END)
                || !t.isBefore(QUIET_HOURS_START);
    }

    /**
     * Respect customer communication quiet hours.
     */
    public LocalDateTime respectQuietHours(
            String action,
            LocalDateTime time) {

        if (!isCustomerFacing(action) || !isQuietHour(time)) {
            return time;
        }

        if (time.toLocalTime().isBefore(QUIET_HOURS_END)) {
            return time.toLocalDate().atTime(QUIET_HOURS_END);
        }

        return time.toLocalDate()
                .plusDays(1)
                .atTime(QUIET_HOURS_END);
    }

    private boolean isOptedOut(Payment payment) {

        return payment.getCustomer() != null
                && payment.getCustomer().isOptedOut();
    }

    private boolean exceedsApprovalThreshold(Payment payment) {

        return payment.getAmount() != null
                && payment.getAmount() > humanApprovalThreshold;
    }

    private String normalize(String action) {

        return action == null || action.isBlank()
                ? null
                : action.trim().toUpperCase();
    }

}
