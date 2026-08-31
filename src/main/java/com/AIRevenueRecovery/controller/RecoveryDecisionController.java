package com.AIRevenueRecovery.controller;

import com.AIRevenueRecovery.entity.FailureReason;
import com.AIRevenueRecovery.entity.RecoveryDecision;
import com.AIRevenueRecovery.service.RecoveryDecisionService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/recovery-decision")
public class RecoveryDecisionController {

    private final RecoveryDecisionService recoveryDecisionService;

    public RecoveryDecisionController(
            RecoveryDecisionService recoveryDecisionService) {

        this.recoveryDecisionService = recoveryDecisionService;
    }

    @GetMapping("/{failureReason}")
    public RecoveryDecision getDecision(
            @PathVariable FailureReason failureReason) {

        String action =
                recoveryDecisionService.decideAction(failureReason);

        String reason = getReason(failureReason);

        int priority = getPriority(failureReason);

        return new RecoveryDecision(
                failureReason,
                action,
                reason,
                priority
        );
    }

    private String getReason(FailureReason failureReason) {

        switch (failureReason) {

            case INSUFFICIENT_FUNDS:
                return "Customer may have insufficient balance. Retry after balance verification.";

            case CARD_DECLINED:
                return "Card was declined. Request an alternate payment method.";

            case NETWORK_ERROR:
                return "Temporary network issue. Retry the payment.";

            case EXPIRED_CARD:
                return "Card has expired. Customer should update card details.";

            case BANK_ERROR:
                return "Temporary bank-side issue. Retry after a delay.";

            case FRAUD_DETECTED:
                return "Fraud detected. Recovery must be blocked.";

            case UNKNOWN:
                return "Failure reason is unknown. Manual review is required.";

            default:
                return "Manual review required.";
        }
    }

    private int getPriority(FailureReason failureReason) {

        switch (failureReason) {

            case FRAUD_DETECTED:
                return 1;

            case INSUFFICIENT_FUNDS:
            case CARD_DECLINED:
                return 2;

            case NETWORK_ERROR:
            case BANK_ERROR:
                return 3;

            case EXPIRED_CARD:
                return 4;

            default:
                return 5;
        }
    }
}