package com.AIRevenueRecovery.service;

import com.AIRevenueRecovery.entity.DashboardSummary;
import com.AIRevenueRecovery.entity.Payment;
import com.AIRevenueRecovery.entity.PaymentStatus;
import com.AIRevenueRecovery.entity.RecoveryAttempt;
import com.AIRevenueRecovery.repository.PaymentRepository;
import com.AIRevenueRecovery.repository.RecoveryAttemptRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DashboardSummaryService {
    private final PaymentRepository paymentRepository;
    private final RecoveryAttemptRepository recoveryAttemptRepository;
    public DashboardSummaryService(
            PaymentRepository paymentRepository,
            RecoveryAttemptRepository recoveryAttemptRepository) {
        this.paymentRepository = paymentRepository;
        this.recoveryAttemptRepository = recoveryAttemptRepository;
    }
    public DashboardSummary getSummary() {
        List<Payment> payments = paymentRepository.findAll();
        List<RecoveryAttempt> attempts = recoveryAttemptRepository.findAll();
        long totalPayments = payments.size();
        long successfulPayments = payments.stream().filter(payment -> payment.getStatus() == PaymentStatus.SUCCESS).count();
        long failedPayments =
                payments.stream().filter(payment -> payment.getStatus() == PaymentStatus.FAILED).count();

        long retryingPayments =
                payments.stream()
                        .filter(payment ->
                                payment.getStatus()
                                        == PaymentStatus.RETRYING)
                        .count();

        long totalRecoveryAttempts =
                attempts.size();

        long successfulRecoveries =
                attempts.stream().filter(attempt -> "SUCCESS".equals(attempt.getResult())).count();
        long failedRecoveries =
                attempts.stream().filter(attempt -> "FAILED".equals(attempt.getResult())).count();
        double recoveredRevenue = attempts.stream().filter(attempt -> "SUCCESS".equals(attempt.getResult()))
                        .filter(attempt -> attempt.getPayment() != null)
                        .mapToDouble(attempt -> attempt.getPayment().getAmount()).sum();
        double recoverySuccessRate = 0.0;
        if (totalRecoveryAttempts > 0) {
            recoverySuccessRate = (successfulRecoveries * 100.0) / totalRecoveryAttempts;
        }
        return new DashboardSummary(totalPayments, successfulPayments,
                failedPayments, retryingPayments,
                totalRecoveryAttempts, successfulRecoveries, failedRecoveries, recoveredRevenue, recoverySuccessRate
        );
    }
}