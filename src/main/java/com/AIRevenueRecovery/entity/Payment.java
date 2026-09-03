package com.AIRevenueRecovery.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import java.time.LocalDateTime;
@Entity
@Table(name = "payments")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Payment {
    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true)
    private String paymentId;

    private LocalDateTime nextRetryAt;

    private String customerId;

    @ManyToOne
    @JoinColumn(name = "customer_ref_id")
    private Customer customer;

    private Double amount;

    private String currency;

    @Enumerated(EnumType.STRING)
    private PaymentStatus status;

    @Enumerated(EnumType.STRING)
    private FailureReason failureReason;

    private Integer retryCount;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
    private LocalDateTime recoveryEmailSentAt;
    private LocalDateTime failureAt;

    @Column(nullable = false)
    private boolean reminderSent = false;

    private LocalDateTime reminderSentAt;
}