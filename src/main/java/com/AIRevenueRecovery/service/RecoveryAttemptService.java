package com.AIRevenueRecovery.service;

import com.AIRevenueRecovery.entity.Payment;
import com.AIRevenueRecovery.entity.PaymentStatus;
import com.AIRevenueRecovery.entity.RecoveryAttempt;
import com.AIRevenueRecovery.entity.RecoveryPlan;
import com.AIRevenueRecovery.exception.RecoveryAttemptNotFoundException;
import com.AIRevenueRecovery.repository.PaymentRepository;
import com.AIRevenueRecovery.repository.RecoveryAttemptRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;
@Service
public class RecoveryAttemptService {
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private final RecoveryAttemptRepository recoveryAttemptRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentGatewayService paymentGatewayService;
    private final AIRecoveryDecisionService aiRecoveryDecisionService;
    private final RetrySchedulingService retrySchedulingService;
    private final EmailService emailService;
    private final RecoveryEventService recoveryEventService;

    public RecoveryAttemptService(
            RecoveryAttemptRepository recoveryAttemptRepository,
            PaymentRepository paymentRepository,
            PaymentGatewayService paymentGatewayService,
            AIRecoveryDecisionService aiRecoveryDecisionService,
            EmailService emailService,
            RetrySchedulingService retrySchedulingService,
            RecoveryEventService recoveryEventService) {

        this.recoveryAttemptRepository = recoveryAttemptRepository;
        this.paymentRepository = paymentRepository;
        this.paymentGatewayService = paymentGatewayService;
        this.aiRecoveryDecisionService = aiRecoveryDecisionService;
        this.retrySchedulingService = retrySchedulingService;
        this.emailService = emailService;
        this.recoveryEventService = recoveryEventService;
    }
    @Transactional
    public RecoveryAttempt createAttempt(
            RecoveryAttempt recoveryAttempt) {
        Payment payment = recoveryAttempt.getPayment();
        if (payment == null || payment.getId() == null) {
            throw new RuntimeException("Payment is required for recovery attempt");
        }
        Long paymentId = payment.getId();
        payment = paymentRepository.findById(paymentId).orElseThrow(() -> new RuntimeException("Payment not found with ID: " + paymentId));
        recoveryAttempt.setPayment(payment);
        recoveryAttempt.setFailureReason(payment.getFailureReason()
        );
        applyAIDecision(recoveryAttempt, payment);
        if (recoveryAttempt.getAttemptNumber() == null) {
            recoveryAttempt.setAttemptNumber(1);
        }

        if (recoveryAttempt.getAttemptedAt() == null) {
            recoveryAttempt.setAttemptedAt(
                    LocalDateTime.now()
            );
        }
        if (recoveryAttempt.getResult() == null) {
            recoveryAttempt.setResult("PENDING");
        }
        RecoveryAttempt savedAttempt = recoveryAttemptRepository.save(recoveryAttempt);
        recordEvent(payment, null, "RECOVERY_ATTEMPT_CREATED", savedAttempt.getAction(),
                "PENDING", "Recovery attempt created", "attemptId=" + savedAttempt.getId()
        );
        recordEvent(payment, null,
                "AI_DECISION_MADE", savedAttempt.getAction(), "SUCCESS", savedAttempt.getAiRecommendation(),
                "confidence=" + savedAttempt.getAiConfidence()
        );
        return savedAttempt;
    }

    private void applyAIDecision(RecoveryAttempt attempt, Payment payment) {
        try {
            AIRecoveryDecisionService.RecoveryDecision decision = aiRecoveryDecisionService.decide(payment);
            attempt.setAction(decision.action());
            String recommendation = decision.recommendation();
            if (recommendation != null && recommendation.length() > 60000) {
                recommendation = recommendation.substring(0, 60000);
            }
            attempt.setAiRecommendation(recommendation);
            attempt.setAiConfidence(decision.confidence());
        } catch (Exception exception) {
            System.err.println("AI recovery decision failed: " + exception.getMessage());
        }
    }
    public List<RecoveryAttempt> getAllAttempts() {
        return recoveryAttemptRepository.findAll();
    }
    public RecoveryAttempt getAttemptById(Long attemptId) {
        return recoveryAttemptRepository.findById(attemptId).orElseThrow(() -> new RecoveryAttemptNotFoundException(
                                "Recovery attempt not found with ID: " + attemptId));
    }

    public List<RecoveryAttempt> getAttemptsByPaymentId(Long paymentId) {
        return recoveryAttemptRepository.findByPaymentIdOrderByAttemptNumberAsc(paymentId);
    }
    @Transactional
    public RecoveryAttempt updateResult(Long attemptId, String result) {
        RecoveryAttempt attempt = recoveryAttemptRepository.findById(attemptId)
                        .orElseThrow(() -> new RecoveryAttemptNotFoundException("Recovery attempt not found with ID: " + attemptId));
        Payment payment = attempt.getPayment();
        if (payment == null) {
            throw new RuntimeException("Payment not found for recovery attempt: " + attemptId
            );
        }
        if (result == null || result.isBlank()) {throw new RuntimeException("Result is required");
        }
        result = result.trim().toUpperCase();
        if (!result.equals("SUCCESS") && !result.equals("FAILED")) {
            throw new RuntimeException("Result must be SUCCESS or FAILED");
        }
        if ("SUCCESS".equalsIgnoreCase(
                attempt.getResult())) {

            throw new RuntimeException("Recovery attempt is already successful");
        }
        attempt.setResult(result);
        if ("SUCCESS".equals(result)) {
            payment.setStatus(PaymentStatus.SUCCESS);
            payment.setFailureReason(null);
            payment.setNextRetryAt(null);
        } else {
            int attemptNumber = attempt.getAttemptNumber();
            if (attemptNumber < MAX_RETRY_ATTEMPTS) {
                payment.setStatus(PaymentStatus.RETRYING);
                LocalDateTime nextRetry = retrySchedulingService.calculateNextRetry(payment.getFailureReason(), attemptNumber);
                payment.setNextRetryAt(nextRetry);
            } else {
                payment.setStatus(PaymentStatus.FAILED
                );
                payment.setNextRetryAt(null);
            }
        }

        payment.setUpdatedAt(LocalDateTime.now()
        );
        paymentRepository.save(payment);
        RecoveryAttempt savedAttempt = recoveryAttemptRepository.save(attempt);
        recordEvent(payment, null, "RECOVERY_RESULT_UPDATED",
                attempt.getAction(), result,
                "Recovery attempt result updated", "attemptId=" + attemptId
        );
        if ("SUCCESS".equals(result)) {
            recordEvent(payment, null,
                    "RECOVERY_SUCCEEDED", attempt.getAction(), "SUCCESS",
                    "Payment successfully recovered", "attemptId=" + attemptId
            );
        } else {
            recordEvent(payment,
                    null, "RECOVERY_FAILED",
                    attempt.getAction(),
                    "FAILED",
                    "Recovery attempt failed",
                    "attemptId=" + attemptId
            );
        }
        return savedAttempt;
    }
    @Transactional
    public RecoveryAttempt processAttempt(Long attemptId) {
        RecoveryAttempt attempt = recoveryAttemptRepository.findById(attemptId).orElseThrow(() ->
                                new RecoveryAttemptNotFoundException("Recovery attempt not found with ID: " + attemptId));
        if (attempt.getPayment() == null) {
            throw new RuntimeException(
                    "Payment not found for recovery attempt: "
                            + attemptId
            );
        }

        Long paymentId = attempt.getPayment().getId();
        Payment payment = paymentRepository.findById(paymentId).orElseThrow(() -> new RuntimeException("Payment not found with ID: " + paymentId));
        if (attempt.getFailureReason() == null) {
            attempt.setFailureReason(payment.getFailureReason());
        }
        if (attempt.getAttemptNumber() == null) {
            attempt.setAttemptNumber(payment.getRetryCount() == null ? 1 : payment.getRetryCount() + 1);
        }
        if ("SUCCESS".equalsIgnoreCase(
                attempt.getResult())) {
            throw new RuntimeException("Attempt is already successful");
        }
        if (payment.getStatus() != PaymentStatus.RETRYING) {
            throw new RuntimeException("Payment is not in retrying state. Current status: " + payment.getStatus());
        }
        applyAIDecision(attempt, payment);
        if (attempt.getAttemptedAt() == null) {
            attempt.setAttemptedAt(LocalDateTime.now());
        }
        recoveryAttemptRepository.save(attempt);
        recordEvent(payment,
                null, "RECOVERY_ACTION_STARTED",
                attempt.getAction(), "PROCESSING",
                "Recovery action execution started", "attemptId=" + attemptId
        );
        if ("BLOCK_RECOVERY".equalsIgnoreCase(attempt.getAction())) {
            attempt.setResult("FAILED");
            payment.setStatus(PaymentStatus.FAILED);
            payment.setNextRetryAt(null);
            recordEvent(payment, null,
                    "RECOVERY_BLOCKED", attempt.getAction(), "FAILED", attempt.getAiRecommendation(),
                    "attemptId=" + attemptId);

        } else if ("REQUEST_ALTERNATE_PAYMENT".equalsIgnoreCase(attempt.getAction())) {
            if (payment.getRecoveryEmailSentAt() == null) {
                try {
                    emailService.sendPaymentRecoveryEmail(payment, attempt.getAiRecommendation());
                    payment.setRecoveryEmailSentAt(LocalDateTime.now());
                    recordEvent(
                            payment, null, "ALTERNATE_PAYMENT_REQUESTED", attempt.getAction(),
                            "SUCCESS", attempt.getAiRecommendation(), "attemptId=" + attemptId
                    );
                } catch (Exception exception) {
                    recordEvent(payment, null,
                            "RECOVERY_EMAIL_FAILED", attempt.getAction(), "FAILED", exception.getMessage(),
                            "attemptId=" + attemptId
                    );
                }
            }

            attempt.setResult("FAILED");
            payment.setStatus(PaymentStatus.FAILED);
            payment.setNextRetryAt(null);
        } else if ("SEND_PAYMENT_REMINDER".equalsIgnoreCase(attempt.getAction())) {
            if (payment.getRecoveryEmailSentAt() == null) {
                try {
                    emailService.sendPaymentRecoveryEmail(payment, attempt.getAiRecommendation());
                    payment.setRecoveryEmailSentAt(LocalDateTime.now());
                    recordEvent(payment, null, "PAYMENT_REMINDER_SENT", attempt.getAction(),
                            "SUCCESS", attempt.getAiRecommendation(), "attemptId=" + attemptId
                    );
                } catch (Exception exception) {

                    recordEvent(payment, null,
                            "RECOVERY_EMAIL_FAILED", attempt.getAction(), "FAILED", exception.getMessage(),
                            "attemptId=" + attemptId);
                }
            }
            attempt.setResult("FAILED");
            payment.setStatus(PaymentStatus.FAILED);
            payment.setNextRetryAt(null);
        } else {
            boolean paymentSuccessful = paymentGatewayService.processPayment(payment, attempt.getAttemptNumber());
            if (paymentSuccessful) {
                attempt.setResult("SUCCESS");
                payment.setStatus(PaymentStatus.SUCCESS);
                payment.setFailureReason(null);
                payment.setNextRetryAt(null);
                recordEvent(payment,
                        null, "PAYMENT_RETRY_SUCCEEDED", attempt.getAction(),
                        "SUCCESS", "Payment retry succeeded", "attemptId=" + attemptId
                );

            } else {

                attempt.setResult("FAILED");

                int currentAttempt =
                        attempt.getAttemptNumber();

                if (currentAttempt < MAX_RETRY_ATTEMPTS) {
                    payment.setStatus(PaymentStatus.RETRYING);
                    LocalDateTime nextRetry = retrySchedulingService.calculateNextRetry(payment.getFailureReason(), currentAttempt);
                    payment.setNextRetryAt(nextRetry
                    );
                } else {
                    payment.setStatus(PaymentStatus.FAILED
                    );
                    payment.setNextRetryAt(null);
                }
                recordEvent(payment, null, "PAYMENT_RETRY_FAILED",
                        attempt.getAction(), "FAILED", "Payment retry failed", "attemptId=" + attemptId
                );
            }
        }
        payment.setUpdatedAt(LocalDateTime.now());
        paymentRepository.save(payment);
        return recoveryAttemptRepository.save(attempt);
    }
    private void recordEvent(
            Payment payment, RecoveryPlan recoveryPlan,
            String eventType, String action, String status, String message, String metadata) {
        try {
            recoveryEventService.recordEvent(payment, recoveryPlan, eventType,
                    action, status, message, metadata);
        } catch (Exception exception) {
            System.err.println("Failed to record recovery event: " + exception.getMessage()
            );
        }
    }
}