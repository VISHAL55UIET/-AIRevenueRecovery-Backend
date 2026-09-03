package com.AIRevenueRecovery.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RecoveryIntelligenceResponse {
    private String paymentId;
    private Double amount;
    private Integer recoveryScore;
    private String priority;
    private String recommendedAction;
    private String reason;
    private Integer customerSuccessfulPayments;
    private Integer customerFailedPayments;
}