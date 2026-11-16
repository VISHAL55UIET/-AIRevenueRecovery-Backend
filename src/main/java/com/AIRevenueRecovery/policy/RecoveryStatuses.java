package com.AIRevenueRecovery.policy;

public final class RecoveryStatuses {

    private RecoveryStatuses() {
    }

    // ---- plan ----
    public static final String PLAN_ACTIVE = "ACTIVE";
    public static final String PLAN_COMPLETED = "COMPLETED";
    public static final String PLAN_EXHAUSTED = "EXHAUSTED";
    public static final String PLAN_BLOCKED = "BLOCKED";
    public static final String PLAN_CANCELLED = "CANCELLED";
    public static final String PLAN_ESCALATED = "ESCALATED";

    // ---- step ----
    public static final String STEP_SCHEDULED = "SCHEDULED";
    public static final String STEP_PROCESSING = "PROCESSING";
    public static final String STEP_COMPLETED = "COMPLETED";
    public static final String STEP_FAILED = "FAILED";
    public static final String STEP_BLOCKED = "BLOCKED";
    public static final String STEP_WAITING_FOR_PAYMENT = "WAITING_FOR_PAYMENT";
    public static final String STEP_ESCALATED = "ESCALATED";
    public static final String STEP_EXPIRED = "EXPIRED";
    public static final String STEP_CANCELLED = "CANCELLED";
}