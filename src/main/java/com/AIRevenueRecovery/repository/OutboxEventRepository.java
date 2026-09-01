package com.AIRevenueRecovery.repository;

import com.AIRevenueRecovery.entity.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OutboxEventRepository
        extends JpaRepository<OutboxEvent, Long> {

    List<OutboxEvent>
    findTop100ByStatusOrderByCreatedAtAsc(
            String status
    );

    List<OutboxEvent>
    findByStatusAndProcessingStartedAtBefore(
            String status,
            java.time.LocalDateTime cutoff
    );
}