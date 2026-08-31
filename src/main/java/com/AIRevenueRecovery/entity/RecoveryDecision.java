package com.AIRevenueRecovery.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RecoveryDecision {

    private FailureReason failureReason;

    private String action;

    private String reason;

    private int priority;
}