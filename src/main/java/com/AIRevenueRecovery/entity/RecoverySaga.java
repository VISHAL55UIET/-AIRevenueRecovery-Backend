package com.AIRevenueRecovery.entity;

import com.AIRevenueRecovery.saga.RecoverySagaState;
import com.AIRevenueRecovery.saga.RecoverySagaTransition;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "recovery_sagas",
        indexes = {
                @Index(
                        name = "idx_saga_payment",
                        columnList = "payment_id"
                ),
                @Index(
                        name = "idx_saga_status",
                        columnList = "status"
                )
        }
)
public class RecoverySaga {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(
            name = "saga_id",
            nullable = false,
            unique = true,
            length = 100
    )
    private String sagaId;

    @Column(
            name = "payment_id",
            nullable = false
    )
    private Long paymentId;

    @Column(
            name = "current_step",
            nullable = false,
            length = 100
    )
    private String currentStep;

    @Column(
            name = "status",
            nullable = false,
            length = 50
    )
    private String status;

    @Column(
            name = "action",
            length = 100
    )
    private String action;

    @Column(
            name = "failure_reason",
            length = 100
    )
    private String failureReason;

    @Column(
            name = "error_message",
            columnDefinition = "TEXT"
    )
    private String errorMessage;

    @Column(
            name = "started_at",
            nullable = false
    )
    private LocalDateTime startedAt;

    @Column(
            name = "updated_at",
            nullable = false
    )
    private LocalDateTime updatedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }


    public String getSagaId() {
        return sagaId;
    }

    public void setSagaId(String sagaId) {
        this.sagaId = sagaId;
    }

    public Long getPaymentId() {
        return paymentId;
    }

    public void setPaymentId(Long paymentId) {
        this.paymentId = paymentId;
    }

    public String getCurrentStep() {
        return currentStep;
    }

    public void setCurrentStep(String currentStep) {
        this.currentStep = currentStep;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }


    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }


    // =========================================================
    // COMPLETED AT
    // =========================================================

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }


    // =========================================================
    // STATE MACHINE
    // =========================================================

    /**
     * Safely transitions this Saga to the requested state.
     *
     * The transition is validated against the centralized
     * RecoverySagaTransition rules before modifying the entity.
     *
     * Database representation remains String-based for backward
     * compatibility with the existing MySQL/Railway schema.
     */
    public void transitionTo(RecoverySagaState nextState) {

        if (nextState == null) {
            throw new IllegalArgumentException(
                    "Next Saga state cannot be null"
            );
        }

        RecoverySagaState currentState;

        try {
            currentState = RecoverySagaState.valueOf(
                    this.currentStep
            );
        } catch (IllegalArgumentException | NullPointerException exception) {

            throw new IllegalStateException(
                    "Unknown current Recovery Saga state: "
                            + this.currentStep,
                    exception
            );
        }
        RecoverySagaTransition.validate(
                currentState,
                nextState
        );
        this.currentStep = nextState.name();
        this.status = nextState.name();

        this.updatedAt = LocalDateTime.now();
        if (nextState == RecoverySagaState.COMPLETED) {
            this.completedAt = LocalDateTime.now();
        }
    }
}