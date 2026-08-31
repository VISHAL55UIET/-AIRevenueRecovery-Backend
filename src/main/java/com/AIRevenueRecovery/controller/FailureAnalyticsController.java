package com.AIRevenueRecovery.controller;

import com.AIRevenueRecovery.service.FailureAnalyticsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/analytics/failures")
public class FailureAnalyticsController {
    private final FailureAnalyticsService failureAnalyticsService;
    public FailureAnalyticsController(FailureAnalyticsService failureAnalyticsService) {
        this.failureAnalyticsService = failureAnalyticsService;
    }
    @GetMapping
    public Map<String, Object> getFailureAnalytics() {
        return failureAnalyticsService
                .getFailureAnalytics();
    }
}