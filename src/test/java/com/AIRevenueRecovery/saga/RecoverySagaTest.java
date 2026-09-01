package com.AIRevenueRecovery.saga;

import com.AIRevenueRecovery.entity.RecoverySaga;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class RecoverySagaTest {

    @Test
    void shouldTransitionSagaToNextState() {

        RecoverySaga saga = new RecoverySaga();

        saga.setCurrentStep(
                RecoverySagaState.STARTED.name()
        );

        saga.setStatus(
                RecoverySagaState.STARTED.name()
        );

        saga.setUpdatedAt(
                LocalDateTime.now()
        );

        saga.transitionTo(
                RecoverySagaState.PAYMENT_VALIDATION
        );

        assertEquals(
                RecoverySagaState.PAYMENT_VALIDATION.name(),
                saga.getCurrentStep()
        );

        assertEquals(
                RecoverySagaState.PAYMENT_VALIDATION.name(),
                saga.getStatus()
        );

        assertNotNull(saga.getUpdatedAt());
    }

    @Test
    void shouldRejectInvalidTransition() {

        RecoverySaga saga = new RecoverySaga();

        saga.setCurrentStep(
                RecoverySagaState.STARTED.name()
        );

        saga.setStatus(
                RecoverySagaState.STARTED.name()
        );

        assertThrows(
                IllegalStateException.class,
                () -> saga.transitionTo(
                        RecoverySagaState.COMPLETED
                )
        );

        assertEquals(
                RecoverySagaState.STARTED.name(),
                saga.getCurrentStep()
        );
    }

    @Test
    void shouldSetCompletedAtWhenSagaCompletes() {

        RecoverySaga saga = new RecoverySaga();

        saga.setCurrentStep(
                RecoverySagaState.COMPLETION.name()
        );

        saga.setStatus(
                RecoverySagaState.COMPLETION.name()
        );

        saga.transitionTo(
                RecoverySagaState.COMPLETED
        );

        assertEquals(
                RecoverySagaState.COMPLETED.name(),
                saga.getCurrentStep()
        );

        assertEquals(
                RecoverySagaState.COMPLETED.name(),
                saga.getStatus()
        );

        assertNotNull(
                saga.getCompletedAt()
        );
    }

    @Test
    void shouldNotChangeStateWhenTransitionIsInvalid() {

        RecoverySaga saga = new RecoverySaga();

        saga.setCurrentStep(
                RecoverySagaState.AI_DECISION.name()
        );

        saga.setStatus(
                RecoverySagaState.AI_DECISION.name()
        );

        assertThrows(
                IllegalStateException.class,
                () -> saga.transitionTo(
                        RecoverySagaState.COMPLETED
                )
        );

        assertEquals(
                RecoverySagaState.AI_DECISION.name(),
                saga.getCurrentStep()
        );

        assertEquals(
                RecoverySagaState.AI_DECISION.name(),
                saga.getStatus()
        );
    }
}