package com.AIRevenueRecovery.service;

import com.AIRevenueRecovery.entity.RecoveryAttempt;
import com.AIRevenueRecovery.repository.RecoveryAttemptRepository;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class FailureReasonAnalyticsService {

    private final RecoveryAttemptRepository recoveryAttemptRepository;

    public FailureReasonAnalyticsService(RecoveryAttemptRepository recoveryAttemptRepository) {
        this.recoveryAttemptRepository = recoveryAttemptRepository;
    }

    public Map<String, Object> getFailureReasonAnalytics() {
        List<RecoveryAttempt> attempts = recoveryAttemptRepository.findAll();
        Map<String, Map<String, Object>> failureStats = new LinkedHashMap<>();

        for (RecoveryAttempt attempt : attempts) {
            if (attempt.getFailureReason() == null) {
                continue;
            }
            String reason = attempt.getFailureReason().name();
            Map<String, Object> stats = failureStats.get(reason);
            if (stats == null) {

                stats = new LinkedHashMap<>();
                stats.put("totalAttempts", 0);
                stats.put("successfulAttempts", 0);
                stats.put("failedAttempts", 0);
                stats.put("successRate", 0.0);
                failureStats.put(reason, stats);
            }
            int totalAttempts = (Integer) stats.get("totalAttempts");
            stats.put("totalAttempts", totalAttempts + 1
            );

            if ("SUCCESS".equals(attempt.getResult())) {
                int successfulAttempts =
                        (Integer) stats.get("successfulAttempts");

                stats.put(
                        "successfulAttempts",
                        successfulAttempts + 1
                );

            } else if ("FAILED".equals(attempt.getResult())) {
                int failedAttempts = (Integer) stats.get("failedAttempts");
                stats.put(
                        "failedAttempts", failedAttempts + 1
                );
            }
        }
        for (Map<String, Object> stats : failureStats.values()) {
            int totalAttempts = (Integer) stats.get("totalAttempts");
            int successfulAttempts = (Integer) stats.get("successfulAttempts");
            double successRate = 0.0;
            if (totalAttempts > 0) {
                successRate = (successfulAttempts * 100.0) / totalAttempts;
            }
            stats.put("successRate", successRate);
        }
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("failureReasonAnalytics", failureStats
        );
        return response;
    }
}