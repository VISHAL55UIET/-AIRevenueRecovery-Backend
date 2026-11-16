package com.AIRevenueRecovery.service;

import com.AIRevenueRecovery.entity.Payment;
import com.AIRevenueRecovery.entity.PaymentStatus;
import com.AIRevenueRecovery.entity.RecoveryPlan;
import com.AIRevenueRecovery.entity.RecoveryPlanStep;
import com.AIRevenueRecovery.exception.PaymentNotFoundException;
import com.AIRevenueRecovery.exception.RecoveryPlanNotFoundException;
import com.AIRevenueRecovery.policy.PolicyDecision;
import com.AIRevenueRecovery.policy.PolicyEngine;
import com.AIRevenueRecovery.repository.PaymentRepository;
import com.AIRevenueRecovery.repository.RecoveryPlanRepository;
import com.AIRevenueRecovery.repository.RecoveryPlanStepRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import static com.AIRevenueRecovery.policy.RecoveryActions.*;
import static com.AIRevenueRecovery.policy.RecoveryStatuses.*;

/**
 * Owns the plan state machine and is the ONLY place that decides the next step.
 * Flow for every step: AI proposes -> PolicyEngine decides -> step stores both.
 */
@Service
public class RecoveryPlanService {

    private static final Logger log = LoggerFactory.getLogger(RecoveryPlanService.class);

    private static final int DEFAULT_MAX_STEPS = 3;
    private static final int MAX_EVENT_MESSAGE = 250;

    private final RecoveryPlanRepository recoveryPlanRepository;
    private final RecoveryPlanStepRepository recoveryPlanStepRepository;
    private final PaymentRepository paymentRepository;
    private final AIRecoveryDecisionService aiRecoveryDecisionService;
    private final PolicyEngine policyEngine;
    private final RecoveryEventService recoveryEventService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public RecoveryPlanService(
            RecoveryPlanRepository recoveryPlanRepository,
            RecoveryPlanStepRepository recoveryPlanStepRepository,
            PaymentRepository paymentRepository,
            AIRecoveryDecisionService aiRecoveryDecisionService,
            PolicyEngine policyEngine,
            RecoveryEventService recoveryEventService,
            ObjectMapper objectMapper,
            Clock clock) {
        this.recoveryPlanRepository = recoveryPlanRepository;
        this.recoveryPlanStepRepository = recoveryPlanStepRepository;
        this.paymentRepository = paymentRepository;
        this.aiRecoveryDecisionService = aiRecoveryDecisionService;
        this.policyEngine = policyEngine;
        this.recoveryEventService = recoveryEventService;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    // ============================================================
    // CREATE PLAN
    // ============================================================

    @Transactional
    public RecoveryPlan createPlan(Long paymentId) {

        // Row lock: concurrent webhook / Kafka deliveries for the same payment
        // cannot both pass the "no existing plan" check below.
        Payment payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException(
                        "Payment not found with ID: " + paymentId));

        if (payment.getStatus() != PaymentStatus.FAILED) {
            throw new IllegalStateException(
                    "Recovery plan can only be created for failed payments");
        }

        RecoveryPlan existingPlan = recoveryPlanRepository
                .findByPaymentId(paymentId)
                .orElse(null);

        if (existingPlan != null) {
            return existingPlan;
        }

        AIRecoveryDecisionService.RecoveryDecision decision =
                aiRecoveryDecisionService.decide(payment);

        PolicyDecision policy = policyEngine.evaluate(
                payment, decision == null ? null : decision.action());

        LocalDateTime now = now();
        LocalDateTime runAt = policyEngine.respectQuietHours(policy.action(), now);

        RecoveryPlan plan = new RecoveryPlan();
        plan.setPayment(payment);
        plan.setStrategy(policy.action());
        plan.setCurrentStep(1);
        plan.setMaxSteps(DEFAULT_MAX_STEPS);
        plan.setStatus(planStatusFor(policy));
        plan.setNextAction(policy.stops() ? null : policy.action());
        plan.setNextActionAt(policy.stops() ? null : runAt);
        plan.setCreatedAt(now);
        plan.setUpdatedAt(now);

        RecoveryPlan savedPlan = recoveryPlanRepository.save(plan);

        RecoveryPlanStep firstStep = createStep(savedPlan, 1, policy, runAt, decision);
        recoveryPlanStepRepository.save(firstStep);

        recordDecisionEvents(payment, savedPlan, 1, policy, decision, runAt);

        return savedPlan;
    }

    // ============================================================
    // EVALUATE CURRENT STEP AND MOVE THE PLAN FORWARD
    // (name kept as executePlan so existing callers keep compiling)
    // ============================================================

    @Transactional
    public RecoveryPlan executePlan(Long planId) {

        RecoveryPlan plan = getPlanById(planId);

        if (!PLAN_ACTIVE.equalsIgnoreCase(plan.getStatus())) {
            throw new IllegalStateException("Recovery plan is not active");
        }

        // Customer already paid (e.g. via payment link): done, whatever the step state.
        Payment payment = plan.getPayment();
        if (payment != null && payment.getStatus() == PaymentStatus.RECOVERED) {
            return completePlan(planId);
        }

        RecoveryPlanStep currentStep = recoveryPlanStepRepository
                .findByRecoveryPlanIdAndStepNumber(planId, plan.getCurrentStep())
                .orElseThrow(() -> new RecoveryPlanNotFoundException(
                        "Recovery plan step not found"));

        String stepStatus = currentStep.getStatus();

        if (!STEP_COMPLETED.equalsIgnoreCase(stepStatus)
                && !STEP_FAILED.equalsIgnoreCase(stepStatus)
                && !STEP_EXPIRED.equalsIgnoreCase(stepStatus)
                && !STEP_BLOCKED.equalsIgnoreCase(stepStatus)) {
            throw new IllegalStateException("Current recovery step has not been executed");
        }

        if (STEP_BLOCKED.equalsIgnoreCase(stepStatus)) {
            return blockPlan(planId);
        }

        if (currentStep.getStepNumber() >= plan.getMaxSteps()) {
            return exhaustPlan(planId);
        }

        return advancePlan(planId);
    }

    // ============================================================
    // ADVANCE PLAN: decide and schedule the next step
    // ============================================================

    @Transactional
    public RecoveryPlan advancePlan(Long planId) {

        RecoveryPlan plan = getPlanById(planId);

        if (!PLAN_ACTIVE.equalsIgnoreCase(plan.getStatus())) {
            throw new IllegalStateException("Recovery plan is not active");
        }

        Payment payment = plan.getPayment();

        if (payment == null) {
            throw new IllegalStateException("Payment is missing from recovery plan");
        }

        if (payment.getStatus() == PaymentStatus.RECOVERED) {
            return completePlan(planId);
        }

        int nextStepNumber = plan.getCurrentStep() + 1;

        if (nextStepNumber > plan.getMaxSteps()) {
            return exhaustPlan(planId);
        }

        // Prevent duplicate step creation.
        RecoveryPlanStep nextStep = recoveryPlanStepRepository
                .findByRecoveryPlanIdAndStepNumber(planId, nextStepNumber)
                .orElse(null);

        if (nextStep == null) {

            // Ask AI again: payment state may have changed after the last attempt.
            AIRecoveryDecisionService.RecoveryDecision decision =
                    aiRecoveryDecisionService.decide(payment);

            PolicyDecision policy = nextPolicy(plan.getNextAction(), decision, payment);

            LocalDateTime scheduledAt = policyEngine.respectQuietHours(
                    policy.action(),
                    calculateNextExecutionTime(policy.action()));

            nextStep = createStep(plan, nextStepNumber, policy, scheduledAt, decision);
            recoveryPlanStepRepository.save(nextStep);

            recordDecisionEvents(payment, plan, nextStepNumber, policy, decision, scheduledAt);

            if (policy.stops()) {
                plan.setCurrentStep(nextStepNumber);
                plan.setStrategy(policy.action());
                return closePlan(plan, planStatusFor(policy));
            }
        }

        plan.setCurrentStep(nextStepNumber);
        plan.setStrategy(nextStep.getAction());
        plan.setNextAction(nextStep.getAction());
        plan.setNextActionAt(nextStep.getScheduledAt());
        plan.setStatus(PLAN_ACTIVE);
        plan.setUpdatedAt(now());

        return recoveryPlanRepository.save(plan);
    }

    // ============================================================
    // NEXT ACTION
    // ============================================================

    /**
     * Policy always wins. Progression is Reminder -> Retry -> Alternate payment,
     * and every candidate is itself checked by the policy engine.
     */
    private PolicyDecision nextPolicy(
            String currentAction,
            AIRecoveryDecisionService.RecoveryDecision decision,
            Payment payment) {

        PolicyDecision base = policyEngine.evaluate(
                payment, decision == null ? null : decision.action());

        if (base.stops()) {
            return base;
        }

        if (SEND_PAYMENT_REMINDER.equalsIgnoreCase(currentAction)) {

            PolicyDecision retry = policyEngine.evaluate(payment, AUTOMATIC_RETRY);

            if (AUTOMATIC_RETRY.equals(retry.action())) {
                return progression(currentAction, retry);
            }

            return progression(currentAction,
                    policyEngine.evaluate(payment, REQUEST_ALTERNATE_PAYMENT));
        }

        if (AUTOMATIC_RETRY.equalsIgnoreCase(currentAction)) {
            return progression(currentAction,
                    policyEngine.evaluate(payment, REQUEST_ALTERNATE_PAYMENT));
        }

        return base;
    }

    private PolicyDecision progression(String currentAction, PolicyDecision decision) {

        if (decision.stops()) {
            return decision;
        }

        return new PolicyDecision(
                decision.action(),
                "PROGRESSION_" + currentAction + "_TO_" + decision.action(),
                decision.reason());
    }

    private LocalDateTime calculateNextExecutionTime(String action) {

        LocalDateTime now = now();

        if (AUTOMATIC_RETRY.equalsIgnoreCase(action)) {
            return now.plusHours(1);
        }

        // Blocked / escalated steps are never actually executed.
        if (BLOCK_RECOVERY.equalsIgnoreCase(action)
                || ESCALATE_TO_HUMAN.equalsIgnoreCase(action)) {
            return now;
        }

        return now.plusHours(24);
    }

    // ============================================================
    // CREATE STEP
    // ============================================================

    private RecoveryPlanStep createStep(
            RecoveryPlan plan,
            int stepNumber,
            PolicyDecision policy,
            LocalDateTime scheduledAt,
            AIRecoveryDecisionService.RecoveryDecision decision) {

        LocalDateTime now = now();

        RecoveryPlanStep step = new RecoveryPlanStep();
        step.setRecoveryPlan(plan);
        step.setStepNumber(stepNumber);
        step.setAction(policy.action());
        step.setScheduledAt(scheduledAt);

        if (policy.blocked()) {
            step.setStatus(STEP_BLOCKED);
            step.setResult("BLOCKED_BY_POLICY");
        } else if (policy.escalated()) {
            step.setStatus(STEP_ESCALATED);
            step.setResult("AWAITING_HUMAN_REVIEW");
        } else {
            step.setStatus(STEP_SCHEDULED);
            step.setResult("PENDING");
        }

        // Audit: which rule decided, and what the AI originally proposed.
        step.setPolicyRule(policy.rule());
        step.setAiProposedAction(decision == null ? null : decision.action());
        step.setAiReasoningJson(toJson(decision == null ? null : decision.reasoning()));

        if (decision != null) {
            step.setAiRecommendation(decision.recommendation());
            step.setAiConfidence(decision.confidence());
        }

        step.setCreatedAt(now);
        step.setUpdatedAt(now);

        return step;
    }

    private String planStatusFor(PolicyDecision policy) {

        if (policy.blocked()) {
            return PLAN_BLOCKED;
        }

        if (policy.escalated()) {
            return PLAN_ESCALATED;
        }

        return PLAN_ACTIVE;
    }

    // ============================================================
    // EVENTS
    // ============================================================

    private void recordDecisionEvents(
            Payment payment,
            RecoveryPlan plan,
            int stepNumber,
            PolicyDecision policy,
            AIRecoveryDecisionService.RecoveryDecision decision,
            LocalDateTime scheduledAt) {

        String proposed = decision == null ? null : decision.action();

        String message = "Step " + stepNumber
                + ": AI proposed " + proposed
                + ", policy chose " + policy.action()
                + " (rule " + policy.rule() + "). " + policy.reason();

        if (policy.blocked()) {
            recordEvent(payment, plan, "RECOVERY_PLAN_BLOCKED", policy.action(), "BLOCKED", message);
            return;
        }

        if (policy.escalated()) {
            recordEvent(payment, plan, "RECOVERY_PLAN_ESCALATED", policy.action(), "ESCALATED", message);
            return;
        }

        recordEvent(payment, plan, "AI_RECOVERY_DECISION", policy.action(), "SCHEDULED", message);

        recordEvent(payment, plan, "RECOVERY_STEP_SCHEDULED", policy.action(), "SCHEDULED",
                "Recovery step " + stepNumber + " scheduled for " + scheduledAt);
    }

    private void recordEvent(
            Payment payment,
            RecoveryPlan plan,
            String eventType,
            String action,
            String status,
            String message) {

        String safeMessage = message != null && message.length() > MAX_EVENT_MESSAGE
                ? message.substring(0, MAX_EVENT_MESSAGE)
                : message;

        recoveryEventService.recordEvent(
                payment, plan, eventType, action, status, safeMessage, null);
    }

    private String toJson(Object value) {

        if (value == null) {
            return null;
        }

        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            log.warn("Could not serialize AI reasoning: {}", exception.toString());
            return null;
        }
    }

    // ============================================================
    // QUERIES
    // ============================================================

    public RecoveryPlan getPlanById(Long planId) {
        return recoveryPlanRepository
                .findById(planId)
                .orElseThrow(() -> new RecoveryPlanNotFoundException(
                        "Recovery plan not found with ID: " + planId));
    }

    public RecoveryPlan getPlanByPaymentId(Long paymentId) {
        return recoveryPlanRepository
                .findByPaymentId(paymentId)
                .orElseThrow(() -> new RecoveryPlanNotFoundException(
                        "Recovery plan not found for payment ID: " + paymentId));
    }

    public List<RecoveryPlan> getAllPlans() {
        return recoveryPlanRepository.findAll();
    }

    public List<RecoveryPlan> getActivePlans() {
        return recoveryPlanRepository.findByStatus(PLAN_ACTIVE);
    }

    // ============================================================
    // STATE TRANSITIONS
    // ============================================================

    @Transactional
    public RecoveryPlan updateNextAction(
            Long planId,
            String nextAction,
            LocalDateTime nextActionAt) {

        RecoveryPlan plan = getPlanById(planId);

        plan.setNextAction(nextAction);
        plan.setNextActionAt(nextActionAt);
        plan.setUpdatedAt(now());

        return recoveryPlanRepository.save(plan);
    }

    @Transactional
    public RecoveryPlan completePlan(Long planId) {

        RecoveryPlan plan = getPlanById(planId);

        Payment payment = plan.getPayment();

        if (payment == null || payment.getStatus() != PaymentStatus.RECOVERED) {
            throw new IllegalStateException(
                    "Recovery plan cannot be completed because payment is not recovered");
        }

        return closePlan(plan, PLAN_COMPLETED);
    }

    @Transactional
    public RecoveryPlan exhaustPlan(Long planId) {
        return closePlan(getPlanById(planId), PLAN_EXHAUSTED);
    }

    @Transactional
    public RecoveryPlan blockPlan(Long planId) {
        return closePlan(getPlanById(planId), PLAN_BLOCKED);
    }

    @Transactional
    public RecoveryPlan escalatePlan(Long planId) {
        return closePlan(getPlanById(planId), PLAN_ESCALATED);
    }

    @Transactional
    public RecoveryPlan cancelPlan(Long planId) {
        return closePlan(getPlanById(planId), PLAN_CANCELLED);
    }

    private RecoveryPlan closePlan(RecoveryPlan plan, String status) {

        plan.setStatus(status);
        plan.setNextAction(null);
        plan.setNextActionAt(null);
        plan.setUpdatedAt(now());

        return recoveryPlanRepository.save(plan);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}