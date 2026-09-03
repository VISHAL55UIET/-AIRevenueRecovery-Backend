package com.AIRevenueRecovery.service;

import com.AIRevenueRecovery.entity.PaymentStatus;
import com.AIRevenueRecovery.entity.RevenueAnalyticsResponse;
import com.AIRevenueRecovery.repository.PaymentRepository;
import org.springframework.stereotype.Service;
@Service
public class RevenueAnalyticsService {
    private final PaymentRepository paymentRepository;
    public RevenueAnalyticsService(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }
    public RevenueAnalyticsResponse getRevenueAnalytics() {
        double totalRevenue = getAmount(PaymentStatus.SUCCESS) + getAmount(PaymentStatus.FAILED) + getAmount(PaymentStatus.RETRYING)
                        + getAmount(PaymentStatus.CREATED) + getAmount(PaymentStatus.RECOVERED);
        double successfulRevenue = getAmount(PaymentStatus.SUCCESS);
        double failedRevenue = getAmount(PaymentStatus.FAILED);
        double retryingRevenue = getAmount(PaymentStatus.RETRYING);
        double recoveredRevenue = getAmount(PaymentStatus.RECOVERED);
        double recoveryRate = 0.0;
        if (totalRevenue > 0) {
            recoveryRate = ((successfulRevenue + recoveredRevenue) / totalRevenue) * 100.0;
        }
        return new RevenueAnalyticsResponse(totalRevenue,
                successfulRevenue, failedRevenue, retryingRevenue, recoveredRevenue, recoveryRate
        );
    }
    private double getAmount(PaymentStatus status) {
        Double amount = paymentRepository.getTotalAmountByStatus(status);
        return amount == null ? 0.0 : amount;
    }
}