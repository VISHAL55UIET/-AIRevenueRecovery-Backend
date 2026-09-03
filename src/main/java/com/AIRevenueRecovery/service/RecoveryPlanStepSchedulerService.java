package com.AIRevenueRecovery.service;

import com.AIRevenueRecovery.entity.RecoveryPlanStep;
import com.AIRevenueRecovery.repository.RecoveryPlanStepRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class RecoveryPlanStepSchedulerService {

    private static final Logger log =
            LoggerFactory.getLogger(
                    RecoveryPlanStepSchedulerService.class
            );
    private static final long PROCESSING_TIMEOUT_MINUTES = 10;

    private final RecoveryPlanStepRepository recoveryPlanStepRepository;
    private final RecoveryPlanStepExecutionService recoveryPlanStepExecutionService;

    public RecoveryPlanStepSchedulerService(
            RecoveryPlanStepRepository recoveryPlanStepRepository,
            RecoveryPlanStepExecutionService recoveryPlanStepExecutionService) {

        this.recoveryPlanStepRepository =
                recoveryPlanStepRepository;

        this.recoveryPlanStepExecutionService =
                recoveryPlanStepExecutionService;
    }

    @Scheduled(fixedRate = 60000)
    public void processScheduledSteps() {

        LocalDateTime now =
                LocalDateTime.now();
        recoverStaleProcessingSteps(now);
        processDueSteps(now);
    }

    @Transactional
    protected void recoverStaleProcessingSteps(
            LocalDateTime now) {

        LocalDateTime threshold =
                now.minusMinutes(
                        PROCESSING_TIMEOUT_MINUTES
                );

        int recovered =
                recoveryPlanStepRepository
                        .resetStaleProcessingSteps(
                                threshold,
                                now
                        );

        if (recovered > 0) {

            log.warn(
                    "Reset stale recovery steps. " +
                            "count={}, timeoutMinutes={}",
                    recovered,
                    PROCESSING_TIMEOUT_MINUTES
            );
        }
    }
    private void processDueSteps(
            LocalDateTime now) {

        List<RecoveryPlanStep> steps =
                recoveryPlanStepRepository
                        .findByStatusAndScheduledAtLessThanEqual(
                                "SCHEDULED",
                                now
                        );

        for (RecoveryPlanStep step : steps) {

            try {

                boolean claimed =
                        claimStep(
                                step.getId(),
                                now
                        );
                if (!claimed) {

                    log.debug(
                            "Recovery step was already claimed. " +
                                    "stepId={}",
                            step.getId()
                    );

                    continue;
                }

                log.info(
                        "Recovery step claimed. stepId={}",
                        step.getId()
                );

                recoveryPlanStepExecutionService
                        .executeStep(
                                step.getId()
                        );

                log.info(
                        "Recovery step execution finished. " +
                                "stepId={}",
                        step.getId()
                );

            } catch (Exception exception) {

                log.error(
                        "Recovery step execution failed. " +
                                "stepId={}, reason={}",
                        step.getId(),
                        exception.getMessage(),
                        exception
                );
            }
        }
    }


    @Transactional
    protected boolean claimStep(
            Long stepId,
            LocalDateTime now) {

        int updatedRows =
                recoveryPlanStepRepository.claimStep(
                        stepId,
                        now,
                        now
                );

        return updatedRows == 1;
    }
}