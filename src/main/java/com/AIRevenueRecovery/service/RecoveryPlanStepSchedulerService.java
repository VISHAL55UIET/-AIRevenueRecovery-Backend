package com.AIRevenueRecovery.service;

import com.AIRevenueRecovery.entity.RecoveryPlanStep;
import com.AIRevenueRecovery.repository.RecoveryPlanStepRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import static com.AIRevenueRecovery.policy.RecoveryStatuses.STEP_SCHEDULED;
import static com.AIRevenueRecovery.policy.RecoveryStatuses.STEP_WAITING_FOR_PAYMENT;

@Service
public class RecoveryPlanStepSchedulerService {

    private static final Logger log = LoggerFactory.getLogger(RecoveryPlanStepSchedulerService.class);

    private static final long PROCESSING_TIMEOUT_MINUTES = 10;
    private static final int BATCH_SIZE = 50;

    private final RecoveryPlanStepRepository recoveryPlanStepRepository;
    private final RecoveryPlanStepExecutionService recoveryPlanStepExecutionService;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;
    private final long waitingExpiryHours;

    public RecoveryPlanStepSchedulerService(
            RecoveryPlanStepRepository recoveryPlanStepRepository,
            RecoveryPlanStepExecutionService recoveryPlanStepExecutionService,
            PlatformTransactionManager transactionManager,
            Clock clock,
            @Value("${recovery.waiting.expiry-hours:24}") long waitingExpiryHours) {

        this.recoveryPlanStepRepository = recoveryPlanStepRepository;
        this.recoveryPlanStepExecutionService = recoveryPlanStepExecutionService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.clock = clock;
        this.waitingExpiryHours = waitingExpiryHours;
    }

    // fixedDelay: the next run starts 60s after the previous one FINISHES,
    // so a slow batch (LLM / gateway calls) can't overlap itself.
    @Scheduled(fixedDelay = 60000)
    public void processScheduledSteps() {

        LocalDateTime now = LocalDateTime.now(clock);

        recoverStaleProcessingSteps(now);
        processDueSteps(now);
        reconcileWaitingSteps(now);
    }

    // ============================================================
    // STALE PROCESSING STEPS (crash / timeout recovery)
    // ============================================================

    private void recoverStaleProcessingSteps(LocalDateTime now) {

        LocalDateTime threshold = now.minusMinutes(PROCESSING_TIMEOUT_MINUTES);

        // TransactionTemplate instead of @Transactional: a @Transactional method
        // called from inside the same class is not proxied, so it would not apply.
        Integer recovered = transactionTemplate.execute(status ->
                recoveryPlanStepRepository.resetStaleProcessingSteps(threshold, now));

        if (recovered != null && recovered > 0) {
            log.warn("Reset stale recovery steps. count={}, timeoutMinutes={}",
                    recovered, PROCESSING_TIMEOUT_MINUTES);
        }
    }
    private void processDueSteps(LocalDateTime now) {

        List<RecoveryPlanStep> steps = recoveryPlanStepRepository
                .findByStatusAndScheduledAtLessThanEqualOrderByScheduledAtAsc(
                        STEP_SCHEDULED,
                        now,
                        PageRequest.of(0, BATCH_SIZE));

        for (RecoveryPlanStep step : steps) {

            try {

                if (!claimStep(step.getId(), now)) {
                    log.debug("Recovery step was already claimed. stepId={}", step.getId());
                    continue;
                }

                log.info("Recovery step claimed. stepId={}", step.getId());

                recoveryPlanStepExecutionService.executeStep(step.getId());

                log.info("Recovery step execution finished. stepId={}", step.getId());

            } catch (Exception exception) {
                log.error("Recovery step execution failed. stepId={}, reason={}",
                        step.getId(), exception.getMessage(), exception);
            }
        }
    }

    private boolean claimStep(Long stepId, LocalDateTime now) {

        Integer updatedRows = transactionTemplate.execute(status ->
                recoveryPlanStepRepository.claimStep(stepId, now, now));

        return updatedRows != null && updatedRows == 1;
    }

    // ============================================================
    // WAITING_FOR_PAYMENT: complete if paid, expire if not
    // ============================================================

    private void reconcileWaitingSteps(LocalDateTime now) {

        LocalDateTime expiryThreshold = now.minusHours(waitingExpiryHours);

        List<RecoveryPlanStep> steps = recoveryPlanStepRepository
                .findByStatusOrderByUpdatedAtAsc(
                        STEP_WAITING_FOR_PAYMENT,
                        PageRequest.of(0, BATCH_SIZE));

        for (RecoveryPlanStep step : steps) {

            try {
                recoveryPlanStepExecutionService.reconcileWaitingStep(step.getId(), expiryThreshold);

            } catch (Exception exception) {
                log.error("Waiting step reconciliation failed. stepId={}, reason={}",
                        step.getId(), exception.getMessage(), exception);
            }
        }
    }
}