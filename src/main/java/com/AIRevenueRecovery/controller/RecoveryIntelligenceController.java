package com.AIRevenueRecovery.controller;

import com.AIRevenueRecovery.dto.RecoveryIntelligenceResponse;
import com.AIRevenueRecovery.service.RecoveryIntelligenceService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/recovery-intelligence")
public class RecoveryIntelligenceController {
    private final RecoveryIntelligenceService recoveryIntelligenceService;

    public RecoveryIntelligenceController(
            RecoveryIntelligenceService recoveryIntelligenceService) {
        this.recoveryIntelligenceService = recoveryIntelligenceService;
    }
    @GetMapping("/{paymentId}")
    public ResponseEntity<RecoveryIntelligenceResponse> analyzePayment(@PathVariable Long paymentId) {
        return ResponseEntity.ok(
                recoveryIntelligenceService.analyzePayment(paymentId)
        );
    }
}