package com.AIRevenueRecovery.controller;

import com.AIRevenueRecovery.service.RecoveryAnalyticsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/analytics")
public class RecoveryAnalyticsController {

    private final RecoveryAnalyticsService recoveryAnalyticsService;

    public RecoveryAnalyticsController(
            RecoveryAnalyticsService recoveryAnalyticsService) {

        this.recoveryAnalyticsService =
                recoveryAnalyticsService;
    }

    @GetMapping("/monthly-revenue")
    public List<Map<String, Object>>
    getMonthlyRevenueAnalytics() {

        return recoveryAnalyticsService
                .getMonthlyRevenueAnalytics();
    }

    @GetMapping("/recovery-performance")
    public List<Map<String, Object>>
    getRecoveryPerformanceAnalytics() {

        return recoveryAnalyticsService
                .getRecoveryPerformanceAnalytics();
    }

    @GetMapping("/recovery")
    public Map<String, Object>
    getRecoveryAnalytics() {

        return recoveryAnalyticsService
                .getRecoveryAnalytics();
    }

    @GetMapping("/ai")
    public Map<String, Object>
    getAIAnalytics() {

        return recoveryAnalyticsService
                .getAIAnalytics();
    }
}