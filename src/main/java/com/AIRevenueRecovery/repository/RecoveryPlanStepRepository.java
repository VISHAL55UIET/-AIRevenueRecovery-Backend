package com.AIRevenueRecovery.repository;

import com.AIRevenueRecovery.entity.RecoveryPlanStep;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface RecoveryPlanStepRepository
        extends JpaRepository<RecoveryPlanStep, Long> {

    List<RecoveryPlanStep> findByRecoveryPlanId(
            Long recoveryPlanId
    );

    List<RecoveryPlanStep> findByStatus(
            String status
    );

    List<RecoveryPlanStep> findByAction(
            String action
    );

    Optional<RecoveryPlanStep>
    findByRecoveryPlanIdAndStepNumber(
            Long recoveryPlanId,
            Integer stepNumber
    );

    List<RecoveryPlanStep>
    findByStatusAndScheduledAtLessThanEqual(
            String status,
            LocalDateTime scheduledAt
    );

    Optional<RecoveryPlanStep>
    findFirstByRecoveryPlanIdOrderByStepNumberDesc(
            Long recoveryPlanId
    );

    @Modifying
    @Transactional
    @Query("""
        UPDATE RecoveryPlanStep s
        SET s.status = 'PROCESSING',
            s.updatedAt = :updatedAt
        WHERE s.id = :stepId
          AND s.status = 'SCHEDULED'
          AND s.scheduledAt <= :now
    """)
    int claimStep(
            @Param("stepId") Long stepId,
            @Param("now") LocalDateTime now,
            @Param("updatedAt") LocalDateTime updatedAt
    );

    @Modifying
    @Transactional
    @Query("""
        UPDATE RecoveryPlanStep s
        SET s.status = 'SCHEDULED',
            s.result = 'RETRY_AFTER_STALE_PROCESSING',
            s.updatedAt = :now
        WHERE s.status = 'PROCESSING'
          AND s.updatedAt <= :threshold
    """)
    int resetStaleProcessingSteps(
            @Param("threshold") LocalDateTime threshold,
            @Param("now") LocalDateTime now
    );
}