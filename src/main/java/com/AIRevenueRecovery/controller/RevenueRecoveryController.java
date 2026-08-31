package com.AIRevenueRecovery.controller;

import com.AIRevenueRecovery.entity.RecoveryAttempt;
import com.AIRevenueRecovery.service.RevenueRecoveryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/recovery")
public class RevenueRecoveryController {
    private final RevenueRecoveryService revenueRecoveryService;
    public RevenueRecoveryController(RevenueRecoveryService revenueRecoveryService) {
        this.revenueRecoveryService = revenueRecoveryService;
    }
    @PostMapping("/{paymentId}")
    public ResponseEntity<RecoveryAttempt> recoverPayment(@PathVariable Long paymentId) {
        RecoveryAttempt attempt = revenueRecoveryService.recoverPayment(paymentId);
        return ResponseEntity.ok(attempt);
    }
}