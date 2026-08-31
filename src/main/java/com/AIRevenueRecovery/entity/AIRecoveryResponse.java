package com.AIRevenueRecovery.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AIRecoveryResponse {

    private String failureReason;
    private String recommendedAction;
    private String reason;
    private double successRate;
    private double confidence;
}