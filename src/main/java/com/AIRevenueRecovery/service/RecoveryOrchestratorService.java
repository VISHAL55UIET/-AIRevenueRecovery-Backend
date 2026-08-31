package com.AIRevenueRecovery.service;

import com.AIRevenueRecovery.entity.Payment;
import com.AIRevenueRecovery.entity.PaymentStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RecoveryOrchestratorService {
    private final RecoveryPlanService recoveryPlanService;
    public RecoveryOrchestratorService(RecoveryPlanService recoveryPlanService) {
        this.recoveryPlanService = recoveryPlanService;
    }
    @Transactional
    public void startRecovery(Payment payment) {
        if (payment == null) {throw new IllegalArgumentException("Payment is required to start recovery");
        }
        if (payment.getId() == null) {throw new IllegalArgumentException("Payment must be persisted before recovery starts"
            );
        }
        if (payment.getStatus() != PaymentStatus.FAILED) {
            return;
        }
        recoveryPlanService.createPlan(payment.getId());
    }
}