package com.AIRevenueRecovery.controller;

import com.AIRevenueRecovery.entity.OutboxEvent;
import com.AIRevenueRecovery.repository.OutboxEventRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/outbox")
public class OutboxEventController {

    private final OutboxEventRepository outboxEventRepository;

    public OutboxEventController(
            OutboxEventRepository outboxEventRepository
    ) {
        this.outboxEventRepository = outboxEventRepository;
    }

    @GetMapping
    public List<OutboxEvent> getAllEvents() {
        return outboxEventRepository.findAll();
    }
}