package com.AIRevenueRecovery.controller;

import com.AIRevenueRecovery.entity.RecoveryPlan;
import com.AIRevenueRecovery.service.RecoveryPlanService;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/recovery-plans")
public class RecoveryPlanController {

    private final RecoveryPlanService recoveryPlanService;

    public RecoveryPlanController(
            RecoveryPlanService recoveryPlanService) {

        this.recoveryPlanService = recoveryPlanService;
    }

    @PostMapping("/{paymentId}")
    public RecoveryPlan createPlan(
            @PathVariable Long paymentId) {

        return recoveryPlanService.createPlan(
                paymentId
        );
    }



    @GetMapping
    public List<RecoveryPlan> getAllPlans() {

        return recoveryPlanService.getAllPlans();
    }



    @GetMapping("/{planId}")
    public RecoveryPlan getPlanById(
            @PathVariable Long planId) {

        return recoveryPlanService.getPlanById(
                planId
        );
    }


    @GetMapping("/payment/{paymentId}")
    public RecoveryPlan getPlanByPaymentId(
            @PathVariable Long paymentId) {

        return recoveryPlanService.getPlanByPaymentId(
                paymentId
        );
    }


    @GetMapping("/active")
    public List<RecoveryPlan> getActivePlans() {

        return recoveryPlanService.getActivePlans();
    }



    @PutMapping("/{planId}/action")
    public RecoveryPlan updateNextAction(
            @PathVariable Long planId,
            @RequestParam String nextAction,
            @RequestParam LocalDateTime nextActionAt) {

        return recoveryPlanService.updateNextAction(
                planId,
                nextAction,
                nextActionAt
        );
    }


    @PutMapping("/{planId}/execute")
    public RecoveryPlan executePlan(
            @PathVariable Long planId) {

        return recoveryPlanService.executePlan(
                planId
        );
    }


    @PutMapping("/{planId}/complete")
    public RecoveryPlan completePlan(
            @PathVariable Long planId) {

        return recoveryPlanService.completePlan(
                planId
        );
    }


    @PutMapping("/{planId}/exhaust")
    public RecoveryPlan exhaustPlan(
            @PathVariable Long planId) {

        return recoveryPlanService.exhaustPlan(
                planId
        );
    }


    @PutMapping("/{planId}/cancel")
    public RecoveryPlan cancelPlan(
            @PathVariable Long planId) {

        return recoveryPlanService.cancelPlan(
                planId
        );
    }
}