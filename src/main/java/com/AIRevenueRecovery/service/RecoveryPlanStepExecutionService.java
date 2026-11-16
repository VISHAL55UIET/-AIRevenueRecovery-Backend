package com.AIRevenueRecovery.service;

import com.AIRevenueRecovery.entity.Payment;
import com.AIRevenueRecovery.entity.PaymentStatus;
import com.AIRevenueRecovery.entity.RecoveryAttempt;
import com.AIRevenueRecovery.entity.RecoveryPlan;
import com.AIRevenueRecovery.entity.RecoveryPlanStep;
import com.AIRevenueRecovery.exception.RecoveryPlanNotFoundException;
import com.AIRevenueRecovery.policy.PolicyDecision;
import com.AIRevenueRecovery.policy.PolicyEngine;
import com.AIRevenueRecovery.repository.PaymentRepository;
import com.AIRevenueRecovery.repository.RecoveryAttemptRepository;
import com.AIRevenueRecovery.repository.RecoveryPlanStepRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

import static com.AIRevenueRecovery.policy.RecoveryActions.*;
import static com.AIRevenueRecovery.policy.RecoveryStatuses.*;

/**
 * Runs the action that is already stored on the step. It never asks the LLM
 * again; it only re-checks the stored action against PolicyEngine (retry count,
 * opt-out etc. may have changed since the step was planned).
 * All plan state changes go through RecoveryPlanService.
 */
@Service
public class RecoveryPlanStepExecutionService {

    private static final Logger log = LoggerFactory.getLogger(RecoveryPlanStepExecutionService.class);

    private static final int DEFAULT_MAX_STEPS = 3;
    private static final int MAX_EVENT_MESSAGE = 250;

    private final RecoveryPlanStepRepository recoveryPlanStepRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentGatewayService paymentGatewayService;
    private final EmailService emailService;
    private final RecoveryAttemptRepository recoveryAttemptRepository;
    private final RecoveryEventService recoveryEventService;
    private final RecoveryPlanService recoveryPlanService;
    private final PolicyEngine policyEngine;
    private final Clock clock;

    public RecoveryPlanStepExecutionService(
            RecoveryPlanStepRepository recoveryPlanStepRepository,
            PaymentRepository paymentRepository,
            PaymentGatewayService paymentGatewayService,
            EmailService emailService,
            RecoveryAttemptRepository recoveryAttemptRepository,
            RecoveryEventService recoveryEventService,
            RecoveryPlanService recoveryPlanService,
            PolicyEngine policyEngine,
            Clock clock) {

        this.recoveryPlanStepRepository = recoveryPlanStepRepository;
        this.paymentRepository = paymentRepository;
        this.paymentGatewayService = paymentGatewayService;
        this.emailService = emailService;
        this.recoveryAttemptRepository = recoveryAttemptRepository;
        this.recoveryEventService = recoveryEventService;
        this.recoveryPlanService = recoveryPlanService;
        this.policyEngine = policyEngine;
        this.clock = clock;
    }

    // ============================================================
    // EXECUTE STEP
    // ============================================================

    @Transactional
    public RecoveryPlanStep executeStep(Long stepId) {

        RecoveryPlanStep step = loadStep(stepId);

        String currentStatus = normalizeStatus(step.getStatus());

        if (STEP_COMPLETED.equals(currentStatus)
                || STEP_BLOCKED.equals(currentStatus)
                || STEP_WAITING_FOR_PAYMENT.equals(currentStatus)
                || STEP_ESCALATED.equals(currentStatus)
                || STEP_EXPIRED.equals(currentStatus)
                || STEP_CANCELLED.equals(currentStatus)) {
            return step;
        }

        if (!STEP_SCHEDULED.equals(currentStatus)
                && !STEP_PROCESSING.equals(currentStatus)
                && !STEP_FAILED.equals(currentStatus)) {
            throw new IllegalStateException(
                    "Recovery plan step cannot be executed from status: " + step.getStatus());
        }

        RecoveryPlan plan = step.getRecoveryPlan();

        if (plan == null) {
            throw new IllegalStateException("Recovery plan not found for step: " + stepId);
        }

        Payment payment = plan.getPayment();

        if (payment == null) {
            throw new IllegalStateException("Payment not found for recovery plan step: " + stepId);
        }

        // Customer paid before this step ran (payment link, webhook ...).
        if (payment.getStatus() == PaymentStatus.RECOVERED) {
            finalizeSuccessfulRecovery(plan, payment, step, "ALREADY_RECOVERED_BEFORE_EXECUTION");
            return step;
        }

        // Plan was blocked / completed / escalated meanwhile: this step must not run.
        if (!PLAN_ACTIVE.equalsIgnoreCase(plan.getStatus())) {
            step.setStatus(STEP_CANCELLED);
            step.setResult("PLAN_NOT_ACTIVE");
            step.setUpdatedAt(now());
            return recoveryPlanStepRepository.save(step);
        }

        String action = normalizeAction(step.getAction());
        LocalDateTime now = now();

        // Quiet hours are checked again at execution time, not only when planned.
        if (policyEngine.isCustomerFacing(action) && policyEngine.isQuietHour(now)) {

            LocalDateTime next = policyEngine.respectQuietHours(action, now);

            step.setStatus(STEP_SCHEDULED);
            step.setScheduledAt(next);
            step.setUpdatedAt(now);

            plan.setNextActionAt(next);
            plan.setUpdatedAt(now);

            recordEvent(payment, plan, "COMMUNICATION_DEFERRED", action, "SCHEDULED",
                    "Quiet hours. Step rescheduled for " + next);

            return recoveryPlanStepRepository.save(step);
        }

        step.setStatus(STEP_PROCESSING);
        step.setUpdatedAt(now);
        recoveryPlanStepRepository.save(step);

        recordEvent(payment, plan, "RECOVERY_STEP_STARTED", action, "PROCESSING",
                "Recovery step execution started.");

        PolicyDecision policy = policyEngine.evaluate(payment, action);

        try {

            runAction(action, policy, payment, plan, step);

        } catch (Exception exception) {

            log.error("Recovery step {} failed: {}", stepId, exception.toString(), exception);

            String message = exception.getMessage() != null
                    ? exception.getMessage()
                    : "Recovery step execution failed";

            step.setStatus(STEP_FAILED);
            step.setResult(abbreviate(message));

            recordEvent(payment, plan, "RECOVERY_STEP_FAILED", action, "FAILED", message);
        }

        return finishStep(plan, payment, step);
    }

    private void runAction(
            String action,
            PolicyDecision policy,
            Payment payment,
            RecoveryPlan plan,
            RecoveryPlanStep step) {

        if (policy.blocked()) {
            executeBlockRecovery(payment, plan, step, policy);
            return;
        }

        if (policy.escalated()) {
            executeEscalation(payment, plan, step, policy);
            return;
        }

        // Policy re-check changed the action: don't run something that was never recorded.
        if (!policy.action().equals(action)) {

            step.setStatus(STEP_FAILED);
            step.setPolicyRule(policy.rule());
            step.setResult("POLICY_REJECTED_ACTION");

            recordEvent(payment, plan, "RECOVERY_STEP_REJECTED", action, "FAILED",
                    "Policy re-check replaced " + action + " with " + policy.action()
                            + " (" + policy.rule() + "). A new step will be planned.");
            return;
        }

        switch (action) {
            case AUTOMATIC_RETRY -> executeAutomaticRetry(payment, plan, step);
            case SEND_PAYMENT_REMINDER -> executePaymentReminder(payment, plan, step);
            case REQUEST_ALTERNATE_PAYMENT -> executeAlternatePayment(payment, plan, step);
            default -> {
                step.setStatus(STEP_FAILED);
                step.setResult("UNKNOWN_ACTION");
            }
        }
    }

    // ============================================================
    // AFTER THE ACTION: move the plan forward
    // ============================================================

    private RecoveryPlanStep finishStep(
            RecoveryPlan plan,
            Payment payment,
            RecoveryPlanStep step) {

        LocalDateTime now = now();

        step.setExecutedAt(now);
        step.setUpdatedAt(now);
        payment.setUpdatedAt(now);

        paymentRepository.save(payment);
        RecoveryPlanStep saved = recoveryPlanStepRepository.save(step);

        String status = normalizeStatus(saved.getStatus());

        if (STEP_WAITING_FOR_PAYMENT.equals(status)) {
            return saved;   // customer has to complete checkout; reconciled by the scheduler
        }

        if (STEP_BLOCKED.equals(status)) {

            payment.setStatus(PaymentStatus.FAILED);
            paymentRepository.save(payment);

            recoveryPlanService.blockPlan(plan.getId());

            recordEvent(payment, plan, "RECOVERY_PLAN_BLOCKED", BLOCK_RECOVERY, "BLOCKED",
                    "Recovery plan was blocked by recovery policy.");

            return saved;
        }

        if (STEP_ESCALATED.equals(status)) {

            recoveryPlanService.escalatePlan(plan.getId());

            recordEvent(payment, plan, "RECOVERY_PLAN_ESCALATED", ESCALATE_TO_HUMAN, "ESCALATED",
                    "Recovery plan handed to a human.");

            return saved;
        }

        if (payment.getStatus() == PaymentStatus.RECOVERED) {
            finalizeSuccessfulRecovery(plan, payment, saved, "SUCCESS");
            return saved;
        }

        continueOrExhaust(plan, payment, saved);

        return saved;
    }

    private void continueOrExhaust(
            RecoveryPlan plan,
            Payment payment,
            RecoveryPlanStep step) {

        int maxSteps = plan.getMaxSteps() != null ? plan.getMaxSteps() : DEFAULT_MAX_STEPS;

        if (step.getStepNumber() < maxSteps) {
            recoveryPlanService.advancePlan(plan.getId());
            return;
        }

        payment.setStatus(PaymentStatus.FAILED);
        payment.setUpdatedAt(now());
        paymentRepository.save(payment);

        recoveryPlanService.exhaustPlan(plan.getId());

        recordEvent(payment, plan, "RECOVERY_PLAN_EXHAUSTED", step.getAction(), "FAILED",
                "All recovery steps were exhausted without recovering the payment.");
    }

    // ============================================================
    // WAITING_FOR_PAYMENT: complete if paid, expire if not
    // ============================================================

    @Transactional
    public RecoveryPlanStep reconcileWaitingStep(Long stepId, LocalDateTime expiryThreshold) {

        RecoveryPlanStep step = loadStep(stepId);

        if (!STEP_WAITING_FOR_PAYMENT.equals(normalizeStatus(step.getStatus()))) {
            return step;
        }

        RecoveryPlan plan = step.getRecoveryPlan();
        Payment payment = plan == null ? null : plan.getPayment();

        if (plan == null || payment == null) {
            throw new IllegalStateException("Plan or payment missing for waiting step: " + stepId);
        }

        if (payment.getStatus() == PaymentStatus.RECOVERED) {
            finalizeSuccessfulRecovery(plan, payment, step, "RECOVERED_VIA_CHECKOUT");
            return step;
        }

        if (!PLAN_ACTIVE.equalsIgnoreCase(plan.getStatus())) {
            step.setStatus(STEP_CANCELLED);
            step.setResult("PLAN_NOT_ACTIVE");
            step.setUpdatedAt(now());
            return recoveryPlanStepRepository.save(step);
        }

        LocalDateTime waitingSince = step.getUpdatedAt();

        if (waitingSince == null || waitingSince.isAfter(expiryThreshold)) {
            return step;   // still inside the payment window
        }

        int attemptNumber = payment.getRetryCount() == null ? 0 : payment.getRetryCount();

        recoveryAttemptRepository
                .findByPaymentIdAndAttemptNumber(payment.getId(), attemptNumber)
                .ifPresent(attempt -> {
                    if ("PENDING_CHECKOUT".equals(attempt.getResult())) {
                        attempt.setResult("EXPIRED");
                        recoveryAttemptRepository.save(attempt);
                    }
                });

        LocalDateTime now = now();

        step.setStatus(STEP_EXPIRED);
        step.setResult("CHECKOUT_EXPIRED");
        step.setUpdatedAt(now);

        if (payment.getStatus() == PaymentStatus.RETRYING) {
            payment.setStatus(PaymentStatus.FAILED);
        }

        payment.setUpdatedAt(now);
        paymentRepository.save(payment);

        RecoveryPlanStep saved = recoveryPlanStepRepository.save(step);

        recordEvent(payment, plan, "PAYMENT_CHECKOUT_EXPIRED", saved.getAction(), "EXPIRED",
                "Customer did not complete checkout in time.");

        continueOrExhaust(plan, payment, saved);

        return saved;
    }

    // ============================================================
    // ACTIONS
    // ============================================================

    private void executeAutomaticRetry(
            Payment payment,
            RecoveryPlan plan,
            RecoveryPlanStep step) {

        int attemptNumber = payment.getRetryCount() == null ? 1 : payment.getRetryCount() + 1;

        RecoveryAttempt attempt = recoveryAttemptRepository
                .findByPaymentIdAndAttemptNumber(payment.getId(), attemptNumber)
                .orElseGet(() -> {

                    RecoveryAttempt newAttempt = new RecoveryAttempt();

                    newAttempt.setPayment(payment);
                    newAttempt.setFailureReason(payment.getFailureReason());
                    newAttempt.setAttemptNumber(attemptNumber);
                    newAttempt.setAction(AUTOMATIC_RETRY);
                    newAttempt.setAiRecommendation(step.getAiRecommendation());
                    newAttempt.setAiConfidence(step.getAiConfidence());
                    newAttempt.setAttemptedAt(now());

                    return newAttempt;
                });

        payment.setRetryCount(attemptNumber);
        payment.setStatus(PaymentStatus.RETRYING);

        recoveryAttemptRepository.save(attempt);

        recordEvent(payment, plan, "PAYMENT_RECOVERY_ATTEMPT", AUTOMATIC_RETRY, "STARTED",
                "Recovery payment flow initiated. Attempt " + attemptNumber);

        boolean successful = paymentGatewayService.processPayment(payment, attemptNumber);

        if (successful) {

            attempt.setResult("SUCCESS");
            payment.setStatus(PaymentStatus.RECOVERED);

            step.setStatus(STEP_COMPLETED);
            step.setResult("SUCCESS");

            recoveryAttemptRepository.save(attempt);

            recordEvent(payment, plan, "PAYMENT_RECOVERED", AUTOMATIC_RETRY, "SUCCESS",
                    "Payment successfully recovered.");

            emailService.sendRecoverySuccessEmail(payment);

            recordEvent(payment, plan, "COMMUNICATION_SENT", "RECOVERY_SUCCESS", "SUCCESS",
                    "Recovery success email sent successfully.");

        } else {

            // The order may exist while the customer has not completed checkout,
            // so the attempt is NOT marked failed.
            attempt.setResult("PENDING_CHECKOUT");

            step.setStatus(STEP_WAITING_FOR_PAYMENT);
            step.setResult("RAZORPAY_CHECKOUT_REQUIRED");

            recoveryAttemptRepository.save(attempt);

            recordEvent(payment, plan, "PAYMENT_CHECKOUT_REQUIRED", AUTOMATIC_RETRY, "PENDING",
                    "Razorpay order created. Customer payment is required.");
        }
    }

    private void executePaymentReminder(
            Payment payment,
            RecoveryPlan plan,
            RecoveryPlanStep step) {

        if (payment.isReminderSent()) {

            step.setStatus(STEP_COMPLETED);
            step.setResult("REMINDER_ALREADY_SENT");

            recordEvent(payment, plan, "COMMUNICATION_SKIPPED", "PAYMENT_REMINDER", "SKIPPED",
                    "Payment reminder was already sent.");

            return;
        }

        emailService.sendPaymentReminderEmail(payment, step.getAiRecommendation());

        payment.setReminderSent(true);
        payment.setReminderSentAt(now());

        step.setStatus(STEP_COMPLETED);
        step.setResult("EMAIL_SENT");

        recordEvent(payment, plan, "COMMUNICATION_SENT", "PAYMENT_REMINDER", "SUCCESS",
                "Payment reminder email sent successfully.");
    }

    private void executeAlternatePayment(
            Payment payment,
            RecoveryPlan plan,
            RecoveryPlanStep step) {

        emailService.sendAlternatePaymentEmail(payment, step.getAiRecommendation());

        step.setStatus(STEP_COMPLETED);
        step.setResult("ALTERNATE_PAYMENT_REQUESTED");

        recordEvent(payment, plan, "COMMUNICATION_SENT", "ALTERNATE_PAYMENT", "SUCCESS",
                "Alternate payment request email sent successfully.");
    }

    private void executeBlockRecovery(
            Payment payment,
            RecoveryPlan plan,
            RecoveryPlanStep step,
            PolicyDecision policy) {

        step.setStatus(STEP_BLOCKED);
        step.setResult("RECOVERY_BLOCKED");
        step.setPolicyRule(policy.rule());

        recordEvent(payment, plan, "RECOVERY_BLOCKED", BLOCK_RECOVERY, "BLOCKED",
                "Recovery blocked (" + policy.rule() + "). " + policy.reason());
    }

    private void executeEscalation(
            Payment payment,
            RecoveryPlan plan,
            RecoveryPlanStep step,
            PolicyDecision policy) {

        step.setStatus(STEP_ESCALATED);
        step.setResult("AWAITING_HUMAN_REVIEW");
        step.setPolicyRule(policy.rule());

        recordEvent(payment, plan, "RECOVERY_ESCALATED", ESCALATE_TO_HUMAN, "ESCALATED",
                "Escalated to a human (" + policy.rule() + "). " + policy.reason());
    }

    // ============================================================
    // SUCCESS
    // ============================================================

    private void finalizeSuccessfulRecovery(
            RecoveryPlan plan,
            Payment payment,
            RecoveryPlanStep step,
            String result) {

        LocalDateTime now = now();

        payment.setStatus(PaymentStatus.RECOVERED);
        payment.setNextRetryAt(null);
        payment.setUpdatedAt(now);

        paymentRepository.save(payment);

        recoveryPlanService.completePlan(plan.getId());

        step.setStatus(STEP_COMPLETED);

        // Keep "SUCCESS" if the step itself recovered the payment; otherwise say how it was recovered.
        if (!"SUCCESS".equals(step.getResult())) {
            step.setResult(result);
        }

        if (step.getExecutedAt() == null) {
            step.setExecutedAt(now);
        }

        step.setUpdatedAt(now);

        recoveryPlanStepRepository.save(step);

        recordEvent(payment, plan, "RECOVERY_PLAN_COMPLETED", step.getAction(), "SUCCESS",
                "Recovery plan completed. Payment was recovered (" + result + ").");
    }

    // ============================================================
    // HELPERS
    // ============================================================

    private RecoveryPlanStep loadStep(Long stepId) {
        return recoveryPlanStepRepository.findById(stepId)
                .orElseThrow(() -> new RecoveryPlanNotFoundException(
                        "Recovery plan step not found with ID: " + stepId));
    }

    private void recordEvent(
            Payment payment,
            RecoveryPlan plan,
            String eventType,
            String action,
            String status,
            String message) {

        recoveryEventService.recordEvent(
                payment, plan, eventType, action, status, abbreviate(message), null);
    }

    private String abbreviate(String text) {

        if (text == null || text.length() <= MAX_EVENT_MESSAGE) {
            return text;
        }

        return text.substring(0, MAX_EVENT_MESSAGE);
    }

    private String normalizeAction(String action) {
        return action == null || action.isBlank() ? BLOCK_RECOVERY : action.trim().toUpperCase();
    }

    private String normalizeStatus(String status) {
        return status == null || status.isBlank() ? STEP_SCHEDULED : status.trim().toUpperCase();
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}