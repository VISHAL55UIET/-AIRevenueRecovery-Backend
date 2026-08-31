package com.AIRevenueRecovery.controller;

import com.AIRevenueRecovery.entity.RecoveryPlanStep;
import com.AIRevenueRecovery.service.RecoveryPlanStepExecutionService;
import com.AIRevenueRecovery.service.RecoveryPlanStepService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/recovery-plan-steps")
public class RecoveryPlanStepController {

    private final RecoveryPlanStepService recoveryPlanStepService;
    private final RecoveryPlanStepExecutionService recoveryPlanStepExecutionService;

    public RecoveryPlanStepController(
            RecoveryPlanStepService recoveryPlanStepService,
            RecoveryPlanStepExecutionService recoveryPlanStepExecutionService) {

        this.recoveryPlanStepService = recoveryPlanStepService;
        this.recoveryPlanStepExecutionService =
                recoveryPlanStepExecutionService;
    }

    @PostMapping("/{recoveryPlanId}")
    public RecoveryPlanStep createStep(
            @PathVariable Long recoveryPlanId,
            @RequestBody RecoveryPlanStep recoveryPlanStep) {

        return recoveryPlanStepService.createStep(
                recoveryPlanId,
                recoveryPlanStep
        );
    }

    @GetMapping
    public List<RecoveryPlanStep> getAllSteps() {

        return recoveryPlanStepService.getAllSteps();
    }

    @GetMapping("/{stepId}")
    public RecoveryPlanStep getStepById(
            @PathVariable Long stepId) {

        return recoveryPlanStepService.getStepById(
                stepId
        );
    }

    @GetMapping("/plan/{recoveryPlanId}")
    public List<RecoveryPlanStep> getStepsByRecoveryPlanId(
            @PathVariable Long recoveryPlanId) {

        return recoveryPlanStepService
                .getStepsByRecoveryPlanId(recoveryPlanId);
    }

    @GetMapping("/scheduled")
    public List<RecoveryPlanStep> getScheduledSteps() {

        return recoveryPlanStepService.getScheduledSteps();
    }

    @PutMapping("/{stepId}/status")
    public RecoveryPlanStep updateStatus(
            @PathVariable Long stepId,
            @RequestParam String status,
            @RequestParam String result) {

        return recoveryPlanStepService.updateStatus(
                stepId,
                status,
                result
        );
    }

    @PutMapping("/{stepId}/execute")
    public RecoveryPlanStep executeStep(
            @PathVariable Long stepId) {

        return recoveryPlanStepExecutionService.executeStep(
                stepId
        );
    }
}