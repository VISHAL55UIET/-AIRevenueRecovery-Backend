package com.AIRevenueRecovery.controller;

import com.AIRevenueRecovery.entity.RecoveryMetricsResponse;
import com.AIRevenueRecovery.service.RecoveryMetricsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/recovery-metrics")
public class RecoveryMetricsController {

    private final RecoveryMetricsService recoveryMetricsService;

    public RecoveryMetricsController(
            RecoveryMetricsService recoveryMetricsService) {

        this.recoveryMetricsService =
                recoveryMetricsService;
    }

    @GetMapping
    public RecoveryMetricsResponse getMetrics() {

        return recoveryMetricsService.getMetrics();
    }
}