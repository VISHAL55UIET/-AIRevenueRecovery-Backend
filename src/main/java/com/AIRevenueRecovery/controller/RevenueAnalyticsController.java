package com.AIRevenueRecovery.controller;

import com.AIRevenueRecovery.entity.RevenueAnalyticsResponse;
import com.AIRevenueRecovery.service.RevenueAnalyticsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/revenue-analytics")
public class RevenueAnalyticsController {

    private final RevenueAnalyticsService revenueAnalyticsService;

    public RevenueAnalyticsController(
            RevenueAnalyticsService revenueAnalyticsService) {

        this.revenueAnalyticsService =
                revenueAnalyticsService;
    }

    @GetMapping
    public RevenueAnalyticsResponse getRevenueAnalytics() {

        return revenueAnalyticsService
                .getRevenueAnalytics();
    }
}