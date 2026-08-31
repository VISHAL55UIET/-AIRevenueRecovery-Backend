package com.AIRevenueRecovery.controller;

import com.AIRevenueRecovery.entity.RecoveryAttempt;
import com.AIRevenueRecovery.service.RecoveryAttemptService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/recovery-attempts")
public class RecoveryAttemptController {

    private final RecoveryAttemptService recoveryAttemptService;

    public RecoveryAttemptController(
            RecoveryAttemptService recoveryAttemptService) {

        this.recoveryAttemptService = recoveryAttemptService;
    }
    @PostMapping
    public RecoveryAttempt createAttempt(
            @RequestBody RecoveryAttempt recoveryAttempt) {
        return recoveryAttemptService.createAttempt(
                recoveryAttempt
        );
    }

    // Get all recovery attempts
    @GetMapping
    public List<RecoveryAttempt> getAllAttempts() {

        return recoveryAttemptService.getAllAttempts();
    }

    // Get recovery attempt by ID
    @GetMapping("/{attemptId}")
    public RecoveryAttempt getAttemptById(
            @PathVariable Long attemptId) {

        return recoveryAttemptService.getAttemptById(
                attemptId
        );
    }

    // Get attempts for a particular payment
    @GetMapping("/payment/{paymentId}")
    public List<RecoveryAttempt> getAttemptsByPaymentId(
            @PathVariable Long paymentId) {

        return recoveryAttemptService
                .getAttemptsByPaymentId(paymentId);
    }

    // Manually update result
    @PutMapping("/{attemptId}/result")
    public RecoveryAttempt updateResult(
            @PathVariable Long attemptId,
            @RequestParam String result) {

        return recoveryAttemptService
                .updateResult(attemptId, result);
    }

    // Process recovery attempt through payment gateway
    @PutMapping("/{attemptId}/process")
    public RecoveryAttempt processAttempt(
            @PathVariable Long attemptId) {

        return recoveryAttemptService
                .processAttempt(attemptId);
    }
}