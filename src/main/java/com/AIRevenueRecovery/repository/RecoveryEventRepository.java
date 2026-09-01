package com.AIRevenueRecovery.repository;

import com.AIRevenueRecovery.entity.RecoveryEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RecoveryEventRepository
        extends JpaRepository<RecoveryEvent, Long> {

    List<RecoveryEvent>
    findByPaymentIdOrderByCreatedAtDesc(Long paymentId);
    List<RecoveryEvent>
    findByRecoveryPlanIdOrderByCreatedAtDesc(Long recoveryPlanId);
    List<RecoveryEvent>
    findByEventTypeOrderByCreatedAtDesc(String eventType);
}