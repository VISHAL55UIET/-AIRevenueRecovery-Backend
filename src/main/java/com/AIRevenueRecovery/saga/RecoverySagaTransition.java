package com.AIRevenueRecovery.saga;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public final class RecoverySagaTransition {
    private static final Map<RecoverySagaState, Set<RecoverySagaState>> ALLOWED_TRANSITIONS =
            Map.of(
                    RecoverySagaState.STARTED,
                    EnumSet.of(RecoverySagaState.PAYMENT_VALIDATION, RecoverySagaState.AI_DECISION,
                            RecoverySagaState.FAILED
                    ),

                    RecoverySagaState.PAYMENT_VALIDATION,
                    EnumSet.of(RecoverySagaState.AI_DECISION,
                            RecoverySagaState.FAILED
                    ),

                    RecoverySagaState.AI_DECISION,
                    EnumSet.of(RecoverySagaState.AI_DECISION_COMPLETED,
                            RecoverySagaState.FAILED
                    ),

                    RecoverySagaState.AI_DECISION_COMPLETED,
                    EnumSet.of(RecoverySagaState.RECOVERY_EXECUTION,
                            RecoverySagaState.FAILED
                    ),

                    RecoverySagaState.RECOVERY_EXECUTION,
                    EnumSet.of(RecoverySagaState.RECOVERY_EXECUTED,
                            RecoverySagaState.FAILED
                    ),

                    RecoverySagaState.RECOVERY_EXECUTED,
                    EnumSet.of(RecoverySagaState.COMPLETION, RecoverySagaState.COMPLETED,
                            RecoverySagaState.FAILED
                    ),

                    RecoverySagaState.COMPLETION,
                    EnumSet.of(RecoverySagaState.COMPLETED,
                            RecoverySagaState.FAILED
                    ),

                    RecoverySagaState.FAILED,
                    EnumSet.of(RecoverySagaState.AI_DECISION,
                            RecoverySagaState.RECOVERY_EXECUTION
                    ),
                    RecoverySagaState.COMPLETED, EnumSet.noneOf(RecoverySagaState.class)
            );
    private RecoverySagaTransition() {
    }
    public static boolean isAllowed(RecoverySagaState from, RecoverySagaState to) {
        return ALLOWED_TRANSITIONS.getOrDefault(from, Set.of()).contains(to);
    }
    public static void validate(RecoverySagaState from, RecoverySagaState to) {
        if (!isAllowed(from, to)) {
            throw new IllegalStateException("Invalid Recovery Saga transition: " + from + " -> " + to);
        }
    }
}