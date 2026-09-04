package com.AIRevenueRecovery.service;

import com.AIRevenueRecovery.entity.Payment;
import com.AIRevenueRecovery.entity.RecoveryAttempt;
import com.AIRevenueRecovery.entity.RecoveryAttemptHistoryResponse;
import com.AIRevenueRecovery.entity.RecoveryHistoryResponse;
import com.AIRevenueRecovery.exception.PaymentNotFoundException;
import com.AIRevenueRecovery.repository.PaymentRepository;
import com.AIRevenueRecovery.repository.RecoveryAttemptRepository;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

@Service
public class RecoveryHistoryService {
    private final PaymentRepository paymentRepository;
    private final RecoveryAttemptRepository recoveryAttemptRepository;
    public RecoveryHistoryService(
            PaymentRepository paymentRepository,
            RecoveryAttemptRepository recoveryAttemptRepository) {
        this.paymentRepository = paymentRepository;
        this.recoveryAttemptRepository = recoveryAttemptRepository;
    }
    public RecoveryHistoryResponse getRecoveryHistory(Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId).orElseThrow(() -> new PaymentNotFoundException("Payment not found with ID: " + paymentId));
        List<RecoveryAttempt> attempts = recoveryAttemptRepository.findByPaymentId(paymentId);
        attempts.sort(Comparator.comparing(RecoveryAttempt::getAttemptNumber, Comparator.nullsLast(Comparator.reverseOrder())));
        List<RecoveryAttemptHistoryResponse>attemptResponses = attempts.stream().map(this::mapAttempt).toList();
        boolean successful = "SUCCESS".equalsIgnoreCase(payment.getStatus().name());
        return new RecoveryHistoryResponse(
                payment.getPaymentId(), payment.getStatus(), payment.getRetryCount(),
                attemptResponses.size(), successful, attemptResponses
        );
    }
    private RecoveryAttemptHistoryResponse mapAttempt(RecoveryAttempt attempt) {
        return new RecoveryAttemptHistoryResponse(attempt.getId(), attempt.getAttemptNumber(), attempt.getAction(),
                attempt.getResult(), attempt.getFailureReason(), attempt.getAiRecommendation(), attempt.getAiConfidence()
        );
    }
}