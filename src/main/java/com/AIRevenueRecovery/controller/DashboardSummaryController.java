package com.AIRevenueRecovery.controller;

import com.AIRevenueRecovery.entity.DashboardSummary;
import com.AIRevenueRecovery.service.DashboardSummaryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardSummaryController {

    private final DashboardSummaryService dashboardSummaryService;

    public DashboardSummaryController(
            DashboardSummaryService dashboardSummaryService) {

        this.dashboardSummaryService = dashboardSummaryService;
    }

    @GetMapping("/summary")
    public DashboardSummary getSummary() {

        return dashboardSummaryService.getSummary();
    }
}