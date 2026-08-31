package com.AIRevenueRecovery.service;

import com.AIRevenueRecovery.entity.Payment;
import com.AIRevenueRecovery.entity.RecoveryEvent;
import com.AIRevenueRecovery.entity.RecoveryPlan;
import com.AIRevenueRecovery.repository.RecoveryEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;
@Service
public class RecoveryEventService {
    private final RecoveryEventRepository recoveryEventRepository;
    public RecoveryEventService(RecoveryEventRepository recoveryEventRepository) {
        this.recoveryEventRepository = recoveryEventRepository;
    }
    @Transactional
    public RecoveryEvent createEvent(
            RecoveryEvent recoveryEvent) {
        if (recoveryEvent == null) {throw new RuntimeException("Recovery event is required");
        }
        if (recoveryEvent.getPayment() == null) {throw new RuntimeException(
                    "Payment is required for recovery event");
        }
        if (recoveryEvent.getEventType() == null|| recoveryEvent.getEventType().isBlank()) {
            throw new RuntimeException("Event type is required");
        }
        if (recoveryEvent.getCreatedAt() == null) {
            recoveryEvent.setCreatedAt(LocalDateTime.now());
        }
        return recoveryEventRepository.save(
                recoveryEvent
        );
    }
    public RecoveryEvent getEventById(Long eventId) {
        return recoveryEventRepository.findById(eventId).orElseThrow(() -> new RuntimeException("Recovery event not found with ID: " + eventId));
    }

    public List<RecoveryEvent> getAllEvents() {
        return recoveryEventRepository.findAll();
    }
    public List<RecoveryEvent> getEventsByPayment(Long paymentId) {
        return recoveryEventRepository.findByPaymentIdOrderByCreatedAtDesc(paymentId);
    }
    public List<RecoveryEvent> getEventsByRecoveryPlan(Long recoveryPlanId) {
        return recoveryEventRepository.findByRecoveryPlanIdOrderByCreatedAtDesc(recoveryPlanId);
    }
    public List<RecoveryEvent> getEventsByType(String eventType) {
        return recoveryEventRepository.findByEventTypeOrderByCreatedAtDesc(eventType);
    }
    @Transactional
    public RecoveryEvent recordEvent(
            Payment payment,
            RecoveryPlan recoveryPlan,
            String eventType,
            String action,
            String status,
            String message,
            String metadata) {
        if (payment == null) {throw new RuntimeException("Payment is required");
        }
        if (eventType == null || eventType.isBlank()) {
            throw new RuntimeException("Event type is required");
        }
        RecoveryEvent event = new RecoveryEvent();
        event.setPayment(payment);
        event.setRecoveryPlan(recoveryPlan);
        event.setEventType(eventType);
        event.setAction(action);
        event.setStatus(status);
        event.setMessage(message);
        event.setMetadata(metadata);
        event.setCreatedAt(LocalDateTime.now());
        return recoveryEventRepository.save(event);
    }
    @Transactional
    public void deleteEvent(Long eventId) {
        RecoveryEvent event = getEventById(eventId);
        recoveryEventRepository.delete(event);
    }
}