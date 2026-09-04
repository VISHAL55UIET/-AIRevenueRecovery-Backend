package com.AIRevenueRecovery.repository;

import com.AIRevenueRecovery.entity.AuditTrail;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditTrailRepository
        extends JpaRepository<AuditTrail, Long> {

    List<AuditTrail> findByPaymentIdOrderByOccurredAtAsc(Long paymentId);
}