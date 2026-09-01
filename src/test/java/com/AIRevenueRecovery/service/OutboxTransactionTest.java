package com.AIRevenueRecovery.service;

import com.AIRevenueRecovery.entity.OutboxEvent;
import com.AIRevenueRecovery.entity.RecoverySaga;

import com.AIRevenueRecovery.repository.OutboxEventRepository;
import com.AIRevenueRecovery.repository.RecoverySagaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class OutboxTransactionTest {

    @Autowired
    private RecoverySagaRepository recoverySagaRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Test
    @Transactional
    void shouldPersistSagaAndOutboxInSameTransaction() {

        String sagaId =
                "TEST-SAGA-" + UUID.randomUUID();

        RecoverySaga saga =
                new RecoverySaga();

        saga.setSagaId(sagaId);
        saga.setPaymentId(999999L);
        saga.setCurrentStep("STARTED");
        saga.setStatus("STARTED");

        LocalDateTime now =
                LocalDateTime.now();

        saga.setStartedAt(now);
        saga.setUpdatedAt(now);

        recoverySagaRepository.save(saga);

        OutboxEvent event =
                new OutboxEvent();

        event.setAggregateType("RecoverySaga");
        event.setAggregateId(sagaId);
        event.setEventType("RECOVERY_SAGA_STARTED");
        event.setPayload(
                "{\"sagaId\":\"" + sagaId + "\"}"
        );
        event.setStatus("PENDING");
        event.setRetryCount(0);

        outboxEventRepository.save(event);

        recoverySagaRepository.flush();
        outboxEventRepository.flush();

        assertNotNull(saga.getId());
        assertNotNull(event.getId());

        assertEquals(
                sagaId,
                event.getAggregateId()
        );

        assertEquals(
                "PENDING",
                event.getStatus()
        );

        assertEquals(
                "RECOVERY_SAGA_STARTED",
                event.getEventType()
        );
    }
}