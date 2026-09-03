package com.AIRevenueRecovery.service;

import com.AIRevenueRecovery.entity.OutboxEvent;
import com.AIRevenueRecovery.repository.OutboxEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class OutboxEventService {
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;
    public OutboxEventService(OutboxEventRepository outboxEventRepository, ObjectMapper objectMapper) {
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }
    public OutboxEvent createEvent(
            String aggregateType,
            String aggregateId,
            String eventType,
            Map<String, Object> data
    ) {
        OutboxEvent event = new OutboxEvent();
        event.setEventId("OUTBOX-" + UUID.randomUUID());
        event.setAggregateType(aggregateType);
        event.setAggregateId(aggregateId);
        event.setEventType(eventType);
        event.setPayload(toJson(data));
        event.setStatus("PENDING");
        event.setRetryCount(0);
        return outboxEventRepository.save(event);
    }
    private String toJson(Map<String, Object> data) {
        if (data == null) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(new LinkedHashMap<>(data));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize outbox event payload", exception);
        }
    }
}