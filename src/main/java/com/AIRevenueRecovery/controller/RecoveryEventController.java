package com.AIRevenueRecovery.controller;

import com.AIRevenueRecovery.entity.RecoveryEvent;
import com.AIRevenueRecovery.service.RecoveryEventService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/recovery-events")
public class RecoveryEventController {

    private final RecoveryEventService recoveryEventService;

    public RecoveryEventController(
            RecoveryEventService recoveryEventService) {

        this.recoveryEventService = recoveryEventService;
    }

    /*
     * Create recovery event
     */
    @PostMapping
    public ResponseEntity<RecoveryEvent> createEvent(
            @RequestBody RecoveryEvent recoveryEvent) {

        return ResponseEntity.ok(
                recoveryEventService.createEvent(recoveryEvent)
        );
    }

    /*
     * Get event by ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<RecoveryEvent> getEventById(
            @PathVariable Long id) {

        return ResponseEntity.ok(
                recoveryEventService.getEventById(id)
        );
    }

    /*
     * Get all events
     */
    @GetMapping
    public ResponseEntity<List<RecoveryEvent>> getAllEvents() {

        return ResponseEntity.ok(
                recoveryEventService.getAllEvents()
        );
    }

    /*
     * Get events by payment
     */
    @GetMapping("/payment/{paymentId}")
    public ResponseEntity<List<RecoveryEvent>> getEventsByPayment(
            @PathVariable Long paymentId) {

        return ResponseEntity.ok(
                recoveryEventService.getEventsByPayment(paymentId)
        );
    }

    /*
     * Get events by recovery plan
     */
    @GetMapping("/plan/{recoveryPlanId}")
    public ResponseEntity<List<RecoveryEvent>> getEventsByRecoveryPlan(
            @PathVariable Long recoveryPlanId) {

        return ResponseEntity.ok(
                recoveryEventService.getEventsByRecoveryPlan(
                        recoveryPlanId
                )
        );
    }

    /*
     * Get events by event type
     */
    @GetMapping("/type/{eventType}")
    public ResponseEntity<List<RecoveryEvent>> getEventsByType(
            @PathVariable String eventType) {

        return ResponseEntity.ok(
                recoveryEventService.getEventsByType(eventType)
        );
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteEvent(
            @PathVariable Long id) {

        recoveryEventService.deleteEvent(id);

        return ResponseEntity.noContent().build();
    }
}