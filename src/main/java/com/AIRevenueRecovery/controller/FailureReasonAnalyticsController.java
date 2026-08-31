package com.AIRevenueRecovery.controller;

import com.AIRevenueRecovery.service.FailureReasonAnalyticsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/analytics/failure-reasons")
public class FailureReasonAnalyticsController {

    private final FailureReasonAnalyticsService
            failureReasonAnalyticsService;

    public FailureReasonAnalyticsController(
            FailureReasonAnalyticsService failureReasonAnalyticsService) {

        this.failureReasonAnalyticsService =
                failureReasonAnalyticsService;
    }

    @GetMapping
    public Map<String, Object> getFailureReasonAnalytics() {

        return failureReasonAnalyticsService
                .getFailureReasonAnalytics();
    }
}