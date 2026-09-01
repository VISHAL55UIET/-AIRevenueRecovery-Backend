package com.AIRevenueRecovery.service;

import com.AIRevenueRecovery.entity.OutboxEvent;

import com.AIRevenueRecovery.repository.OutboxEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxEventServiceTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private OutboxEventService outboxEventService;

    @Test
    void shouldCreateAndSavePendingOutboxEvent() throws Exception {

        Map<String, Object> data = Map.of(
                "sagaId", "SAGA-123",
                "paymentId", 10L,
                "state", "AI_DECISION"
        );

        when(objectMapper.writeValueAsString(any()))
                .thenReturn(
                        "{\"sagaId\":\"SAGA-123\",\"paymentId\":10,\"state\":\"AI_DECISION\"}"
                );

        OutboxEvent savedEvent = new OutboxEvent();

        when(outboxEventRepository.save(any(OutboxEvent.class)))
                .thenReturn(savedEvent);

        OutboxEvent result =
                outboxEventService.createEvent(
                        "RecoverySaga",
                        "SAGA-123",
                        "RECOVERY_SAGA_AI_DECISION",
                        data
                );

        assertSame(savedEvent, result);

        ArgumentCaptor<OutboxEvent> captor =
                ArgumentCaptor.forClass(OutboxEvent.class);

        verify(outboxEventRepository)
                .save(captor.capture());

        OutboxEvent event = captor.getValue();

        assertNotNull(event.getEventId());
        assertTrue(event.getEventId().startsWith("OUTBOX-"));

        assertEquals(
                "RecoverySaga",
                event.getAggregateType()
        );

        assertEquals(
                "SAGA-123",
                event.getAggregateId()
        );

        assertEquals(
                "RECOVERY_SAGA_AI_DECISION",
                event.getEventType()
        );

        assertEquals(
                "PENDING",
                event.getStatus()
        );

        assertEquals(
                0,
                event.getRetryCount()
        );

        assertNotNull(event.getPayload());

        verify(objectMapper)
                .writeValueAsString(any());

        verifyNoMoreInteractions(outboxEventRepository);
    }

    @Test
    void shouldCreateEmptyPayloadWhenDataIsNull() {

        OutboxEvent savedEvent = new OutboxEvent();

        when(outboxEventRepository.save(any(OutboxEvent.class)))
                .thenReturn(savedEvent);

        OutboxEvent result =
                outboxEventService.createEvent(
                        "RecoverySaga",
                        "SAGA-456",
                        "RECOVERY_SAGA_STARTED",
                        null
                );

        assertSame(savedEvent, result);

        ArgumentCaptor<OutboxEvent> captor =
                ArgumentCaptor.forClass(OutboxEvent.class);

        verify(outboxEventRepository)
                .save(captor.capture());

        OutboxEvent event = captor.getValue();

        assertEquals(
                "RecoverySaga",
                event.getAggregateType()
        );

        assertEquals(
                "SAGA-456",
                event.getAggregateId()
        );

        assertEquals(
                "RECOVERY_SAGA_STARTED",
                event.getEventType()
        );

        assertEquals(
                "PENDING",
                event.getStatus()
        );

        assertEquals(
                0,
                event.getRetryCount()
        );

        assertEquals(
                "{}",
                event.getPayload()
        );

        verifyNoInteractions(objectMapper);
    }

    @Test
    void shouldFailWhenPayloadSerializationFails()
            throws Exception {

        Map<String, Object> data = Map.of(
                "sagaId",
                "SAGA-789"
        );

        when(objectMapper.writeValueAsString(any()))
                .thenThrow(
                        new RuntimeException(
                                "Serialization failed"
                        )
                );

        assertThrows(
                RuntimeException.class,
                () -> outboxEventService.createEvent(
                        "RecoverySaga",
                        "SAGA-789",
                        "RECOVERY_SAGA_STARTED",
                        data
                )
        );

        verify(objectMapper)
                .writeValueAsString(any());

        verify(
                outboxEventRepository,
                never()
        ).save(any(OutboxEvent.class));
    }
}