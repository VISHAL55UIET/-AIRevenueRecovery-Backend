package com.AIRevenueRecovery.service;

import com.AIRevenueRecovery.entity.FailureReason;
import com.AIRevenueRecovery.entity.Payment;
import com.AIRevenueRecovery.entity.PaymentStatus;
import com.AIRevenueRecovery.entity.RecoveryAttempt;
import com.AIRevenueRecovery.repository.PaymentRepository;
import com.AIRevenueRecovery.repository.RecoveryAttemptRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AnalyticsService {
    private final PaymentRepository paymentRepository;
    private final RecoveryAttemptRepository recoveryAttemptRepository;
    private final RecoveryDecisionService recoveryDecisionService;
    public AnalyticsService(
            PaymentRepository paymentRepository, RecoveryAttemptRepository recoveryAttemptRepository,
            RecoveryDecisionService recoveryDecisionService) {
        this.paymentRepository = paymentRepository;
        this.recoveryAttemptRepository = recoveryAttemptRepository;
        this.recoveryDecisionService = recoveryDecisionService;
    }

    public long getTotalPayments() {
        return paymentRepository.count();
    }
    public long getFailedPayments() {
        return paymentRepository.findByStatus(PaymentStatus.FAILED).size();
    }
    public long getRecoveredPayments() {
        return paymentRepository
                .findByStatus(PaymentStatus.SUCCESS).size();
    }

    public double getRecoveryRate() {
        long totalPayments = paymentRepository.count();
        if (totalPayments == 0) {
            return 0.0;
        }
        long recoveredPayments = getRecoveredPayments();
        return (recoveredPayments * 100.0) / totalPayments;
    }
    public double getRevenueRecovered() {

        List<Payment> successfulPayments = paymentRepository.findByStatus(PaymentStatus.SUCCESS);
        double total = 0.0;
        for (Payment payment : successfulPayments) {
            if (payment.getAmount() != null) {
                total += payment.getAmount();
            }
        }
        return total;
    }
    public List<Map<String, Object>> getRevenueAnalytics(int period) {
        LocalDateTime startDate = LocalDateTime.now().minusDays(period);
        List<Object[]> results = paymentRepository.getDailyRevenueAnalytics(startDate);
        List<Map<String, Object>> response = new ArrayList<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd MMM");
        for (Object[] row : results) {
            String dateValue = row[0].toString();
            LocalDate date = LocalDate.parse(dateValue);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("date", date.format(formatter)
            );
            data.put("revenue",
                    ((Number) row[1]).doubleValue()
            );
            data.put("recovered", ((Number) row[2]).doubleValue());
            response.add(data);
        }
        return response;
    }
    public Map<String, Object> getAIRecoveryAnalytics() {
        List<RecoveryAttempt> attempts = recoveryAttemptRepository.findAll();
        long failedPayments = paymentRepository.findByStatus(PaymentStatus.FAILED).size();
        Map<String, Object> failureReasonAnalysis = new LinkedHashMap<>();
        for (FailureReason failureReason : FailureReason.values()) {
            if (failureReason == FailureReason.UNKNOWN) {
                continue;
            }
            int totalAttempts = 0;
            int successful = 0;
            int failed = 0;
            int pending = 0;
            for (RecoveryAttempt attempt : attempts) {
                if (attempt.getFailureReason() != failureReason) {
                    continue;
                }
                totalAttempts++;
                String result = attempt.getResult();
                if ("SUCCESS".equalsIgnoreCase(result)) {
                    successful++;
                } else if ("FAILED".equalsIgnoreCase(result)
                ) {
                    failed++;

                } else if (
                        "PENDING".equalsIgnoreCase(result) || "RETRY_SCHEDULED".equalsIgnoreCase(result)
                ) {
                    pending++;
                }
            }
            if (totalAttempts == 0) {
                continue;
            }
            double successRate = (successful * 100.0) / totalAttempts;
            String recommendedAction =
                    recoveryDecisionService.decideAction(failureReason);
            double dataConfidence;
            if (totalAttempts >= 10) {
                dataConfidence = 0.95;
            } else if (totalAttempts >= 5) {
                dataConfidence = 0.80;
            } else if (totalAttempts >= 2) {
                dataConfidence = 0.60;
            } else {
                dataConfidence = 0.40;
            }
            Map<String, Object> analysis = new LinkedHashMap<>();
            analysis.put("totalAttempts", totalAttempts);
            analysis.put("successful", successful);
            analysis.put("failed", failed);
            analysis.put("pending", pending);
            analysis.put("successRate", successRate);
            analysis.put("recommendedAction", recommendedAction);
            analysis.put("dataConfidence", dataConfidence);
            failureReasonAnalysis.put(failureReason.name(), analysis);
        }
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("failedPayments", failedPayments);
        response.put("totalRecoveryAttempts", attempts.size());
        response.put("failureReasonAnalysis", failureReasonAnalysis);
        response.put("generatedAt", LocalDateTime.now());
        return response;
    }
}