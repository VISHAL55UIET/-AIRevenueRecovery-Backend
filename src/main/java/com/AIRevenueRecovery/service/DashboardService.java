package com.AIRevenueRecovery.service;

import com.AIRevenueRecovery.entity.PaymentStatus;
import com.AIRevenueRecovery.repository.PaymentRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

@Service
public class DashboardService {

    private final PaymentRepository paymentRepository;

    public DashboardService(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    public Map<String, Object> getDashboardStats(int period) {

        Map<String, Object> stats = new HashMap<>();

        LocalDateTime startDate =
                LocalDateTime.now().minusDays(period);

        // Total payments created in selected period
        long totalProcessed =
                paymentRepository.countByCreatedAtGreaterThanEqual(
                        startDate
                );

        // Failed payments
        long failedPayments =
                paymentRepository.countByStatusAndCreatedAtGreaterThanEqual(
                        PaymentStatus.FAILED,
                        startDate
                );

        // RECOVERED = successfully recovered payments
        long successfulPayments =
                paymentRepository.countByStatusAndCreatedAtGreaterThanEqual(
                        PaymentStatus.RECOVERED,
                        startDate
                );

        // Total recovered revenue
        Double recoveredRevenue =
                paymentRepository.getTotalAmountByStatusAndCreatedAtGreaterThanEqual(
                        PaymentStatus.RECOVERED,
                        startDate
                );

        if (recoveredRevenue == null) {
            recoveredRevenue = 0.0;
        }

        // Pending payments
        long pendingPayments =
                paymentRepository.countPendingPaymentsByPeriod(
                        Arrays.asList(
                                PaymentStatus.CREATED,
                                PaymentStatus.RETRYING
                        ),
                        startDate
                );

        // Active customers
        long activeCustomers =
                paymentRepository.countActiveCustomersByPeriod(
                        startDate
                );

        // Recovery rate
        long eligiblePayments =
                failedPayments + successfulPayments;

        double recoveryRate = 0.0;

        if (eligiblePayments > 0) {
            recoveryRate =
                    ((double) successfulPayments / eligiblePayments) * 100;
        }

        recoveryRate =
                Math.round(recoveryRate * 100.0) / 100.0;

        stats.put("period", period);
        stats.put("totalProcessed", totalProcessed);
        stats.put("failedPayments", failedPayments);
        stats.put("successfulPayments", successfulPayments);
        stats.put("recoveredRevenue", recoveredRevenue);
        stats.put("recoveryRate", recoveryRate);
        stats.put("pendingPayments", pendingPayments);
        stats.put("activeCustomers", activeCustomers);

        return stats;
    }
}