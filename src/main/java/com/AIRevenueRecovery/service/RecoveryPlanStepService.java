package com.AIRevenueRecovery.service;

import com.AIRevenueRecovery.entity.RecoveryPlan;
import com.AIRevenueRecovery.entity.RecoveryPlanStep;
import com.AIRevenueRecovery.repository.RecoveryPlanRepository;
import com.AIRevenueRecovery.repository.RecoveryPlanStepRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;
@Service
public class RecoveryPlanStepService {
    private final RecoveryPlanStepRepository recoveryPlanStepRepository;
    private final RecoveryPlanRepository recoveryPlanRepository;
    public RecoveryPlanStepService(
            RecoveryPlanStepRepository recoveryPlanStepRepository,
            RecoveryPlanRepository recoveryPlanRepository) {
        this.recoveryPlanStepRepository = recoveryPlanStepRepository;
        this.recoveryPlanRepository = recoveryPlanRepository;
    }
    @Transactional
    public RecoveryPlanStep createStep(
            Long recoveryPlanId,
            RecoveryPlanStep recoveryPlanStep) {
        RecoveryPlan recoveryPlan = recoveryPlanRepository.findById(recoveryPlanId).orElseThrow(() -> new RuntimeException("Recovery plan not found with ID: " + recoveryPlanId));
        recoveryPlanStep.setRecoveryPlan(recoveryPlan);
        if (recoveryPlanStep.getStepNumber() == null) {
            RecoveryPlanStep lastStep = recoveryPlanStepRepository.findFirstByRecoveryPlanIdOrderByStepNumberDesc(recoveryPlanId).orElse(null);
            if (lastStep == null) {
                recoveryPlanStep.setStepNumber(1);
            } else {
                recoveryPlanStep.setStepNumber(
                        lastStep.getStepNumber() + 1
                );
            }
        }
        if (recoveryPlanStep.getStatus() == null) {
            recoveryPlanStep.setStatus("SCHEDULED");
        }
        if (recoveryPlanStep.getScheduledAt() == null) {
            recoveryPlanStep.setScheduledAt(LocalDateTime.now());
        }
        if (recoveryPlanStep.getCreatedAt() == null) {
            recoveryPlanStep.setCreatedAt(LocalDateTime.now());
        }

        recoveryPlanStep.setUpdatedAt(LocalDateTime.now());
        return recoveryPlanStepRepository.save(recoveryPlanStep);
    }
    public RecoveryPlanStep getStepById(Long stepId) {
        return recoveryPlanStepRepository.findById(stepId).orElseThrow(() -> new RuntimeException("Recovery plan step not found with ID: " + stepId));
    }

    public List<RecoveryPlanStep> getAllSteps() {
        return recoveryPlanStepRepository.findAll();
    }
    public List<RecoveryPlanStep> getStepsByRecoveryPlanId(Long recoveryPlanId) {
        return recoveryPlanStepRepository.findByRecoveryPlanId(recoveryPlanId);
    }
    public List<RecoveryPlanStep> getScheduledSteps() {
        return recoveryPlanStepRepository.findByStatusAndScheduledAtLessThanEqual(
                        "SCHEDULED", LocalDateTime.now()
                );
    }

    @Transactional
    public RecoveryPlanStep updateStatus(
            Long stepId, String status, String result) {
        RecoveryPlanStep recoveryPlanStep =
                getStepById(stepId);

        recoveryPlanStep.setStatus(status);
        recoveryPlanStep.setResult(result);
        recoveryPlanStep.setUpdatedAt(LocalDateTime.now());
        if ("COMPLETED".equals(status) ||
                "FAILED".equals(status)) {
            recoveryPlanStep.setExecutedAt(LocalDateTime.now());
        }
        return recoveryPlanStepRepository.save(recoveryPlanStep
        );
    }
}