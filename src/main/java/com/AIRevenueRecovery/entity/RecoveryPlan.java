package com.AIRevenueRecovery.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "recovery_plans")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RecoveryPlan {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @OneToOne
    @JoinColumn(name = "payment_id", nullable = false, unique = true)
    private Payment payment;
    private String strategy;
    private Integer currentStep;
    private Integer maxSteps;
    private String status;
    private String nextAction;
    private LocalDateTime nextActionAt;
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}