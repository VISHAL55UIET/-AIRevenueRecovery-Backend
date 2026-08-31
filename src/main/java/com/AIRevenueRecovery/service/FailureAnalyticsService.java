package com.AIRevenueRecovery.service;

import com.AIRevenueRecovery.entity.FailureReason;
import com.AIRevenueRecovery.entity.Payment;
import com.AIRevenueRecovery.entity.RecoveryAttempt;
import com.AIRevenueRecovery.repository.PaymentRepository;
import com.AIRevenueRecovery.repository.RecoveryAttemptRepository;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class FailureAnalyticsService {

    private final PaymentRepository paymentRepository;
    private final RecoveryAttemptRepository recoveryAttemptRepository;

    public FailureAnalyticsService(
            PaymentRepository paymentRepository,
            RecoveryAttemptRepository recoveryAttemptRepository) {

        this.paymentRepository = paymentRepository;
        this.recoveryAttemptRepository =
                recoveryAttemptRepository;
    }

    public Map<String, Object> getFailureAnalytics() {

        List<Payment> payments =
                paymentRepository.findAll();

        List<RecoveryAttempt> attempts =
                recoveryAttemptRepository.findAll();

        Map<String, Map<String, Object>> failureStats =
                new LinkedHashMap<>();

        /*
         * Analyze failed payments
         */
        for (Payment payment : payments) {

            FailureReason failureReason =
                    payment.getFailureReason();

            if (failureReason == null) {
                continue;
            }

            String reason =
                    failureReason.name();

            Map<String, Object> stats =
                    failureStats.get(reason);

            if (stats == null) {

                stats = new LinkedHashMap<>();

                stats.put(
                        "failedPayments",
                        0L
                );

                stats.put(
                        "failedAmount",
                        0.0
                );

                stats.put(
                        "recoveryAttempts",
                        0L
                );

                stats.put(
                        "successfulRecoveries",
                        0L
                );

                stats.put(
                        "failedRecoveries",
                        0L
                );

                failureStats.put(
                        reason,
                        stats
                );
            }

            long failedPayments =
                    (Long) stats.get(
                            "failedPayments"
                    );

            stats.put(
                    "failedPayments",
                    failedPayments + 1
            );

            double failedAmount =
                    (Double) stats.get(
                            "failedAmount"
                    );

            stats.put(
                    "failedAmount",
                    failedAmount + payment.getAmount()
            );
        }

        /*
         * Analyze recovery attempts
         */
        for (RecoveryAttempt attempt : attempts) {

            FailureReason failureReason =
                    attempt.getFailureReason();

            if (failureReason == null) {
                continue;
            }

            String reason =
                    failureReason.name();

            Map<String, Object> stats =
                    failureStats.get(reason);

            if (stats == null) {

                stats = new LinkedHashMap<>();

                stats.put(
                        "failedPayments",
                        0L
                );

                stats.put(
                        "failedAmount",
                        0.0
                );

                stats.put(
                        "recoveryAttempts",
                        0L
                );

                stats.put(
                        "successfulRecoveries",
                        0L
                );

                stats.put(
                        "failedRecoveries",
                        0L
                );

                failureStats.put(
                        reason,
                        stats
                );
            }

            long recoveryAttempts =
                    (Long) stats.get(
                            "recoveryAttempts"
                    );

            stats.put(
                    "recoveryAttempts",
                    recoveryAttempts + 1
            );

            if ("SUCCESS".equals(
                    attempt.getResult())) {

                long successfulRecoveries =
                        (Long) stats.get(
                                "successfulRecoveries"
                        );

                stats.put(
                        "successfulRecoveries",
                        successfulRecoveries + 1
                );

            } else if ("FAILED".equals(
                    attempt.getResult())) {

                long failedRecoveries =
                        (Long) stats.get(
                                "failedRecoveries"
                        );

                stats.put(
                        "failedRecoveries",
                        failedRecoveries + 1
                );
            }
        }

        /*
         * Calculate recovery success rate
         */
        for (Map<String, Object> stats :
                failureStats.values()) {

            long recoveryAttempts =
                    (Long) stats.get(
                            "recoveryAttempts"
                    );

            long successfulRecoveries =
                    (Long) stats.get(
                            "successfulRecoveries"
                    );

            double recoverySuccessRate =
                    0.0;

            if (recoveryAttempts > 0) {

                recoverySuccessRate =
                        (successfulRecoveries * 100.0)
                                / recoveryAttempts;
            }

            stats.put(
                    "recoverySuccessRate",
                    recoverySuccessRate
            );
        }

        /*
         * Final response
         */
        Map<String, Object> response =
                new LinkedHashMap<>();

        response.put(
                "failureReasonAnalysis",
                failureStats
        );

        return response;
    }
}