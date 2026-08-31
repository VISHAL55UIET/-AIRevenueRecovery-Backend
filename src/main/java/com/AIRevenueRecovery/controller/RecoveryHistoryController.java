package com.AIRevenueRecovery.controller;

import com.AIRevenueRecovery.entity.RecoveryHistoryResponse;
import com.AIRevenueRecovery.service.RecoveryHistoryService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/recovery-history")
public class RecoveryHistoryController {

    private final RecoveryHistoryService recoveryHistoryService;

    public RecoveryHistoryController(
            RecoveryHistoryService recoveryHistoryService) {

        this.recoveryHistoryService = recoveryHistoryService;
    }

    @GetMapping("/{paymentId}")
    public RecoveryHistoryResponse getRecoveryHistory(
            @PathVariable Long paymentId) {

        return recoveryHistoryService.getRecoveryHistory(paymentId);
    }
}