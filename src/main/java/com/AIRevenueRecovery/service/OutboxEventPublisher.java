package com.AIRevenueRecovery.service;

import com.AIRevenueRecovery.entity.OutboxEvent;
import com.AIRevenueRecovery.repository.OutboxEventRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class OutboxEventPublisher {

    private static final String PENDING = "PENDING";
    private static final String PROCESSING = "PROCESSING";
    private static final String PUBLISHED = "PUBLISHED";
    private static final String FAILED = "FAILED";

    private static final String PUBLISHER_ID = "LOCAL-PUBLISHER";

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaEventPublisher kafkaEventPublisher;

    private final boolean enabled;
    private final int batchSize;
    private final int maxRetries;
    private final int staleMinutes;

    public OutboxEventPublisher(
            OutboxEventRepository outboxEventRepository,
            KafkaEventPublisher kafkaEventPublisher,
            @Value("${outbox.publisher.enabled:true}") boolean enabled,
            @Value("${outbox.publisher.batch-size:100}") int batchSize,
            @Value("${outbox.publisher.max-retries:5}") int maxRetries,
            @Value("${outbox.publisher.stale-minutes:10}") int staleMinutes
    ) {
        this.outboxEventRepository = outboxEventRepository;
        this.kafkaEventPublisher = kafkaEventPublisher;
        this.enabled = enabled;
        this.batchSize = batchSize;
        this.maxRetries = maxRetries;
        this.staleMinutes = staleMinutes;
    }

    @Scheduled(
            fixedDelayString = "${outbox.publisher.fixed-delay:5000}",
            initialDelayString = "${outbox.publisher.initial-delay:10000}"
    )
    @Transactional
    public void scheduledPublish() {

        if (!enabled) {
            return;
        }

        publishPendingEvents();
    }

    @Transactional
    public int publishPendingEvents() {

        releaseStaleEvents();

        List<OutboxEvent> events =
                outboxEventRepository
                        .findTop100ByStatusOrderByCreatedAtAsc(
                                PENDING
                        );

        int publishedCount = 0;

        int limit = Math.min(
                events.size(),
                batchSize
        );

        for (int i = 0; i < limit; i++) {

            OutboxEvent event = events.get(i);

            try {

                markProcessing(event);

                publishToKafka(event);

                markPublished(event);

                publishedCount++;

            } catch (Exception exception) {

                handleFailure(
                        event,
                        exception
                );
            }
        }

        return publishedCount;
    }

    private void markProcessing(
            OutboxEvent event
    ) {

        event.setStatus(PROCESSING);

        event.setProcessingStartedAt(
                LocalDateTime.now()
        );

        event.setLockedBy(
                PUBLISHER_ID
        );

        outboxEventRepository.save(event);
    }

    private void publishToKafka(
            OutboxEvent event
    ) throws Exception {

        if (event == null) {

            throw new IllegalArgumentException(
                    "Outbox event cannot be null"
            );
        }

        if (event.getEventId() == null
                || event.getEventId().isBlank()) {

            throw new IllegalStateException(
                    "Outbox event ID is empty"
            );
        }

        if (event.getPayload() == null
                || event.getPayload().isBlank()) {

            throw new IllegalStateException(
                    "Outbox event payload is empty: "
                            + event.getEventId()
            );
        }

        kafkaEventPublisher
                .publish(event)
                .get(
                        10,
                        TimeUnit.SECONDS
                );
    }

    private void markPublished(
            OutboxEvent event
    ) {

        event.setStatus(PUBLISHED);

        event.setPublishedAt(
                LocalDateTime.now()
        );

        event.setLastError(null);

        event.setLockedBy(null);

        event.setProcessingStartedAt(null);

        outboxEventRepository.save(event);
    }

    private void releaseStaleEvents() {

        LocalDateTime cutoff =
                LocalDateTime.now()
                        .minusMinutes(staleMinutes);

        List<OutboxEvent> staleEvents =
                outboxEventRepository
                        .findByStatusAndProcessingStartedAtBefore(
                                PROCESSING,
                                cutoff
                        );

        for (OutboxEvent event : staleEvents) {

            event.setStatus(PENDING);

            event.setLockedBy(null);

            event.setProcessingStartedAt(null);

            outboxEventRepository.save(event);
        }
    }

    private void handleFailure(
            OutboxEvent event,
            Exception exception
    ) {

        int retryCount =
                event.getRetryCount() == null
                        ? 0
                        : event.getRetryCount();

        retryCount++;

        event.setRetryCount(retryCount);

        String errorMessage =
                exception.getMessage();

        if (errorMessage == null
                || errorMessage.isBlank()) {

            errorMessage =
                    exception.getClass()
                            .getSimpleName();
        }

        event.setLastError(
                errorMessage
        );

        event.setLockedBy(null);

        event.setProcessingStartedAt(null);

        if (retryCount >= maxRetries) {

            event.setStatus(FAILED);

        } else {

            event.setStatus(PENDING);
        }

        outboxEventRepository.save(event);
    }
}