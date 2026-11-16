package com.AIRevenueRecovery.approval;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApprovalRequest {
    private Long stepId;
    private Long planId;
    private String approvedBy;
    private String comments;
    private String status; // e.g., "APPROVED", "REJECTED"
}