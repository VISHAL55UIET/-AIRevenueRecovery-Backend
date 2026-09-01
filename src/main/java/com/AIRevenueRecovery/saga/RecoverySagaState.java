package com.AIRevenueRecovery.saga;

public enum RecoverySagaState {

    STARTED,
    PAYMENT_VALIDATION,
    AI_DECISION,
    AI_DECISION_COMPLETED,
    RECOVERY_EXECUTION,
    RECOVERY_EXECUTED,
    COMPLETION,
    COMPLETED,
    FAILED
}