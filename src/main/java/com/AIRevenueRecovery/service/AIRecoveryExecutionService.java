package com.AIRevenueRecovery.service;

import com.AIRevenueRecovery.entity.Payment;
import com.AIRevenueRecovery.entity.PaymentStatus;
import com.AIRevenueRecovery.entity.RecoveryAttempt;
import com.AIRevenueRecovery.repository.PaymentRepository;
import com.AIRevenueRecovery.repository.RecoveryAttemptRepository;
import org.springframework.stereotype.Service;


import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class AIRecoveryExecutionService {

    private final PaymentRepository paymentRepository;
    private final RecoveryAttemptRepository recoveryAttemptRepository;
    private final AIRecoveryDecisionService aiRecoveryDecisionService;

    public AIRecoveryExecutionService(
            PaymentRepository paymentRepository,
            RecoveryAttemptRepository recoveryAttemptRepository,
            AIRecoveryDecisionService aiRecoveryDecisionService
    ) {
        this.paymentRepository = paymentRepository;
        this.recoveryAttemptRepository = recoveryAttemptRepository;
        this.aiRecoveryDecisionService = aiRecoveryDecisionService;
    }
    public Map<String, Object> executeRecovery(Long paymentId) {
        Payment payment =
                paymentRepository.findById(paymentId).orElseThrow(() -> new IllegalArgumentException("Payment not found: " + paymentId));
        if (payment.getStatus() != PaymentStatus.FAILED) {
            throw new IllegalStateException("AI recovery can only be executed for FAILED payments");
        }
        AIRecoveryDecisionService.RecoveryDecision decision = aiRecoveryDecisionService.decide(payment);
        String action = decision.action();
        if ("SEND_PAYMENT_REMINDER".equals(action)
                && payment.isReminderSent()) {

            Map<String, Object> response =
                    new LinkedHashMap<>();

            response.put(
                    "paymentId",
                    payment.getPaymentId()
            );

            response.put(
                    "databaseId",
                    payment.getId()
            );

            response.put(
                    "failureReason",
                    payment.getFailureReason()
            );

            response.put(
                    "action",
                    action
            );

            response.put(
                    "result",
                    "REMINDER_ALREADY_SENT"
            );

            response.put(
                    "aiRecommendation",
                    decision.recommendation()
            );

            response.put(
                    "aiConfidence",
                    decision.confidence()
            );
            response.put("reminderSentAt", payment.getReminderSentAt());
            return response;
        }
        int attemptNumber = (int) recoveryAttemptRepository.countByPaymentId(payment.getId()) + 1;
        RecoveryAttempt attempt = new RecoveryAttempt();
        attempt.setPayment(payment);
        attempt.setFailureReason(payment.getFailureReason());
        attempt.setAttemptNumber(attemptNumber);
        attempt.setAction(action);
        attempt.setAiRecommendation(decision.recommendation());
        attempt.setAiConfidence(decision.confidence());
        attempt.setAttemptedAt(LocalDateTime.now());
        String result = executeAction(payment, action);

        attempt.setResult(result);
        recoveryAttemptRepository.save(attempt);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("paymentId", payment.getPaymentId());
        response.put("databaseId", payment.getId());
        response.put("failureReason", payment.getFailureReason());
        response.put("attemptNumber", attemptNumber);
        response.put("action", action);
        response.put("result", result);
        response.put("aiRecommendation", decision.recommendation());
        response.put("aiConfidence", decision.confidence());
        response.put("attemptedAt", attempt.getAttemptedAt());
        return response;
    }
    private String executeAction(Payment payment, String action) {
        if (action == null) {
            return "MANUAL_REVIEW_REQUIRED";
        }
        return switch (action) {
            case "AUTOMATIC_RETRY",
                 "RETRY_PAYMENT",
                 "RETRY_AFTER_DELAY",
                 "RETRY_AFTER_BALANCE_CHECK" -> {
                int currentRetryCount = payment.getRetryCount() == null ? 0 : payment.getRetryCount();
                if (currentRetryCount >= 3) {
                    payment.setStatus(PaymentStatus.FAILED);
                    payment.setNextRetryAt(null);
                    paymentRepository.save(payment);
                    yield "MAX_RETRY_LIMIT_REACHED";
                }
                int nextRetryCount = currentRetryCount + 1;
                payment.setRetryCount(nextRetryCount);
                payment.setStatus(PaymentStatus.RETRYING);
                payment.setNextRetryAt(
                        LocalDateTime.now().plusMinutes(30)
                );
                payment.setUpdatedAt(LocalDateTime.now());
                paymentRepository.save(payment);
                yield "RETRY_SCHEDULED";
            }
            case "SEND_PAYMENT_REMINDER" -> {
                if (payment.isReminderSent()) {
                    yield "REMINDER_ALREADY_SENT";
                }
                payment.setReminderSent(true);
                payment.setReminderSentAt(LocalDateTime.now());
                payment.setUpdatedAt(LocalDateTime.now());
                paymentRepository.save(payment);
                yield "PAYMENT_REMINDER_SCHEDULED";
            }
            case "REQUEST_ALTERNATE_PAYMENT", "REQUEST_CARD_UPDATE" -> "ALTERNATE_PAYMENT_REQUESTED";
            case "BLOCK_RECOVERY" -> "RECOVERY_BLOCKED";
            case "MANUAL_REVIEW" -> "MANUAL_REVIEW_REQUIRED";
            default -> "UNKNOWN_AI_ACTION";
        };
    }
}