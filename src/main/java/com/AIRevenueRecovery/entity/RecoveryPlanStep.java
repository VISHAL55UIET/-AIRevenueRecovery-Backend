package com.AIRevenueRecovery.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "recovery_plan_steps")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RecoveryPlanStep {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne
    @JoinColumn(name = "recovery_plan_id", nullable = false)
    private RecoveryPlan recoveryPlan;
    private Integer stepNumber;
    private String action;
    private LocalDateTime scheduledAt;
    private LocalDateTime executedAt;
    private String status;
    private String result;
    private String aiRecommendation;
    private Double aiConfidence;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}