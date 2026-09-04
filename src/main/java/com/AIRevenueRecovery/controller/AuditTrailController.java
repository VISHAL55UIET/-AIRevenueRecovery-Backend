package com.AIRevenueRecovery.controller;

import com.AIRevenueRecovery.entity.AuditTrail;
import com.AIRevenueRecovery.service.AuditTrailService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/audit-trail")
public class AuditTrailController {

    private final AuditTrailService auditTrailService;

    public AuditTrailController(AuditTrailService auditTrailService) {
        this.auditTrailService = auditTrailService;
    }

    @PostMapping("/payment/{paymentId}")
    public AuditTrail createAudit(
            @PathVariable Long paymentId,
            @RequestParam String sagaId,
            @RequestParam String previousState,
            @RequestParam String currentState,
            @RequestParam String action) {

        return auditTrailService.record(
                paymentId,
                sagaId,
                previousState,
                currentState,
                action
        );
    }

    @GetMapping("/payment/{paymentId}")
    public List<AuditTrail> getAuditTrail(
            @PathVariable Long paymentId) {

        return auditTrailService.getByPaymentId(paymentId);
    }
}