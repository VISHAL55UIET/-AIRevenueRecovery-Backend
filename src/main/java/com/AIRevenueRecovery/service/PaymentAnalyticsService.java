package com.AIRevenueRecovery.service;

import com.AIRevenueRecovery.entity.Payment;
import com.AIRevenueRecovery.entity.PaymentStatus;
import com.AIRevenueRecovery.repository.PaymentRepository;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class PaymentAnalyticsService {
    private final PaymentRepository paymentRepository;
    public PaymentAnalyticsService(
            PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }
    public Map<String, Object> getPaymentAnalytics() {
        List<Payment> payments = paymentRepository.findAll();
        long totalPayments = payments.size();
        long successfulPayments = payments.stream().filter(payment -> payment.getStatus()== PaymentStatus.SUCCESS).count();
        long failedPayments = payments.stream().filter(payment -> payment.getStatus() == PaymentStatus.FAILED).count();
        long retryingPayments = payments.stream().filter(payment -> payment.getStatus() == PaymentStatus.RETRYING).count();
        double totalRevenue = payments.stream().mapToDouble(Payment::getAmount).sum();
        double successfulRevenue = payments.stream().filter(payment -> payment.getStatus() == PaymentStatus.SUCCESS).mapToDouble(Payment::getAmount).sum();
        double failedRevenue = payments.stream().filter(payment -> payment.getStatus() == PaymentStatus.FAILED).mapToDouble(Payment::getAmount).sum();
        double successRate = 0.0;
        if (totalPayments > 0) {
            successRate = (successfulPayments * 100.0)/ totalPayments;
        }
        Map<String, Object> analytics = new LinkedHashMap<>();
        analytics.put("totalPayments", totalPayments);
        analytics.put("successfulPayments", successfulPayments);
        analytics.put("failedPayments", failedPayments);
        analytics.put("retryingPayments", retryingPayments);
        analytics.put("totalRevenue", totalRevenue);
        analytics.put("successfulRevenue", successfulRevenue);
        analytics.put("failedRevenue", failedRevenue);
        analytics.put("paymentSuccessRate", successRate);
        return analytics;
    }
}