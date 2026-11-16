
package com.AIRevenueRecovery.approval;

import com.AIRevenueRecovery.entity.RecoveryPlanStep;
import com.AIRevenueRecovery.service.RecoveryPlanStepService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/approvals")
public class ApprovalController {

    private final RecoveryPlanStepService recoveryPlanStepService;

    public ApprovalController(RecoveryPlanStepService recoveryPlanStepService) {
        this.recoveryPlanStepService = recoveryPlanStepService;
    }
    @PostMapping("/approve")
    public ResponseEntity<Map<String, Object>> approveStep(
            @RequestBody ApprovalRequest request) {

        RecoveryPlanStep updatedStep =
                recoveryPlanStepService.updateStepStatus(
                        request.getStepId(),
                        "APPROVED"
                );

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Recovery plan step approved successfully");
        response.put("stepId", updatedStep.getId());
        response.put("status", updatedStep.getStatus());
        response.put("approvedBy", request.getApprovedBy());

        return ResponseEntity.ok(response);
    }

    /**
     * Recovery plan step ko reject karne ke liye API
     */
    @PostMapping("/reject")
    public ResponseEntity<Map<String, Object>> rejectStep(
            @RequestBody ApprovalRequest request) {

        RecoveryPlanStep updatedStep =
                recoveryPlanStepService.updateStatus(
                        request.getStepId(),
                        "REJECTED",
                        request.getComments() != null
                                ? request.getComments()
                                : "Step rejected by approver"
                );

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Recovery plan step rejected");
        response.put("stepId", updatedStep.getId());
        response.put("status", updatedStep.getStatus());
        response.put("reason", updatedStep.getResult());

        return ResponseEntity.ok(response);
    }

    /**
     * Specific step details nikalne ke liye API
     */
    @GetMapping("/step/{stepId}")
    public ResponseEntity<RecoveryPlanStep> getStepApprovalDetails(
            @PathVariable Long stepId) {

        RecoveryPlanStep step =
                recoveryPlanStepService.getStepById(stepId);

        return ResponseEntity.ok(step);
    }
}
