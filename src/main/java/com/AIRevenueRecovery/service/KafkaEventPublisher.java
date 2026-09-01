package com.AIRevenueRecovery.service;

import com.AIRevenueRecovery.config.KafkaConfig;
import com.AIRevenueRecovery.entity.OutboxEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
public class KafkaEventPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;

    public KafkaEventPublisher(
            KafkaTemplate<String, String> kafkaTemplate
    ) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public CompletableFuture<SendResult<String, String>> publish(
            OutboxEvent event
    ) {

        if (event == null) {

            throw new IllegalArgumentException(
                    "Outbox event cannot be null"
            );
        }

        if (event.getEventId() == null
                || event.getEventId().isBlank()) {

            throw new IllegalArgumentException(
                    "Outbox event ID cannot be empty"
            );
        }

        if (event.getPayload() == null
                || event.getPayload().isBlank()) {

            throw new IllegalArgumentException(
                    "Outbox event payload cannot be empty: "
                            + event.getEventId()
            );
        }

        return kafkaTemplate.send(
                KafkaConfig.RECOVERY_EVENTS_TOPIC,
                event.getEventId(),
                event.getPayload()
        );
    }
}