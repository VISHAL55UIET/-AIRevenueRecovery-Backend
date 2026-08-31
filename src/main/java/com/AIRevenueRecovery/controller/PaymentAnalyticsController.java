package com.AIRevenueRecovery.controller;

import com.AIRevenueRecovery.service.PaymentAnalyticsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/analytics/payments")
public class PaymentAnalyticsController {

    private final PaymentAnalyticsService paymentAnalyticsService;

    public PaymentAnalyticsController(
            PaymentAnalyticsService paymentAnalyticsService) {

        this.paymentAnalyticsService =
                paymentAnalyticsService;
    }

    @GetMapping
    public Map<String, Object> getPaymentAnalytics() {

        return paymentAnalyticsService
                .getPaymentAnalytics();
    }
}