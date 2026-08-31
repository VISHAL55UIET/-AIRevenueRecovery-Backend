package com.AIRevenueRecovery.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DashboardSummary {
    private long totalPayments;
    private long successfulPayments;
    private long failedPayments;
    private long retryingPayments;
    private long totalRecoveryAttempts;
    private long successfulRecoveries;
    private long failedRecoveries;
    private double recoveredRevenue;
    private double recoverySuccessRate;
}