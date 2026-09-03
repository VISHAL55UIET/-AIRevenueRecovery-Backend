package com.AIRevenueRecovery.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "recovery_attempts",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_payment_attempt",
                        columnNames = {
                                "payment_id",
                                "attempt_number"
                        }
                )
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RecoveryAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "payment_id",
            nullable = false
    )
    private Payment payment;
    @Enumerated(EnumType.STRING)
    private FailureReason failureReason;

    @Column(name = "attempt_number", nullable = false)
    private Integer attemptNumber;

    @Column(nullable = false)
    private String action;
    private String result;
    @Column(length = 2000)
    private String aiRecommendation;
    private Double aiConfidence;

    private LocalDateTime attemptedAt;
    @Column(name = "razorpay_order_id", unique = true)
    private String razorpayOrderId;
    @Column(name = "razorpay_payment_id", unique = true)
    private String razorpayPaymentId;
}