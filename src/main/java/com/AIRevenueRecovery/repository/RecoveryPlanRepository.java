package com.AIRevenueRecovery.repository;

import com.AIRevenueRecovery.entity.RecoveryPlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RecoveryPlanRepository extends JpaRepository<RecoveryPlan, Long> {

    Optional<RecoveryPlan> findByPaymentId(Long paymentId);
    List<RecoveryPlan> findByStatus(String status);
    List<RecoveryPlan> findByNextAction(String nextAction);
    List<RecoveryPlan> findByStatusAndNextAction(String status, String nextAction);
    List<RecoveryPlan> findByNextActionAtLessThanEqual(java.time.LocalDateTime nextActionAt
    );
}