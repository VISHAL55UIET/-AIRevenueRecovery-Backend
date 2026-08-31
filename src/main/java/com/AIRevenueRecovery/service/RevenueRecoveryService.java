package com.AIRevenueRecovery.service;

import com.AIRevenueRecovery.entity.Payment;
import com.AIRevenueRecovery.entity.PaymentStatus;
import com.AIRevenueRecovery.entity.RecoveryAttempt;
import com.AIRevenueRecovery.exception.MaximumRetryLimitException;
import com.AIRevenueRecovery.exception.PaymentNotFoundException;
import com.AIRevenueRecovery.repository.PaymentRepository;
import com.AIRevenueRecovery.repository.RecoveryAttemptRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

@Service
public class RevenueRecoveryService {

    private final PaymentRepository paymentRepository;
    private final RecoveryAttemptRepository recoveryAttemptRepository;
    private final RecoveryDecisionService recoveryDecisionService;
    private final AIRecoveryService aiRecoveryService;
    public RevenueRecoveryService(
            PaymentRepository paymentRepository,
            RecoveryAttemptRepository recoveryAttemptRepository,
            RecoveryDecisionService recoveryDecisionService,
            AIRecoveryService aiRecoveryService) {
        this.paymentRepository = paymentRepository;
        this.recoveryAttemptRepository = recoveryAttemptRepository;
        this.recoveryDecisionService = recoveryDecisionService;
        this.aiRecoveryService = aiRecoveryService;
    }
    @Transactional
    public RecoveryAttempt recoverPayment(Long paymentId) {

        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() ->
                        new PaymentNotFoundException(
                                "Payment not found with ID: " + paymentId
                        ));

        if (payment.getStatus() != PaymentStatus.FAILED) {

            throw new RuntimeException(
                    "Payment is not failed. Current status: "
                            + payment.getStatus()
            );
        }

        Integer retryCount = payment.getRetryCount();

        if (retryCount == null) {
            retryCount = 0;
        }

        if (retryCount >= 3) {

            throw new MaximumRetryLimitException(
                    "Maximum retry limit reached"
            );
        }

        int nextAttempt = retryCount + 1;

        /*
         * Prevent multiple pending
         * recovery attempts.
         */
        RecoveryAttempt pendingAttempt =
                recoveryAttemptRepository
                        .findFirstByPaymentIdAndResultOrderByAttemptedAtDesc(
                                paymentId,
                                "PENDING"
                        )
                        .orElse(null);

        if (pendingAttempt != null) {

            throw new RuntimeException(
                    "Recovery attempt is already pending for payment "
                            + paymentId
            );
        }

        /*
         * Prevent duplicate attempt number.
         */
        RecoveryAttempt existingAttempt =
                recoveryAttemptRepository
                        .findByPaymentIdAndAttemptNumber(
                                paymentId,
                                nextAttempt
                        )
                        .orElse(null);

        if (existingAttempt != null) {

            throw new RuntimeException(
                    "Recovery attempt already exists for payment "
                            + paymentId
                            + " with attempt number "
                            + nextAttempt
            );
        }

        /*
         * Decide recovery action.
         */
        String action =
                recoveryDecisionService.decideAction(
                        payment.getFailureReason()
                );

        /*
         * Block recovery for fraud.
         */
        if ("BLOCK_RECOVERY".equals(action)) {

            throw new RuntimeException(
                    "Recovery blocked because fraud was detected"
            );
        }

        /*
         * AI recovery analysis.
         */
        Map<String, Object> aiAnalysis =
                aiRecoveryService.analyzeRecovery(
                        payment.getFailureReason()
                );

        /*
         * Create recovery attempt.
         */
        RecoveryAttempt attempt =
                new RecoveryAttempt();

        attempt.setPayment(payment);

        attempt.setFailureReason(
                payment.getFailureReason()
        );

        attempt.setAttemptNumber(
                nextAttempt
        );

        attempt.setAction(
                action
        );

        attempt.setResult(
                "PENDING"
        );

        attempt.setAiRecommendation(
                (String) aiAnalysis.get("recommendedAction")
        );

        Object confidence =
                aiAnalysis.get("confidence");

        if (confidence instanceof Number) {

            attempt.setAiConfidence(
                    ((Number) confidence).doubleValue()
            );
        }

        attempt.setAttemptedAt(
                LocalDateTime.now()
        );

        /*
         * Update payment retry information.
         */
        payment.setRetryCount(
                nextAttempt
        );

        payment.setStatus(
                PaymentStatus.RETRYING
        );

        payment.setNextRetryAt(
                calculateNextRetryTime(nextAttempt)
        );

        payment.setUpdatedAt(
                LocalDateTime.now()
        );

        paymentRepository.save(payment);

        /*
         * Save recovery attempt.
         */
        return recoveryAttemptRepository.save(
                attempt
        );
    }

    private LocalDateTime calculateNextRetryTime(
            int attemptNumber) {

        LocalDateTime now =
                LocalDateTime.now();

        if (attemptNumber == 1) {
            return now.plusSeconds(30);
        }

        if (attemptNumber == 2) {
            return now.plusSeconds(60);
        }

        return now.plusSeconds(120);
    }
}