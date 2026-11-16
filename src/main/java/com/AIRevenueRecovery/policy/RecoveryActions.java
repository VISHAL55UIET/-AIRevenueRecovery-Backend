package com.AIRevenueRecovery.policy;

import java.util.Set;


public final class RecoveryActions {

    private RecoveryActions() {
    }

    public static final String AUTOMATIC_RETRY = "AUTOMATIC_RETRY";
    public static final String REQUEST_ALTERNATE_PAYMENT = "REQUEST_ALTERNATE_PAYMENT";
    public static final String SEND_PAYMENT_REMINDER = "SEND_PAYMENT_REMINDER";
    public static final String ESCALATE_TO_HUMAN = "ESCALATE_TO_HUMAN";
    public static final String BLOCK_RECOVERY = "BLOCK_RECOVERY";

    public static final Set<String> ALL = Set.of(
            AUTOMATIC_RETRY,
            REQUEST_ALTERNATE_PAYMENT,
            SEND_PAYMENT_REMINDER,
            ESCALATE_TO_HUMAN,
            BLOCK_RECOVERY
    );

    public static boolean isKnown(String action) {
        return action != null && ALL.contains(action);
    }
}