package com.AIRevenueRecovery.service;

import com.AIRevenueRecovery.entity.PaymentStatus;
import com.AIRevenueRecovery.entity.RecoveryMetricsResponse;
import com.AIRevenueRecovery.repository.PaymentRepository;
import com.AIRevenueRecovery.repository.RecoveryAttemptRepository;
import org.springframework.stereotype.Service;

@Service
public class RecoveryMetricsService {

    private final PaymentRepository paymentRepository;
    private final RecoveryAttemptRepository recoveryAttemptRepository;
    public RecoveryMetricsService(PaymentRepository paymentRepository, RecoveryAttemptRepository recoveryAttemptRepository) {
        this.paymentRepository = paymentRepository;
        this.recoveryAttemptRepository = recoveryAttemptRepository;
    }
    public RecoveryMetricsResponse getMetrics() {
        long totalPayments = paymentRepository.count();
        long successfulPayments = paymentRepository.countByStatus(PaymentStatus.SUCCESS);
        long failedPayments = paymentRepository.countByStatus(PaymentStatus.FAILED);
        long retryingPayments = paymentRepository.countByStatus(PaymentStatus.RETRYING);
        long recoveredPayments = paymentRepository.countByStatus(PaymentStatus.RECOVERED);
        long totalRecoveryAttempts = recoveryAttemptRepository.count();
        double recoveryRate = 0.0;
        if (totalPayments > 0) {
             recoveryRate = ((double) successfulPayments / totalPayments) * 100.0;
        }
        return new RecoveryMetricsResponse(
                totalPayments, successfulPayments, failedPayments,
                retryingPayments, recoveredPayments, totalRecoveryAttempts, recoveryRate);
    }
}