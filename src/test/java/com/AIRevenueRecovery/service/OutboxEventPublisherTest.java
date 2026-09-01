package com.AIRevenueRecovery.service;

import com.AIRevenueRecovery.entity.OutboxEvent;
import com.AIRevenueRecovery.repository.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.SendResult;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxEventPublisherTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private KafkaEventPublisher kafkaEventPublisher;

    private OutboxEventPublisher outboxEventPublisher;

    @BeforeEach
    void setUp() {

        outboxEventPublisher =
                new OutboxEventPublisher(
                        outboxEventRepository,
                        kafkaEventPublisher,
                        true,
                        100,
                        5,
                        10
                );
    }

    @Test
    void shouldPublishPendingEvent() {

        OutboxEvent event =
                createEvent(
                        "EVENT-1",
                        "payload"
                );

        when(
                outboxEventRepository
                        .findTop100ByStatusOrderByCreatedAtAsc(
                                "PENDING"
                        )
        ).thenReturn(
                List.of(event)
        );

        mockSuccessfulKafkaPublish();

        int result =
                outboxEventPublisher
                        .publishPendingEvents();

        assertEquals(
                1,
                result
        );

        assertEquals(
                "PUBLISHED",
                event.getStatus()
        );

        assertNotNull(
                event.getPublishedAt()
        );

        assertNull(
                event.getLastError()
        );

        assertNull(
                event.getLockedBy()
        );

        assertNull(
                event.getProcessingStartedAt()
        );

        verify(
                outboxEventRepository,
                times(2)
        ).save(event);

        verify(
                kafkaEventPublisher,
                times(1)
        ).publish(event);
    }

    @Test
    void shouldNotPublishWhenThereAreNoPendingEvents() {

        when(
                outboxEventRepository
                        .findTop100ByStatusOrderByCreatedAtAsc(
                                "PENDING"
                        )
        ).thenReturn(
                List.of()
        );

        int result =
                outboxEventPublisher
                        .publishPendingEvents();

        assertEquals(
                0,
                result
        );

        verify(
                outboxEventRepository,
                never()
        ).save(
                any(OutboxEvent.class)
        );

        verify(
                kafkaEventPublisher,
                never()
        ).publish(
                any(OutboxEvent.class)
        );
    }

    @Test
    void shouldKeepEventPendingWhenPublishingFails() {

        OutboxEvent event =
                createEvent(
                        "EVENT-2",
                        ""
                );

        when(
                outboxEventRepository
                        .findTop100ByStatusOrderByCreatedAtAsc(
                                "PENDING"
                        )
        ).thenReturn(
                List.of(event)
        );

        int result =
                outboxEventPublisher
                        .publishPendingEvents();

        assertEquals(
                0,
                result
        );

        assertEquals(
                "PENDING",
                event.getStatus()
        );

        assertEquals(
                1,
                event.getRetryCount()
        );

        assertNotNull(
                event.getLastError()
        );

        assertNull(
                event.getLockedBy()
        );

        assertNull(
                event.getProcessingStartedAt()
        );

        verify(
                outboxEventRepository,
                times(2)
        ).save(event);

        verify(
                kafkaEventPublisher,
                never()
        ).publish(
                any(OutboxEvent.class)
        );
    }

    @Test
    void shouldMoveEventToFailedAfterMaxRetries() {

        OutboxEvent event =
                createEvent(
                        "EVENT-3",
                        ""
                );

        event.setRetryCount(4);

        when(
                outboxEventRepository
                        .findTop100ByStatusOrderByCreatedAtAsc(
                                "PENDING"
                        )
        ).thenReturn(
                List.of(event)
        );

        int result =
                outboxEventPublisher
                        .publishPendingEvents();

        assertEquals(
                0,
                result
        );

        assertEquals(
                "FAILED",
                event.getStatus()
        );

        assertEquals(
                5,
                event.getRetryCount()
        );

        assertNotNull(
                event.getLastError()
        );

        assertNull(
                event.getLockedBy()
        );

        assertNull(
                event.getProcessingStartedAt()
        );

        verify(
                outboxEventRepository,
                times(2)
        ).save(event);

        verify(
                kafkaEventPublisher,
                never()
        ).publish(
                any(OutboxEvent.class)
        );
    }

    @Test
    void shouldProcessMultiplePendingEvents() {

        OutboxEvent first =
                createEvent(
                        "EVENT-4",
                        "payload-1"
                );

        OutboxEvent second =
                createEvent(
                        "EVENT-5",
                        "payload-2"
                );

        when(
                outboxEventRepository
                        .findTop100ByStatusOrderByCreatedAtAsc(
                                "PENDING"
                        )
        ).thenReturn(
                List.of(
                        first,
                        second
                )
        );

        mockSuccessfulKafkaPublish();

        int result =
                outboxEventPublisher
                        .publishPendingEvents();

        assertEquals(
                2,
                result
        );

        assertEquals(
                "PUBLISHED",
                first.getStatus()
        );

        assertEquals(
                "PUBLISHED",
                second.getStatus()
        );

        assertNotNull(
                first.getPublishedAt()
        );

        assertNotNull(
                second.getPublishedAt()
        );

        assertNull(
                first.getLockedBy()
        );

        assertNull(
                second.getLockedBy()
        );

        assertNull(
                first.getProcessingStartedAt()
        );

        assertNull(
                second.getProcessingStartedAt()
        );

        verify(
                outboxEventRepository,
                times(4)
        ).save(
                any(OutboxEvent.class)
        );

        verify(
                kafkaEventPublisher,
                times(2)
        ).publish(
                any(OutboxEvent.class)
        );
    }

    @Test
    void shouldNotPublishWhenPublisherIsDisabled() {

        OutboxEventPublisher disabledPublisher =
                new OutboxEventPublisher(
                        outboxEventRepository,
                        kafkaEventPublisher,
                        false,
                        100,
                        5,
                        10
                );

        disabledPublisher.scheduledPublish();

        verify(
                outboxEventRepository,
                never()
        ).findTop100ByStatusOrderByCreatedAtAsc(
                "PENDING"
        );

        verify(
                outboxEventRepository,
                never()
        ).save(
                any(OutboxEvent.class)
        );

        verify(
                kafkaEventPublisher,
                never()
        ).publish(
                any(OutboxEvent.class)
        );
    }

    /**
     * Mock successful Kafka acknowledgement.
     */
    private void mockSuccessfulKafkaPublish() {

        SendResult<String, String> sendResult =
                mock(SendResult.class);

        when(
                kafkaEventPublisher
                        .publish(any(OutboxEvent.class))
        ).thenReturn(
                CompletableFuture.completedFuture(
                        sendResult
                )
        );
    }

    private OutboxEvent createEvent(
            String eventId,
            String payload
    ) {

        OutboxEvent event =
                new OutboxEvent();

        event.setEventId(
                eventId
        );

        event.setAggregateType(
                "RecoverySaga"
        );

        event.setAggregateId(
                "SAGA-123"
        );

        event.setEventType(
                "RECOVERY_SAGA_STARTED"
        );

        event.setPayload(
                payload
        );

        event.setStatus(
                "PENDING"
        );

        event.setRetryCount(
                0
        );

        event.setLastError(
                null
        );

        event.setLockedBy(
                null
        );

        event.setProcessingStartedAt(
                null
        );

        return event;
    }
}