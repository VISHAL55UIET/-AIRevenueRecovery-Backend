package com.AIRevenueRecovery.service;

import com.AIRevenueRecovery.entity.Payment;
import com.AIRevenueRecovery.entity.PaymentStatus;
import com.AIRevenueRecovery.entity.RecoveryPlan;
import com.AIRevenueRecovery.entity.RecoveryPlanStep;
import com.AIRevenueRecovery.repository.PaymentRepository;
import com.AIRevenueRecovery.repository.RecoveryPlanRepository;
import com.AIRevenueRecovery.repository.RecoveryPlanStepRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class RecoveryPlanService {

    private static final int DEFAULT_MAX_STEPS = 3;
    private static final int MAX_RETRY_COUNT = 3;

    private final RecoveryPlanRepository recoveryPlanRepository;
    private final RecoveryPlanStepRepository recoveryPlanStepRepository;
    private final PaymentRepository paymentRepository;
    private final AIRecoveryDecisionService aiRecoveryDecisionService;

    public RecoveryPlanService(
            RecoveryPlanRepository recoveryPlanRepository,
            RecoveryPlanStepRepository recoveryPlanStepRepository,
            PaymentRepository paymentRepository,
            AIRecoveryDecisionService aiRecoveryDecisionService) {

        this.recoveryPlanRepository = recoveryPlanRepository;
        this.recoveryPlanStepRepository = recoveryPlanStepRepository;
        this.paymentRepository = paymentRepository;
        this.aiRecoveryDecisionService = aiRecoveryDecisionService;
    }

    // ============================================================
    // CREATE RECOVERY PLAN
    // ============================================================

    @Transactional
    public RecoveryPlan createPlan(Long paymentId) {

        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() ->
                        new RuntimeException(
                                "Payment not found with ID: " + paymentId
                        )
                );

        /*
         * Recovery is only meaningful for failed payments.
         */
        if (payment.getStatus() != PaymentStatus.FAILED) {
            throw new RuntimeException(
                    "Recovery plan can only be created for failed payments"
            );
        }

        /*
         * Do not create duplicate recovery plans.
         */
        RecoveryPlan existingPlan =
                recoveryPlanRepository
                        .findByPaymentId(paymentId)
                        .orElse(null);

        if (existingPlan != null) {
            return existingPlan;
        }

        /*
         * Ask AI for the first recovery decision.
         */
        AIRecoveryDecisionService.RecoveryDecision decision =
                aiRecoveryDecisionService.decide(payment);

        String action = applySafetyRules(
                payment,
                decision
        );

        LocalDateTime now = LocalDateTime.now();

        RecoveryPlan recoveryPlan = new RecoveryPlan();

        recoveryPlan.setPayment(payment);
        recoveryPlan.setStrategy(action);
        recoveryPlan.setCurrentStep(1);
        recoveryPlan.setMaxSteps(DEFAULT_MAX_STEPS);
        recoveryPlan.setStatus("ACTIVE");
        recoveryPlan.setNextAction(action);
        recoveryPlan.setNextActionAt(now);
        recoveryPlan.setCreatedAt(now);
        recoveryPlan.setUpdatedAt(now);

        RecoveryPlan savedPlan =
                recoveryPlanRepository.save(recoveryPlan);

        /*
         * Create first recovery step.
         */
        RecoveryPlanStep firstStep =
                createStep(
                        savedPlan,
                        1,
                        action,
                        now,
                        decision
                );

        recoveryPlanStepRepository.save(firstStep);

        return savedPlan;
    }

    // ============================================================
    // EXECUTE PLAN
    // ============================================================

    @Transactional
    public RecoveryPlan executePlan(Long planId) {

        RecoveryPlan recoveryPlan =
                getPlanById(planId);

        if (!"ACTIVE".equalsIgnoreCase(
                recoveryPlan.getStatus())) {

            throw new RuntimeException(
                    "Recovery plan is not active"
            );
        }

        RecoveryPlanStep currentStep =
                recoveryPlanStepRepository
                        .findByRecoveryPlanIdAndStepNumber(
                                planId,
                                recoveryPlan.getCurrentStep()
                        )
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Recovery plan step not found"
                                )
                        );

        /*
         * A plan can advance only after the current
         * recovery step has been executed.
         */
        if (!"COMPLETED".equalsIgnoreCase(
                currentStep.getStatus())
                && !"FAILED".equalsIgnoreCase(
                currentStep.getStatus())
                && !"BLOCKED".equalsIgnoreCase(
                currentStep.getStatus())) {

            throw new RuntimeException(
                    "Current recovery step has not been executed"
            );
        }

        Payment payment =
                recoveryPlan.getPayment();

        if (payment != null
                && payment.getStatus()
                == PaymentStatus.RECOVERED) {

            return completePlan(planId);
        }

        /*
         * If recovery was blocked, stop the plan.
         */
        if ("BLOCKED".equalsIgnoreCase(
                currentStep.getStatus())) {

            return cancelPlan(planId);
        }

        /*
         * Maximum steps reached.
         */
        if (currentStep.getStepNumber()
                >= recoveryPlan.getMaxSteps()) {

            return exhaustPlan(planId);
        }

        return advancePlan(planId);
    }

    // ============================================================
    // ADVANCE PLAN
    // ============================================================

    @Transactional
    public RecoveryPlan advancePlan(Long planId) {

        RecoveryPlan recoveryPlan =
                getPlanById(planId);

        if (!"ACTIVE".equalsIgnoreCase(
                recoveryPlan.getStatus())) {

            throw new RuntimeException(
                    "Recovery plan is not active"
            );
        }

        Payment payment =
                recoveryPlan.getPayment();

        if (payment == null) {
            throw new RuntimeException(
                    "Payment is missing from recovery plan"
            );
        }

        /*
         * If payment is already recovered,
         * no further step should be created.
         */
        if (payment.getStatus()
                == PaymentStatus.RECOVERED) {

            return completePlan(planId);
        }

        int nextStepNumber =
                recoveryPlan.getCurrentStep() + 1;

        if (nextStepNumber >
                recoveryPlan.getMaxSteps()) {

            return exhaustPlan(planId);
        }

        /*
         * Prevent duplicate step creation.
         */
        RecoveryPlanStep nextStep =
                recoveryPlanStepRepository
                        .findByRecoveryPlanIdAndStepNumber(
                                planId,
                                nextStepNumber
                        )
                        .orElse(null);

        if (nextStep == null) {

            /*
             * Ask AI again because the payment state
             * may have changed after the previous attempt.
             */
            AIRecoveryDecisionService.RecoveryDecision decision =
                    aiRecoveryDecisionService.decide(
                            payment
                    );

            String nextAction =
                    getNextAction(
                            recoveryPlan.getNextAction(),
                            decision,
                            payment
                    );

            LocalDateTime scheduledAt =
                    calculateNextExecutionTime(
                            nextAction
                    );

            nextStep =
                    createStep(
                            recoveryPlan,
                            nextStepNumber,
                            nextAction,
                            scheduledAt,
                            decision
                    );

            recoveryPlanStepRepository.save(
                    nextStep
            );
        }

        recoveryPlan.setCurrentStep(
                nextStepNumber
        );

        recoveryPlan.setStrategy(
                nextStep.getAction()
        );

        recoveryPlan.setNextAction(
                nextStep.getAction()
        );

        recoveryPlan.setNextActionAt(
                nextStep.getScheduledAt()
        );

        recoveryPlan.setStatus(
                "ACTIVE"
        );

        recoveryPlan.setUpdatedAt(
                LocalDateTime.now()
        );

        return recoveryPlanRepository.save(
                recoveryPlan
        );
    }

    // ============================================================
    // CREATE STEP
    // ============================================================

    private RecoveryPlanStep createStep(
            RecoveryPlan recoveryPlan,
            int stepNumber,
            String action,
            LocalDateTime scheduledAt,
            AIRecoveryDecisionService.RecoveryDecision decision) {

        LocalDateTime now =
                LocalDateTime.now();

        RecoveryPlanStep step =
                new RecoveryPlanStep();

        step.setRecoveryPlan(
                recoveryPlan
        );

        step.setStepNumber(
                stepNumber
        );

        step.setAction(
                action
        );

        step.setScheduledAt(
                scheduledAt
        );

        step.setStatus(
                "SCHEDULED"
        );

        step.setResult(
                "PENDING"
        );

        if (decision != null) {

            step.setAiRecommendation(
                    decision.recommendation()
            );

            step.setAiConfidence(
                    decision.confidence()
            );
        }

        step.setCreatedAt(
                now
        );

        step.setUpdatedAt(
                now
        );

        return step;
    }

    // ============================================================
    // AI SAFETY RULES
    // ============================================================

    private String applySafetyRules(
            Payment payment,
            AIRecoveryDecisionService.RecoveryDecision decision) {

        if (payment == null) {
            return "BLOCK_RECOVERY";
        }

        /*
         * If AI failed to return a decision,
         * fail safely.
         */
        if (decision == null
                || decision.action() == null
                || decision.action().isBlank()) {

            return "BLOCK_RECOVERY";
        }

        /*
         * Fraud/security failures should never
         * automatically retry.
         */
        if (payment.getFailureReason() != null) {

            String reason =
                    payment.getFailureReason()
                            .name()
                            .toUpperCase();

            if (reason.contains("FRAUD")
                    || reason.contains("SECURITY")) {

                return "BLOCK_RECOVERY";
            }

            /*
             * Expired card / unusable card should
             * request another payment method.
             */
            if (reason.contains("EXPIRED")
                    || reason.contains("CARD_EXPIRED")
                    || reason.contains("INVALID_CARD")) {

                return "REQUEST_ALTERNATE_PAYMENT";
            }

            /*
             * Insufficient funds should generally
             * notify the customer instead of retrying.
             */
            if (reason.contains("INSUFFICIENT")
                    || reason.contains("INSUFFICIENT_FUNDS")) {

                return "SEND_PAYMENT_REMINDER";
            }
        }

        /*
         * Never allow unlimited retries.
         */
        int retryCount =
                payment.getRetryCount() == null
                        ? 0
                        : payment.getRetryCount();

        String action =
                decision.action()
                        .trim()
                        .toUpperCase();

        if ("AUTOMATIC_RETRY".equals(action)
                && retryCount >= MAX_RETRY_COUNT) {

            return "REQUEST_ALTERNATE_PAYMENT";
        }

        /*
         * Only allow known actions.
         */
        return switch (action) {

            case "AUTOMATIC_RETRY" ->
                    "AUTOMATIC_RETRY";

            case "REQUEST_ALTERNATE_PAYMENT" ->
                    "REQUEST_ALTERNATE_PAYMENT";

            case "SEND_PAYMENT_REMINDER" ->
                    "SEND_PAYMENT_REMINDER";

            case "BLOCK_RECOVERY" ->
                    "BLOCK_RECOVERY";

            default ->
                    "BLOCK_RECOVERY";
        };
    }

    // ============================================================
    // NEXT ACTION
    // ============================================================

    private String getNextAction(
            String currentAction,
            AIRecoveryDecisionService.RecoveryDecision decision,
            Payment payment) {

        String aiAction =
                applySafetyRules(
                        payment,
                        decision
                );

        /*
         * Recovery progression:
         *
         * Reminder
         *      ↓
         * Retry
         *      ↓
         * Alternate payment
         *
         * AI can still override this when the
         * situation requires a safer action.
         */

        if ("SEND_PAYMENT_REMINDER".equalsIgnoreCase(
                currentAction)) {

            if (canRetry(payment)) {
                return "AUTOMATIC_RETRY";
            }

            return "REQUEST_ALTERNATE_PAYMENT";
        }

        if ("AUTOMATIC_RETRY".equalsIgnoreCase(
                currentAction)) {

            return "REQUEST_ALTERNATE_PAYMENT";
        }

        return aiAction;
    }

    // ============================================================
    // RETRY CHECK
    // ============================================================

    private boolean canRetry(
            Payment payment) {

        if (payment == null) {
            return false;
        }

        int retryCount =
                payment.getRetryCount() == null
                        ? 0
                        : payment.getRetryCount();

        if (retryCount >= MAX_RETRY_COUNT) {
            return false;
        }

        if (payment.getFailureReason() != null) {

            String reason =
                    payment.getFailureReason()
                            .name()
                            .toUpperCase();

            if (reason.contains("FRAUD")
                    || reason.contains("SECURITY")
                    || reason.contains("EXPIRED")
                    || reason.contains("INVALID_CARD")) {

                return false;
            }
        }

        return true;
    }

    // ============================================================
    // NEXT EXECUTION TIME
    // ============================================================

    private LocalDateTime calculateNextExecutionTime(
            String action) {

        LocalDateTime now =
                LocalDateTime.now();

        if ("AUTOMATIC_RETRY".equalsIgnoreCase(
                action)) {

            return now.plusHours(1);
        }

        if ("SEND_PAYMENT_REMINDER".equalsIgnoreCase(
                action)) {

            return now.plusHours(24);
        }

        if ("REQUEST_ALTERNATE_PAYMENT".equalsIgnoreCase(
                action)) {

            return now.plusHours(24);
        }

        /*
         * Blocked actions should not actually
         * be scheduled for future execution.
         */
        if ("BLOCK_RECOVERY".equalsIgnoreCase(
                action)) {

            return now;
        }

        return now.plusHours(24);
    }

    // ============================================================
    // GET PLAN
    // ============================================================

    public RecoveryPlan getPlanById(
            Long planId) {

        return recoveryPlanRepository
                .findById(planId)
                .orElseThrow(() ->
                        new RuntimeException(
                                "Recovery plan not found with ID: "
                                        + planId
                        )
                );
    }

    // ============================================================
    // GET BY PAYMENT
    // ============================================================

    public RecoveryPlan getPlanByPaymentId(
            Long paymentId) {

        return recoveryPlanRepository
                .findByPaymentId(paymentId)
                .orElseThrow(() ->
                        new RuntimeException(
                                "Recovery plan not found for payment ID: "
                                        + paymentId
                        )
                );
    }

    // ============================================================
    // GET ALL
    // ============================================================

    public List<RecoveryPlan> getAllPlans() {

        return recoveryPlanRepository.findAll();
    }

    // ============================================================
    // GET ACTIVE
    // ============================================================

    public List<RecoveryPlan> getActivePlans() {

        return recoveryPlanRepository
                .findByStatus("ACTIVE");
    }

    // ============================================================
    // UPDATE NEXT ACTION
    // ============================================================

    @Transactional
    public RecoveryPlan updateNextAction(
            Long planId,
            String nextAction,
            LocalDateTime nextActionAt) {

        RecoveryPlan recoveryPlan =
                getPlanById(planId);

        recoveryPlan.setNextAction(
                nextAction
        );

        recoveryPlan.setNextActionAt(
                nextActionAt
        );

        recoveryPlan.setUpdatedAt(
                LocalDateTime.now()
        );

        return recoveryPlanRepository.save(
                recoveryPlan
        );
    }

    // ============================================================
    // COMPLETE
    // ============================================================

    @Transactional
    public RecoveryPlan completePlan(
            Long planId) {

        RecoveryPlan recoveryPlan =
                getPlanById(planId);

        Payment payment =
                recoveryPlan.getPayment();

        if (payment == null
                || payment.getStatus()
                != PaymentStatus.RECOVERED) {

            throw new RuntimeException(
                    "Recovery plan cannot be completed because "
                            + "payment is not recovered"
            );
        }

        recoveryPlan.setStatus(
                "COMPLETED"
        );

        recoveryPlan.setNextAction(
                null
        );

        recoveryPlan.setNextActionAt(
                null
        );

        recoveryPlan.setUpdatedAt(
                LocalDateTime.now()
        );

        return recoveryPlanRepository.save(
                recoveryPlan
        );
    }

    // ============================================================
    // EXHAUST
    // ============================================================

    @Transactional
    public RecoveryPlan exhaustPlan(
            Long planId) {

        RecoveryPlan recoveryPlan =
                getPlanById(planId);

        recoveryPlan.setStatus(
                "EXHAUSTED"
        );

        recoveryPlan.setNextAction(
                null
        );

        recoveryPlan.setNextActionAt(
                null
        );

        recoveryPlan.setUpdatedAt(
                LocalDateTime.now()
        );

        return recoveryPlanRepository.save(
                recoveryPlan
        );
    }

    // ============================================================
    // CANCEL
    // ============================================================

    @Transactional
    public RecoveryPlan cancelPlan(
            Long planId) {

        RecoveryPlan recoveryPlan =
                getPlanById(planId);

        recoveryPlan.setStatus(
                "CANCELLED"
        );

        recoveryPlan.setNextAction(
                null
        );

        recoveryPlan.setNextActionAt(
                null
        );

        recoveryPlan.setUpdatedAt(
                LocalDateTime.now()
        );

        return recoveryPlanRepository.save(
                recoveryPlan
        );
    }
}