
package com.AIRevenueRecovery.batch;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "batch_runs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String jobName;

    private String status;

    private LocalDateTime startedAt;

    private LocalDateTime endedAt;

    @Builder.Default
    private Integer totalRecords = 0;

    @Builder.Default
    private Integer recordsProcessed = 0;

    @Builder.Default
    private Integer recordsFailed = 0;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();

        if (this.startedAt == null) {
            this.startedAt = LocalDateTime.now();
        }

        if (this.status == null) {
            this.status = "STARTED";
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public void setCompletedAt(LocalDateTime now) {
        this.endedAt = now;
    }
}
