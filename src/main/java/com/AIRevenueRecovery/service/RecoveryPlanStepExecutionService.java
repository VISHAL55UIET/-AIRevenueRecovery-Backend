package com.AIRevenueRecovery.service;

import com.AIRevenueRecovery.entity.Payment;
import com.AIRevenueRecovery.entity.PaymentStatus;
import com.AIRevenueRecovery.entity.RecoveryAttempt;
import com.AIRevenueRecovery.entity.RecoveryPlan;
import com.AIRevenueRecovery.entity.RecoveryPlanStep;
import com.AIRevenueRecovery.repository.PaymentRepository;
import com.AIRevenueRecovery.repository.RecoveryAttemptRepository;
import com.AIRevenueRecovery.repository.RecoveryPlanStepRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class RecoveryPlanStepExecutionService {

    private static final int MAX_RETRIES = 3;
    private static final int MAX_RECOVERY_STEPS = 3;

    private final RecoveryPlanStepRepository recoveryPlanStepRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentGatewayService paymentGatewayService;
    private final EmailService emailService;
    private final RecoveryAttemptRepository recoveryAttemptRepository;
    private final RecoveryEventService recoveryEventService;
    private final AIRecoveryDecisionService aiRecoveryDecisionService;

    public RecoveryPlanStepExecutionService(
            RecoveryPlanStepRepository recoveryPlanStepRepository,
            PaymentRepository paymentRepository,
            PaymentGatewayService paymentGatewayService,
            EmailService emailService,
            RecoveryAttemptRepository recoveryAttemptRepository,
            RecoveryEventService recoveryEventService,
            AIRecoveryDecisionService aiRecoveryDecisionService) {

        this.recoveryPlanStepRepository = recoveryPlanStepRepository;
        this.paymentRepository = paymentRepository;
        this.paymentGatewayService = paymentGatewayService;
        this.emailService = emailService;
        this.recoveryAttemptRepository = recoveryAttemptRepository;
        this.recoveryEventService = recoveryEventService;
        this.aiRecoveryDecisionService = aiRecoveryDecisionService;
    }

    @Transactional
    public RecoveryPlanStep executeStep(Long stepId) {

        RecoveryPlanStep step =
                recoveryPlanStepRepository.findById(stepId)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Recovery plan step not found with ID: " + stepId
                                )
                        );

        String currentStatus = normalizeStatus(step.getStatus());

        if ("COMPLETED".equals(currentStatus)
                || "BLOCKED".equals(currentStatus)
                || "WAITING_FOR_PAYMENT".equals(currentStatus)) {
            return step;
        }

        if (!"SCHEDULED".equals(currentStatus)
                && !"PROCESSING".equals(currentStatus)
                && !"FAILED".equals(currentStatus)) {

            throw new RuntimeException(
                    "Recovery plan step cannot be executed from status: "
                            + step.getStatus()
            );
        }

        RecoveryPlan recoveryPlan = step.getRecoveryPlan();

        if (recoveryPlan == null) {
            throw new RuntimeException(
                    "Recovery plan not found for step: " + stepId
            );
        }

        Payment payment = recoveryPlan.getPayment();

        if (payment == null) {
            throw new RuntimeException(
                    "Payment not found for recovery plan step: " + stepId
            );
        }

        if (payment.getStatus() == PaymentStatus.RECOVERED) {

            finalizeSuccessfulRecovery(
                    recoveryPlan,
                    payment,
                    step
            );

            return step;
        }

        LocalDateTime now = LocalDateTime.now();

        step.setStatus("PROCESSING");
        step.setUpdatedAt(now);

        recoveryPlanStepRepository.save(step);

        recordEvent(
                payment,
                recoveryPlan,
                "RECOVERY_STEP_STARTED",
                step.getAction(),
                "PROCESSING",
                "Recovery step execution started."
        );

        try {

            AIRecoveryDecisionService.RecoveryDecision decision =
                    aiRecoveryDecisionService.decide(payment);

            String action =
                    applyRecoveryGuardrails(
                            payment,
                            decision
                    );

            switch (action) {

                case "AUTOMATIC_RETRY" -> {

                    validateRetryAllowed(payment);

                    executeAutomaticRetry(
                            payment,
                            recoveryPlan,
                            step
                    );
                }

                case "SEND_PAYMENT_REMINDER" -> {

                    executePaymentReminder(
                            payment,
                            recoveryPlan,
                            step
                    );
                }

                case "REQUEST_ALTERNATE_PAYMENT" -> {

                    executeAlternatePayment(
                            payment,
                            recoveryPlan,
                            step
                    );
                }

                case "BLOCK_RECOVERY" -> {

                    executeBlockRecovery(
                            payment,
                            recoveryPlan,
                            step
                    );
                }

                default -> {

                    step.setStatus("FAILED");
                    step.setResult("UNKNOWN_ACTION");
                }
            }

            LocalDateTime completedAt = LocalDateTime.now();

            step.setUpdatedAt(completedAt);
            payment.setUpdatedAt(completedAt);

            paymentRepository.save(payment);

            RecoveryPlanStep savedStep = recoveryPlanStepRepository.save(step);


            if ("WAITING_FOR_PAYMENT".equalsIgnoreCase(
                    savedStep.getStatus())) {

                return savedStep;
            }


            if ("BLOCKED".equalsIgnoreCase(
                    savedStep.getStatus())) {

                finalizeBlockedRecovery(
                        recoveryPlan,
                        payment,
                        savedStep
                );

                return savedStep;
            }

            if (payment.getStatus() == PaymentStatus.RECOVERED) {

                finalizeSuccessfulRecovery(
                        recoveryPlan,
                        payment,
                        savedStep
                );

                return savedStep;
            }



            if (shouldContinueRecovery(
                    recoveryPlan,
                    savedStep
            )) {

                createNextStep(
                        recoveryPlan,
                        savedStep
                );

            } else {

                finalizeRecoveryPlan(
                        recoveryPlan,
                        payment,
                        savedStep
                );
            }

            return savedStep;

        } catch (Exception exception) {

            LocalDateTime failedAt = LocalDateTime.now();

            step.setStatus("FAILED");

            step.setResult(
                    exception.getMessage() != null
                            ? exception.getMessage()
                            : "Recovery step execution failed"
            );

            step.setExecutedAt(failedAt);
            step.setUpdatedAt(failedAt);

            payment.setUpdatedAt(failedAt);

            paymentRepository.save(payment);

            recordEvent(
                    payment,
                    recoveryPlan,
                    "RECOVERY_STEP_FAILED",
                    step.getAction(),
                    "FAILED",
                    exception.getMessage() != null
                            ? exception.getMessage()
                            : "Recovery step execution failed"
            );

            return recoveryPlanStepRepository.save(step);
        }
    }

    private void executeAutomaticRetry(
            Payment payment,
            RecoveryPlan recoveryPlan,
            RecoveryPlanStep step) {

        validateRetryAllowed(payment);

        int attemptNumber =
                payment.getRetryCount() == null
                        ? 1
                        : payment.getRetryCount() + 1;

        RecoveryAttempt attempt =
                recoveryAttemptRepository
                        .findByPaymentIdAndAttemptNumber(
                                payment.getId(),
                                attemptNumber
                        )
                        .orElseGet(() -> {

                            RecoveryAttempt newAttempt =
                                    new RecoveryAttempt();

                            newAttempt.setPayment(payment);
                            newAttempt.setFailureReason(
                                    payment.getFailureReason()
                            );
                            newAttempt.setAttemptNumber(
                                    attemptNumber
                            );
                            newAttempt.setAction(
                                    "AUTOMATIC_RETRY"
                            );
                            newAttempt.setAiRecommendation(
                                    step.getAiRecommendation()
                            );
                            newAttempt.setAiConfidence(
                                    step.getAiConfidence()
                            );
                            newAttempt.setAttemptedAt(
                                    LocalDateTime.now()
                            );

                            return newAttempt;
                        });

        payment.setRetryCount(attemptNumber);
        payment.setStatus(PaymentStatus.RETRYING);

        recoveryAttemptRepository.save(attempt);

        recordEvent(
                payment,
                recoveryPlan,
                "PAYMENT_RECOVERY_ATTEMPT",
                "AUTOMATIC_RETRY",
                "STARTED",
                "Recovery payment flow initiated. Attempt "
                        + attemptNumber
        );
        boolean successful =
                paymentGatewayService.processPayment(
                        payment,
                        attemptNumber
                );

        if (successful) {
            attempt.setResult("SUCCESS");

            payment.setStatus(
                    PaymentStatus.RECOVERED
            );

            step.setStatus("COMPLETED");
            step.setResult("SUCCESS");

            recoveryAttemptRepository.save(attempt);

            recordEvent(
                    payment,
                    recoveryPlan,
                    "PAYMENT_RECOVERED",
                    "AUTOMATIC_RETRY",
                    "SUCCESS",
                    "Payment successfully recovered."
            );

            emailService.sendRecoverySuccessEmail(payment);

            recordEvent(
                    payment,
                    recoveryPlan,
                    "COMMUNICATION_SENT",
                    "RECOVERY_SUCCESS",
                    "SUCCESS",
                    "Recovery success email sent successfully."
            );

        } else {

            /*
             * Order creation may have succeeded while the
             * customer has not completed Checkout yet.
             *
             * Therefore DO NOT mark the attempt as FAILED.
             */
            attempt.setResult("PENDING_CHECKOUT");

            step.setStatus("WAITING_FOR_PAYMENT");

            step.setResult(
                    "RAZORPAY_CHECKOUT_REQUIRED"
            );

            recoveryAttemptRepository.save(attempt);

            recordEvent(
                    payment,
                    recoveryPlan,
                    "PAYMENT_CHECKOUT_REQUIRED",
                    "AUTOMATIC_RETRY",
                    "PENDING",
                    "Razorpay order created. Customer payment is required."
            );
        }
    }

    private void executePaymentReminder(
            Payment payment,
            RecoveryPlan recoveryPlan,
            RecoveryPlanStep step) {

        if (payment.isReminderSent()) {

            step.setStatus("COMPLETED");
            step.setResult("REMINDER_ALREADY_SENT");

            recordEvent(
                    payment,
                    recoveryPlan,
                    "COMMUNICATION_SKIPPED",
                    "PAYMENT_REMINDER",
                    "SKIPPED",
                    "Payment reminder was already sent."
            );

            return;
        }

        emailService.sendPaymentReminderEmail(
                payment,
                step.getAiRecommendation()
        );

        payment.setReminderSent(true);
        payment.setReminderSentAt(LocalDateTime.now());

        step.setStatus("COMPLETED");
        step.setResult("EMAIL_SENT");

        recordEvent(
                payment,
                recoveryPlan,
                "COMMUNICATION_SENT",
                "PAYMENT_REMINDER",
                "SUCCESS",
                "Payment reminder email sent successfully."
        );
    }


    private void executeAlternatePayment(
            Payment payment,
            RecoveryPlan recoveryPlan,
            RecoveryPlanStep step) {

        emailService.sendAlternatePaymentEmail(
                payment,
                step.getAiRecommendation()
        );

        step.setStatus("COMPLETED");
        step.setResult("ALTERNATE_PAYMENT_REQUESTED");

        recordEvent(
                payment,
                recoveryPlan,
                "COMMUNICATION_SENT",
                "ALTERNATE_PAYMENT",
                "SUCCESS",
                "Alternate payment request email sent successfully."
        );
    }

    // ============================================================
    // BLOCK RECOVERY
    // ============================================================

    private void executeBlockRecovery(
            Payment payment,
            RecoveryPlan recoveryPlan,
            RecoveryPlanStep step) {

        payment.setStatus(PaymentStatus.FAILED);

        step.setStatus("BLOCKED");
        step.setResult("RECOVERY_BLOCKED");

        recordEvent(
                payment,
                recoveryPlan,
                "RECOVERY_BLOCKED",
                "BLOCK_RECOVERY",
                "BLOCKED",
                step.getAiRecommendation() != null
                        ? step.getAiRecommendation()
                        : "Recovery blocked because the payment is not safe to recover."
        );
    }


    private void validateRetryAllowed(
            Payment payment) {

        int retryCount =
                payment.getRetryCount() == null
                        ? 0
                        : payment.getRetryCount();

        if (retryCount >= MAX_RETRIES) {

            throw new IllegalStateException(
                    "Maximum recovery retry limit reached: "
                            + MAX_RETRIES
            );
        }

        if (payment.getFailureReason() != null) {

            String reason =
                    payment.getFailureReason()
                            .name()
                            .toUpperCase();

            if (reason.contains("FRAUD")
                    || reason.contains("SECURITY")) {

                throw new IllegalStateException(
                        "Automatic retry is blocked for fraud/security failure."
                );
            }

            if (reason.contains("EXPIRED")
                    || reason.contains("INVALID_CARD")) {

                throw new IllegalStateException(
                        "Automatic retry is blocked for unusable card."
                );
            }
        }
    }


    private void createNextStep(
            RecoveryPlan recoveryPlan,
            RecoveryPlanStep completedStep) {

        if (recoveryPlan == null
                || completedStep == null) {
            return;
        }

        int nextStepNumber =
                completedStep.getStepNumber() + 1;

        int maxSteps =
                recoveryPlan.getMaxSteps() != null
                        ? Math.min(
                        recoveryPlan.getMaxSteps(),
                        MAX_RECOVERY_STEPS
                )
                        : MAX_RECOVERY_STEPS;

        if (nextStepNumber > maxSteps) {

            finalizeRecoveryPlan(
                    recoveryPlan,
                    recoveryPlan.getPayment(),
                    completedStep
            );

            return;
        }

        if (recoveryPlanStepRepository
                .findByRecoveryPlanIdAndStepNumber(
                        recoveryPlan.getId(),
                        nextStepNumber
                )
                .isPresent()) {

            return;
        }

        Payment payment =
                recoveryPlan.getPayment();

        if (payment == null) {
            return;
        }

        if (payment.getStatus()
                == PaymentStatus.RECOVERED) {

            finalizeSuccessfulRecovery(
                    recoveryPlan,
                    payment,
                    completedStep
            );

            return;
        }

        AIRecoveryDecisionService.RecoveryDecision decision =
                aiRecoveryDecisionService.decide(payment);

        String nextAction =
                applyRecoveryGuardrails(
                        payment,
                        decision
                );

        LocalDateTime now =
                LocalDateTime.now();

        LocalDateTime scheduledAt =
                calculateNextExecutionTime(
                        nextAction
                );

        RecoveryPlanStep nextStep =
                new RecoveryPlanStep();

        nextStep.setRecoveryPlan(recoveryPlan);
        nextStep.setStepNumber(nextStepNumber);
        nextStep.setAction(nextAction);
        nextStep.setScheduledAt(scheduledAt);
        nextStep.setStatus("SCHEDULED");
        nextStep.setResult("PENDING");

        nextStep.setAiRecommendation(
                decision != null
                        ? decision.recommendation()
                        : null
        );

        nextStep.setAiConfidence(
                decision != null
                        ? decision.confidence()
                        : null
        );

        nextStep.setCreatedAt(now);
        nextStep.setUpdatedAt(now);

        recoveryPlanStepRepository.save(nextStep);

        recoveryPlan.setCurrentStep(nextStepNumber);
        recoveryPlan.setNextAction(nextAction);
        recoveryPlan.setNextActionAt(scheduledAt);
        recoveryPlan.setStatus("ACTIVE");
        recoveryPlan.setUpdatedAt(now);

        recordEvent(
                payment,
                recoveryPlan,
                "AI_RECOVERY_DECISION",
                nextAction,
                "SCHEDULED",
                "AI selected next recovery action: "
                        + nextAction
                        + " with confidence "
                        + (decision != null
                        ? decision.confidence()
                        : "N/A")
        );

        recordEvent(
                payment,
                recoveryPlan,
                "RECOVERY_STEP_SCHEDULED",
                nextAction,
                "SCHEDULED",
                "Next recovery step scheduled for "
                        + scheduledAt
        );
    }

    private String applyRecoveryGuardrails(
            Payment payment,
            AIRecoveryDecisionService.RecoveryDecision decision) {

        if (payment == null) {
            return "BLOCK_RECOVERY";
        }

        if (decision == null
                || decision.action() == null
                || decision.action().isBlank()) {

            return "BLOCK_RECOVERY";
        }

        String action =
                normalizeAction(
                        decision.action()
                );

        if (payment.getFailureReason() != null) {

            String reason =
                    payment.getFailureReason()
                            .name()
                            .toUpperCase();

            if (reason.contains("FRAUD")
                    || reason.contains("SECURITY")) {

                return "BLOCK_RECOVERY";
            }

            if (reason.contains("EXPIRED")
                    || reason.contains("INVALID_CARD")) {

                return "REQUEST_ALTERNATE_PAYMENT";
            }

            if (reason.contains("INSUFFICIENT")) {

                return "SEND_PAYMENT_REMINDER";
            }
        }

        int retryCount =
                payment.getRetryCount() == null
                        ? 0
                        : payment.getRetryCount();

        if ("AUTOMATIC_RETRY".equals(action)
                && retryCount >= MAX_RETRIES) {

            return "REQUEST_ALTERNATE_PAYMENT";
        }

        return switch (action) {

            case "AUTOMATIC_RETRY" ->
                    "AUTOMATIC_RETRY";

            case "SEND_PAYMENT_REMINDER" ->
                    "SEND_PAYMENT_REMINDER";

            case "REQUEST_ALTERNATE_PAYMENT" ->
                    "REQUEST_ALTERNATE_PAYMENT";

            case "BLOCK_RECOVERY" ->
                    "BLOCK_RECOVERY";

            default ->
                    "BLOCK_RECOVERY";
        };
    }


    private boolean shouldContinueRecovery(
            RecoveryPlan recoveryPlan,
            RecoveryPlanStep step) {

        if (recoveryPlan == null
                || step == null) {
            return false;
        }

        if ("WAITING_FOR_PAYMENT".equalsIgnoreCase(
                step.getStatus())) {
            return false;
        }

        int maxSteps =
                recoveryPlan.getMaxSteps() != null
                        ? Math.min(
                        recoveryPlan.getMaxSteps(),
                        MAX_RECOVERY_STEPS
                )
                        : MAX_RECOVERY_STEPS;

        if (step.getStepNumber() >= maxSteps) {
            return false;
        }

        if ("BLOCKED".equalsIgnoreCase(
                step.getStatus())) {
            return false;
        }

        if (recoveryPlan.getPayment() == null) {
            return false;
        }

        if (recoveryPlan.getPayment().getStatus()
                == PaymentStatus.RECOVERED) {
            return false;
        }

        return "COMPLETED".equalsIgnoreCase(
                step.getStatus()
        ) || "FAILED".equalsIgnoreCase(
                step.getStatus()
        );
    }

    private void finalizeSuccessfulRecovery(
            RecoveryPlan recoveryPlan,
            Payment payment,
            RecoveryPlanStep completedStep) {

        LocalDateTime now =
                LocalDateTime.now();

        payment.setStatus(
                PaymentStatus.RECOVERED
        );

        payment.setNextRetryAt(null);
        payment.setUpdatedAt(now);

        paymentRepository.save(payment);

        recoveryPlan.setStatus("COMPLETED");
        recoveryPlan.setCurrentStep(
                completedStep.getStepNumber()
        );
        recoveryPlan.setNextAction(null);
        recoveryPlan.setNextActionAt(null);
        recoveryPlan.setUpdatedAt(now);

        completedStep.setStatus("COMPLETED");

        if (completedStep.getResult() == null) {
            completedStep.setResult("SUCCESS");
        }

        completedStep.setExecutedAt(now);
        completedStep.setUpdatedAt(now);

        recoveryPlanStepRepository.save(completedStep);

        recordEvent(
                payment,
                recoveryPlan,
                "RECOVERY_PLAN_COMPLETED",
                completedStep.getAction(),
                "SUCCESS",
                "Recovery plan completed successfully. Payment was recovered."
        );
    }

    private void finalizeBlockedRecovery(
            RecoveryPlan recoveryPlan,
            Payment payment,
            RecoveryPlanStep completedStep) {

        LocalDateTime now =
                LocalDateTime.now();

        payment.setStatus(
                PaymentStatus.FAILED
        );

        payment.setUpdatedAt(now);

        paymentRepository.save(payment);

        recoveryPlan.setStatus("BLOCKED");
        recoveryPlan.setCurrentStep(
                completedStep.getStepNumber()
        );
        recoveryPlan.setNextAction(null);
        recoveryPlan.setNextActionAt(null);
        recoveryPlan.setUpdatedAt(now);

        recordEvent(
                payment,
                recoveryPlan,
                "RECOVERY_PLAN_BLOCKED",
                "BLOCK_RECOVERY",
                "BLOCKED",
                "Recovery plan was blocked by recovery safety rules."
        );
    }

    private void finalizeRecoveryPlan(
            RecoveryPlan recoveryPlan,
            Payment payment,
            RecoveryPlanStep completedStep) {

        LocalDateTime now =
                LocalDateTime.now();

        if (payment.getStatus()
                == PaymentStatus.RECOVERED) {

            finalizeSuccessfulRecovery(
                    recoveryPlan,
                    payment,
                    completedStep
            );

            return;
        }

        payment.setStatus(
                PaymentStatus.FAILED
        );

        payment.setUpdatedAt(now);

        paymentRepository.save(payment);

        recoveryPlan.setStatus("EXHAUSTED");
        recoveryPlan.setCurrentStep(
                completedStep.getStepNumber()
        );
        recoveryPlan.setNextAction(null);
        recoveryPlan.setNextActionAt(null);
        recoveryPlan.setUpdatedAt(now);

        recordEvent(
                payment,
                recoveryPlan,
                "RECOVERY_PLAN_EXHAUSTED",
                completedStep.getAction(),
                "FAILED",
                "All recovery steps were exhausted without recovering the payment."
        );
    }

    // ============================================================
    // EXECUTION TIME
    // ============================================================

    private LocalDateTime calculateNextExecutionTime(
            String action) {

        LocalDateTime now =
                LocalDateTime.now();

        return switch (action) {

            case "AUTOMATIC_RETRY" ->
                    now.plusHours(1);

            case "SEND_PAYMENT_REMINDER" ->
                    now.plusHours(24);

            case "REQUEST_ALTERNATE_PAYMENT" ->
                    now.plusHours(24);

            case "BLOCK_RECOVERY" ->
                    now;

            default ->
                    now.plusHours(24);
        };
    }

    // ============================================================
    // EVENT
    // ============================================================

    private void recordEvent(
            Payment payment,
            RecoveryPlan recoveryPlan,
            String eventType,
            String action,
            String status,
            String message) {

        recoveryEventService.recordEvent(
                payment,
                recoveryPlan,
                eventType, action,
                status, message, null);
    }
    private String normalizeAction(String action) {
        if (action == null || action.isBlank()) {
            return "BLOCK_RECOVERY";
        }

        return action.trim().toUpperCase();
    }
    private String normalizeStatus(String status) {
        if (status == null
                || status.isBlank()) {

            return "SCHEDULED";
        }

        return status.trim().toUpperCase();
    }
}