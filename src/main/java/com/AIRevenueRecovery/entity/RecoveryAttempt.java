package com.AIRevenueRecovery.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "recovery_attempts")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RecoveryAttempt {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne
    @JoinColumn(name = "payment_id")
    private Payment payment;

    @Enumerated(EnumType.STRING)
    private FailureReason failureReason;

    private Integer attemptNumber;

    private String action;

    private String result;

    private String aiRecommendation;

    private Double aiConfidence;

    private LocalDateTime attemptedAt;
}