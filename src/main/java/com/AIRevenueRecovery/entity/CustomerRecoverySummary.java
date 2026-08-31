package com.AIRevenueRecovery.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CustomerRecoverySummary {
    private String customerId;
    private long totalPayments;
    private long successfulPayments;
    private long failedPayments;
    private double totalAmount;
    private double recoveredAmount;
    private double recoveryRate;
}