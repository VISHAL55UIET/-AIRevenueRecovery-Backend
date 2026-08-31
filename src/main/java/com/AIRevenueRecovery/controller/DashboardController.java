package com.AIRevenueRecovery.controller;

import com.AIRevenueRecovery.service.DashboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {
    private final DashboardService dashboardService;
    public DashboardController(
            DashboardService dashboardService) {

        this.dashboardService = dashboardService;
    }
    @GetMapping("/stats")
    public Map<String, Object> getDashboardStats(
            @RequestParam(
                    defaultValue = "30"
            ) int period
    ) {

        return dashboardService.getDashboardStats(
                period
        );
    }
}