package com.AIRevenueRecovery.service;

import com.AIRevenueRecovery.entity.PaymentStatus;
import com.AIRevenueRecovery.repository.PaymentRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class DashboardService {

    private final PaymentRepository paymentRepository;

    public DashboardService(
            PaymentRepository paymentRepository
    ) {
        this.paymentRepository = paymentRepository;
    }


    // =====================================================
    // DASHBOARD STATS
    // =====================================================

    public Map<String, Object> getDashboardStats(
            int period
    ) {

        Map<String, Object> stats =
                new HashMap<>();


        // =================================================
        // CALCULATE START DATE
        // =================================================

        LocalDateTime startDate =
                LocalDateTime.now()
                        .minusDays(period);


        // =================================================
        // TOTAL PROCESSED PAYMENTS
        // =================================================

        long totalProcessed =
                paymentRepository
                        .countByCreatedAtGreaterThanEqual(
                                startDate
                        );


        // =================================================
        // FAILED PAYMENTS
        // =================================================

        long failedPayments =
                paymentRepository
                        .countByStatusAndCreatedAtGreaterThanEqual(
                                PaymentStatus.FAILED,
                                startDate
                        );


        // =================================================
        // SUCCESSFUL PAYMENTS
        // =================================================

        long successfulPayments =
                paymentRepository
                        .countByStatusAndCreatedAtGreaterThanEqual(
                                PaymentStatus.SUCCESS,
                                startDate
                        );


        // =================================================
        // RECOVERED REVENUE
        // =================================================

        Double recoveredRevenue =
                paymentRepository
                        .getTotalAmountByStatusAndCreatedAtGreaterThanEqual(
                                PaymentStatus.SUCCESS,
                                startDate
                        );

        if (recoveredRevenue == null) {
            recoveredRevenue = 0.0;
        }


        // =================================================
        // PENDING PAYMENTS
        // =================================================

        long pendingPayments =
                paymentRepository
                        .countPendingPaymentsByPeriod(
                                Arrays.asList(
                                        PaymentStatus.CREATED,
                                        PaymentStatus.RETRYING
                                ),
                                startDate
                        );


        // =================================================
        // ACTIVE CUSTOMERS
        // =================================================

        long activeCustomers =
                paymentRepository
                        .countActiveCustomersByPeriod(
                                startDate
                        );


        // =================================================
        // RECOVERY RATE
        // =================================================
        //
        // Successful payments /
        // (Failed + Successful payments)
        //

        long eligiblePayments =
                failedPayments +
                        successfulPayments;


        double recoveryRate = 0.0;


        if (eligiblePayments > 0) {

            recoveryRate =
                    (
                            (double) successfulPayments
                                    / eligiblePayments
                    ) * 100;
        }


        // Round to 2 decimal places

        recoveryRate =
                Math.round(
                        recoveryRate * 100.0
                ) / 100.0;


        // =================================================
        // RESPONSE
        // =================================================

        stats.put(
                "period",
                period
        );

        stats.put(
                "totalProcessed",
                totalProcessed
        );

        stats.put(
                "failedPayments",
                failedPayments
        );

        stats.put(
                "successfulPayments",
                successfulPayments
        );

        stats.put(
                "recoveredRevenue",
                recoveredRevenue
        );
        stats.put("recoveryRate", recoveryRate);
        stats.put("pendingPayments", pendingPayments);
        stats.put("activeCustomers", activeCustomers);
        return stats;
    }
}