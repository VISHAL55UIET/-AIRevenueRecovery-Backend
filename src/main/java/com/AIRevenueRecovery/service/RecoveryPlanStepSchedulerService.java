package com.AIRevenueRecovery.service;

import com.AIRevenueRecovery.entity.RecoveryPlanStep;
import com.AIRevenueRecovery.repository.RecoveryPlanStepRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class RecoveryPlanStepSchedulerService {
    private final RecoveryPlanStepRepository recoveryPlanStepRepository;
    private final RecoveryPlanStepExecutionService recoveryPlanStepExecutionService;
    public RecoveryPlanStepSchedulerService(
            RecoveryPlanStepRepository recoveryPlanStepRepository,
            RecoveryPlanStepExecutionService recoveryPlanStepExecutionService) {
        this.recoveryPlanStepRepository = recoveryPlanStepRepository;
        this.recoveryPlanStepExecutionService = recoveryPlanStepExecutionService;
    }
    @Scheduled(fixedRate = 60000)
    @Transactional
    public void processScheduledSteps() {
        List<RecoveryPlanStep> steps = recoveryPlanStepRepository.findByStatusAndScheduledAtLessThanEqual("SCHEDULED",
                                LocalDateTime.now());
        for (RecoveryPlanStep step : steps) {
            try {
                step.setStatus("PROCESSING");
                step.setUpdatedAt(LocalDateTime.now());
                recoveryPlanStepRepository.save(step);
                recoveryPlanStepExecutionService.executeStep(step.getId());
                System.out.println("Recovery plan step executed: " + step.getId());
            } catch (Exception exception) {
                System.err.println("Failed to execute recovery plan step " + step.getId() + ": " + exception.getMessage()
                );
            }
        }
    }
}