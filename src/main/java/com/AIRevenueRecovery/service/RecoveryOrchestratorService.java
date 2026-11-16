
package com.AIRevenueRecovery.service;

import com.AIRevenueRecovery.entity.Payment;
import com.AIRevenueRecovery.entity.PaymentStatus;
import com.AIRevenueRecovery.policy.PolicyDecision;
import com.AIRevenueRecovery.policy.PolicyEngine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RecoveryOrchestratorService {

    private static final Logger log =
            LoggerFactory.getLogger(RecoveryOrchestratorService.class);

    private static final int ALLOW = 1;
    private static final int ESCALATE_HUMAN = 2;
    private static final int STOP = 0;

    private final RecoveryPlanService recoveryPlanService;
    private final PolicyEngine policyEngine;
    private final AuditTrailService auditTrailService;

    public RecoveryOrchestratorService(
            RecoveryPlanService recoveryPlanService,
            PolicyEngine policyEngine,
            AuditTrailService auditTrailService) {

        this.recoveryPlanService = recoveryPlanService;
        this.policyEngine = policyEngine;
        this.auditTrailService = auditTrailService;
    }

    @Transactional
    public void startRecovery(Payment payment) {

        if (payment == null) {
            throw new IllegalArgumentException(
                    "Payment is required to start recovery"
            );
        }

        if (payment.getId() == null) {
            throw new IllegalArgumentException(
                    "Payment must be persisted before recovery starts"
            );
        }

        if (payment.getStatus() != PaymentStatus.FAILED) {
            log.debug(
                    "Skipping recovery for payment {} with status {}",
                    payment.getId(),
                    payment.getStatus()
            );
            return;
        }

        PolicyDecision decision = policyEngine.evaluateStart(payment);

        switch (decision.outcome()) {

            case ALLOW -> {
                recoveryPlanService.createPlan(payment.getId());
                log.info(
                        "Recovery plan created for payment {}",
                        payment.getId()
                );
            }

            case ESCALATE_HUMAN -> log.info(
                    "Payment {} escalated to human: {}",
                    payment.getId(),
                    decision.reason()
            );

            case STOP -> log.info(
                    "Payment {} recovery stopped: {}",
                    payment.getId(),
                    decision.reason()
            );

            default -> throw new IllegalStateException(
                    "Unknown policy outcome: " + decision.outcome()
            );
        }

        audit(payment, decision);
    }

    private void audit(Payment payment, PolicyDecision decision) {

        log.info(
                "Policy audit | paymentId={} | outcome={} | rule={} | reason={}",
                payment.getId(),
                decision.outcome(),
                decision.rule(),
                decision.reason()
        );

        // Integrate AuditTrailService persistence here.
    }
}
