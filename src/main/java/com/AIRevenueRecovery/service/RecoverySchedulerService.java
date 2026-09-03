package com.AIRevenueRecovery.service;

import com.AIRevenueRecovery.entity.Payment;
import com.AIRevenueRecovery.entity.PaymentStatus;
import com.AIRevenueRecovery.entity.RecoveryAttempt;
import com.AIRevenueRecovery.repository.PaymentRepository;
import com.AIRevenueRecovery.repository.RecoveryAttemptRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class RecoverySchedulerService {
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private final PaymentRepository paymentRepository;
    private final RecoveryAttemptRepository recoveryAttemptRepository;
    private final RecoveryAttemptService recoveryAttemptService;
    private final RetrySchedulingService retrySchedulingService;
    public RecoverySchedulerService(
            PaymentRepository paymentRepository,
            RecoveryAttemptRepository recoveryAttemptRepository,
            RecoveryAttemptService recoveryAttemptService,
            RetrySchedulingService retrySchedulingService) {
        this.paymentRepository = paymentRepository;
        this.recoveryAttemptRepository = recoveryAttemptRepository;
        this.recoveryAttemptService = recoveryAttemptService;
        this.retrySchedulingService =
                retrySchedulingService;
    }
    @Scheduled(fixedRate = 60000)
    @Transactional
    public void processScheduledRecoveries() {
        LocalDateTime currentTime =
                LocalDateTime.now();
        List<Payment> payments = paymentRepository.findPaymentsReadyForRetry(PaymentStatus.RETRYING, currentTime);
        for (Payment payment : payments) {
            try {
                processPaymentRecovery(payment);
            } catch (Exception exception) {
                System.err.println(
                        "Recovery scheduler error for payment " + payment.getId() + ": " + exception.getMessage());
            }
        }
    }
    private void processPaymentRecovery(Payment payment) {
        if (payment.getStatus()!= PaymentStatus.RETRYING) {
            return;
        }
        if (payment.getRetryCount() != null && payment.getRetryCount() >= MAX_RETRY_ATTEMPTS) {payment.setStatus(PaymentStatus.FAILED);
            payment.setNextRetryAt(null);
            payment.setUpdatedAt(LocalDateTime.now());
            paymentRepository.save(payment);
            return;
        }
        int attemptNumber = payment.getRetryCount() == null ? 1 : payment.getRetryCount() + 1;
        Optional<RecoveryAttempt> existingAttempt =
                recoveryAttemptRepository.findByPaymentIdAndAttemptNumber(payment.getId(), attemptNumber);
        if (existingAttempt.isPresent()) {
            if ("PENDING".equalsIgnoreCase(existingAttempt.get().getResult())) {
                return;
            }
            return;
        }
        Optional<RecoveryAttempt> pendingAttempt =
                recoveryAttemptRepository.findFirstByPaymentIdAndResultOrderByAttemptedAtDesc(payment.getId(), "PENDING");
        if (pendingAttempt.isPresent()){
            return;
        }
        RecoveryAttempt attempt = new RecoveryAttempt();
        attempt.setPayment(payment);
        attempt.setFailureReason(payment.getFailureReason()
        );
        attempt.setAttemptNumber(attemptNumber);
        attempt.setAction("AUTOMATIC_RETRY");
        attempt.setResult("PENDING");
        attempt.setAttemptedAt(LocalDateTime.now());
        RecoveryAttempt savedAttempt =
                recoveryAttemptRepository.save(attempt);
        payment.setRetryCount(attemptNumber);
        LocalDateTime nextRetry = retrySchedulingService.calculateNextRetry(payment.getFailureReason(), attemptNumber);
        payment.setNextRetryAt(nextRetry);
        payment.setUpdatedAt(LocalDateTime.now()
        );
        paymentRepository.save(payment);
        recoveryAttemptService.processAttempt(savedAttempt.getId());
    }
}