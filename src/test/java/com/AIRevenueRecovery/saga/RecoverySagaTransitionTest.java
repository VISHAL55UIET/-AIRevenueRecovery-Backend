package com.AIRevenueRecovery.saga;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RecoverySagaTransitionTest {

    @Test
    void shouldAllowValidTransition() {
        assertTrue(
                RecoverySagaTransition.isAllowed(
                        RecoverySagaState.STARTED,
                        RecoverySagaState.PAYMENT_VALIDATION
                )
        );
    }

    @Test
    void shouldRejectInvalidTransition() {
        assertFalse(
                RecoverySagaTransition.isAllowed(
                        RecoverySagaState.STARTED,
                        RecoverySagaState.COMPLETED
                )
        );
    }

    @Test
    void shouldValidateValidTransition() {
        assertDoesNotThrow(() ->
                RecoverySagaTransition.validate(
                        RecoverySagaState.AI_DECISION,
                        RecoverySagaState.AI_DECISION_COMPLETED
                )
        );
    }

    @Test
    void shouldThrowForInvalidTransition() {
        assertThrows(
                IllegalStateException.class,
                () ->
                        RecoverySagaTransition.validate(
                                RecoverySagaState.AI_DECISION,
                                RecoverySagaState.COMPLETED
                        )
        );
    }

    @Test
    void completedStateShouldNotAllowFurtherTransition() {
        assertFalse(
                RecoverySagaTransition.isAllowed(
                        RecoverySagaState.COMPLETED,
                        RecoverySagaState.FAILED
                )
        );
    }
}