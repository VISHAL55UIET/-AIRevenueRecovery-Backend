package com.AIRevenueRecovery.controller;

import com.AIRevenueRecovery.service.AnalyticsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(
            AnalyticsService analyticsService) {

        this.analyticsService = analyticsService;
    }

    @GetMapping
    public Map<String, Object> getAnalytics() {

        Map<String, Object> response =
                new LinkedHashMap<>();

        response.put(
                "totalPayments",
                analyticsService.getTotalPayments()
        );

        response.put(
                "failedPayments",
                analyticsService.getFailedPayments()
        );

        response.put(
                "recoveredPayments",
                analyticsService.getRecoveredPayments()
        );

        response.put(
                "recoveryRate",
                analyticsService.getRecoveryRate()
        );

        response.put(
                "revenueRecovered",
                analyticsService.getRevenueRecovered()
        );

        return response;
    }

    @GetMapping("/revenue")
    public List<Map<String, Object>> getRevenueAnalytics(
            @RequestParam(defaultValue = "30") int period) {

        return analyticsService.getRevenueAnalytics(period);
    }
}