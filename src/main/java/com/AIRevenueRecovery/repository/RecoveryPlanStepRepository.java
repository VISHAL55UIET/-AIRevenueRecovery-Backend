package com.AIRevenueRecovery.repository;

import com.AIRevenueRecovery.entity.RecoveryPlanStep;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
public interface RecoveryPlanStepRepository extends JpaRepository<RecoveryPlanStep, Long> {
    List<RecoveryPlanStep> findByRecoveryPlanId(Long recoveryPlanId);
    List<RecoveryPlanStep> findByStatus(String status);
    List<RecoveryPlanStep> findByAction(String action);
    Optional<RecoveryPlanStep> findByRecoveryPlanIdAndStepNumber(Long recoveryPlanId, Integer stepNumber);
    List<RecoveryPlanStep> findByStatusAndScheduledAtLessThanEqual(String status,LocalDateTime scheduledAt);
    Optional<RecoveryPlanStep> findFirstByRecoveryPlanIdOrderByStepNumberDesc(Long recoveryPlanId);
}