package com.AIRevenueRecovery.service;

import com.AIRevenueRecovery.entity.FailureReason;
import com.AIRevenueRecovery.entity.Payment;
import com.AIRevenueRecovery.entity.RecoveryAttempt;
import com.AIRevenueRecovery.repository.PaymentRepository;
import com.AIRevenueRecovery.repository.RecoveryAttemptRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class RecoveryAnalyticsService {

    private final RecoveryAttemptRepository recoveryAttemptRepository;
    private final PaymentRepository paymentRepository;

    public RecoveryAnalyticsService(
            RecoveryAttemptRepository recoveryAttemptRepository,
            PaymentRepository paymentRepository) {
        this.recoveryAttemptRepository = recoveryAttemptRepository;
        this.paymentRepository = paymentRepository;
    }
    public List<Map<String, Object>> getMonthlyRevenueAnalytics() {
        List<Payment> payments = paymentRepository.findAll();
        List<RecoveryAttempt> attempts = recoveryAttemptRepository.findAll();
        Set<Long> recoveredPaymentIds = new HashSet<>();
        for (RecoveryAttempt attempt : attempts) {
            if ("SUCCESS".equalsIgnoreCase(attempt.getResult()) && attempt.getPayment() != null
                    && attempt.getPayment().getId() != null) {
                recoveredPaymentIds.add(attempt.getPayment().getId());
            }
        }

        Map<String, Map<String, Object>> monthlyData = new LinkedHashMap<>();
        for (Payment payment : payments) {
            if (payment == null) {
                continue;
            }
            if (payment.getCreatedAt() == null && payment.getUpdatedAt() == null) {
                continue;
            }
            LocalDate date;
            if (payment.getCreatedAt() != null) {date = payment.getCreatedAt().toLocalDate();
            } else {
                date = payment.getUpdatedAt().toLocalDate();
            }
            String month = date.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
            Map<String, Object> data = monthlyData.get(month);
            if (data == null) {
                data = new LinkedHashMap<>();data.put("month", month);
                data.put("revenue", 0.0);
                data.put("recovered", 0.0);
                monthlyData.put(month, data);
            }
            double revenue = ((Number) data.get("revenue")).doubleValue();
            double recovered =((Number) data.get("recovered")).doubleValue();
            double amount = payment.getAmount() == null ? 0.0 : payment.getAmount();
            data.put("revenue", revenue + amount
            );
            if (payment.getId() != null && recoveredPaymentIds.contains(
                    payment.getId())) {
                data.put("recovered", recovered + amount);
            }
        }
        return new ArrayList<>(monthlyData.values()
        );
    }
    public List<Map<String, Object>>
    getRecoveryPerformanceAnalytics() {
        List<RecoveryAttempt> attempts = recoveryAttemptRepository.findAll();
        Map<LocalDate, Map<String, Object>> dailyData = new LinkedHashMap<>();
        LocalDate today = LocalDate.now();
        for (int i = 6; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put(
                    "date", date.getDayOfMonth() + " " + date.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
            );
            data.put("recovered", 0.0);
            data.put("failed", 0.0);
            dailyData.put(date, data);
        }
        Map<Long, RecoveryAttempt> latestAttemptByPayment = new LinkedHashMap<>();
        for (RecoveryAttempt attempt : attempts) {
            if (attempt == null || attempt.getPayment() == null || attempt.getPayment().getId() == null || attempt.getAttemptedAt() == null) {
                continue;
            }
            Long paymentId = attempt.getPayment().getId();
            RecoveryAttempt existing = latestAttemptByPayment.get(paymentId);
            if (existing == null || existing.getAttemptedAt() == null || attempt.getAttemptedAt().isAfter(existing.getAttemptedAt())) {latestAttemptByPayment.put(paymentId, attempt);
            }
        }
        for (RecoveryAttempt attempt :
                latestAttemptByPayment.values()) {
            LocalDate date = attempt.getAttemptedAt().toLocalDate();
            if (!dailyData.containsKey(date)) {
                continue;
            }
            Map<String, Object> data = dailyData.get(date);
            double amount = 0.0;
            if (attempt.getPayment() != null && attempt.getPayment().getAmount() != null) {
                amount = attempt.getPayment().getAmount();
            }
            if ("SUCCESS".equalsIgnoreCase(attempt.getResult())) {
                double recovered = ((Number) data.get("recovered")).doubleValue();
                data.put("recovered", recovered + amount);
            } else if ("FAILED".equalsIgnoreCase(attempt.getResult())) {
                double failed = ((Number) data.get("failed")).doubleValue();
                data.put("failed", failed + amount
                );
            }
        }
        return new ArrayList<>(dailyData.values());
    }
    public Map<String, Object> getRecoveryAnalytics() {
        List<RecoveryAttempt> attempts = recoveryAttemptRepository.findAll();
        int totalAttempts = attempts.size();
        int successfulAttempts = 0,failedAttempts = 0,pendingAttempts = 0;
        double recoveredRevenue = 0.0;
        Set<Long> recoveredPaymentIds = new HashSet<>();
        for (RecoveryAttempt attempt : attempts) {
            if (attempt == null) {
                continue;
            }
            String result = attempt.getResult();
            if ("SUCCESS".equalsIgnoreCase(result)) {
                successfulAttempts++;if (attempt.getPayment() != null) {
                    Long paymentId = attempt.getPayment().getId();
                    if (paymentId != null && recoveredPaymentIds.add(paymentId)) {
                        Double amount = attempt.getPayment().getAmount();
                        if (amount != null) {
                            recoveredRevenue += amount;
                        }
                    }
                }
            } else if ("FAILED".equalsIgnoreCase(result)) {
                failedAttempts++;
            } else if ("PENDING".equalsIgnoreCase(result)) {
                pendingAttempts++;
            }
        }
        int completedAttempts = successfulAttempts + failedAttempts;
        double successRate = 0.0;
        if (completedAttempts > 0) {

            successRate =
                    (successfulAttempts * 100.0)
                            / completedAttempts;
        }

        int recoveredPayments =
                recoveredPaymentIds.size();

        double averageAttemptsPerRecoveredPayment =
                recoveredPayments == 0 ? 0.0
                        : (successfulAttempts * 1.0) / recoveredPayments;
        Map<String, Object> analytics = new LinkedHashMap<>();
        analytics.put("totalRecoveryAttempts", totalAttempts);
        analytics.put("successfulRecoveryAttempts", successfulAttempts);
        analytics.put("failedRecoveryAttempts", failedAttempts);
        analytics.put("pendingRecoveryAttempts", pendingAttempts);
        analytics.put("completedRecoveryAttempts", completedAttempts);
        analytics.put("recoveredPayments", recoveredPayments);
        analytics.put("recoverySuccessRate", successRate);
        analytics.put("recoveredRevenue", recoveredRevenue
        );
        analytics.put("averageAttemptsPerRecoveredPayment", averageAttemptsPerRecoveredPayment
        );
        return analytics;
    }
    public Map<String, Object> getAIAnalytics() {
        List<RecoveryAttempt> attempts = recoveryAttemptRepository.findAll();
        Map<String, Map<String, Object>> failureStats = new LinkedHashMap<>();
        for (RecoveryAttempt attempt : attempts) {
            if (attempt == null) {
                continue;
            }
            FailureReason failureReasonEnum = attempt.getFailureReason();
            if (failureReasonEnum == null) {
                continue;
            }
            String failureReason = failureReasonEnum.name();
            Map<String, Object> stats = failureStats.get(failureReason);
            if (stats == null) {
                stats = new LinkedHashMap<>();
                stats.put("totalAttempts", 0);
                stats.put("successful", 0);
                stats.put("failed", 0);
                stats.put("pending", 0);
                stats.put("recommendedAction", null);
                stats.put("aiConfidence", null);
                failureStats.put(failureReason, stats);
            }
            int total = ((Number) stats.get("totalAttempts")).intValue();
            stats.put("totalAttempts", total + 1);
            if ("SUCCESS".equalsIgnoreCase(
                    attempt.getResult())) {

                int successful =
                        ((Number) stats.get(
                                "successful"
                        )).intValue();

                stats.put(
                        "successful",
                        successful + 1
                );

            } else if ("FAILED".equalsIgnoreCase(
                    attempt.getResult())) {

                int failed = ((Number) stats.get("failed")).intValue();
                stats.put("failed", failed + 1);
            } else if ("PENDING".equalsIgnoreCase(
                    attempt.getResult())) {
                int pending = ((Number) stats.get("pending")).intValue();
                stats.put("pending", pending + 1);
            }
            if (stats.get("recommendedAction") == null && attempt.getAiRecommendation() != null) {
                stats.put("recommendedAction", attempt.getAiRecommendation());
            }
            if (stats.get("aiConfidence") == null && attempt.getAiConfidence() != null) {stats.put("aiConfidence", attempt.getAiConfidence());
            }
        }
        for (Map<String, Object> stats : failureStats.values()) {
            int successful = ((Number) stats.get("successful")).intValue();
            int failed = ((Number) stats.get("failed")).intValue();
            int completed = successful + failed;
            double successRate = 0.0;
            if (completed > 0) {successRate = (successful * 100.0) / completed;
            }
            stats.put("successRate", successRate);
        }
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("failureReasonAnalysis", failureStats
        );

        return response;
    }
}