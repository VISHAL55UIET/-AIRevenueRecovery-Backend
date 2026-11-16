
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recovery_plan_id", nullable = false)
    private RecoveryPlan recoveryPlan;

    private Integer stepNumber;

    private String action;

    private LocalDateTime scheduledAt;

    private LocalDateTime executedAt;

    private String status;

    private String result;

    @Column(columnDefinition = "TEXT")
    private String aiRecommendation;

    private Double aiConfidence;

    private String policyRule;

    @Column(name = "ai_reasoning_json", columnDefinition = "TEXT")
    private String aiReasoningJson;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public void setAiProposedAction(String action) {
        this.action = action;
    }

    public void setAiReasoningJson(String json) {
        this.aiReasoningJson = json;
    }
}
