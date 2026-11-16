
package com.AIRevenueRecovery.policy;

/**
 * Result of a policy check: the action that is actually allowed to run,
 * the name of the rule that decided it, and a human-readable reason.
 *
 * The rule name is stored on the step and in the event log for audit trail.
 */
public record PolicyDecision(
        String action,
        String rule,
        String reason
) {

    public boolean blocked() {
        return RecoveryActions.BLOCK_RECOVERY.equals(action);
    }

    public boolean escalated() {
        return RecoveryActions.ESCALATE_TO_HUMAN.equals(action);
    }

    /**
     * True when automation must stop.
     */
    public boolean stops() {
        return blocked() || escalated();
    }

    /**
     * Returns numeric outcome for policy evaluation.
     */
    public int outcome() {

        if (blocked()) {
            return 0;
        }

        if (escalated()) {
            return 2;
        }

        return 1;
    }
}
