package com.AIRevenueRecovery.repository;

import com.AIRevenueRecovery.entity.RecoveryAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RecoveryAttemptRepository
        extends JpaRepository<RecoveryAttempt, Long> {

    List<RecoveryAttempt>
    findByPaymentIdOrderByAttemptNumberAsc(Long paymentId);

    List<RecoveryAttempt>
    findByPaymentId(Long paymentId);

    Optional<RecoveryAttempt>
    findByPaymentIdAndAttemptNumber(Long paymentId, Integer attemptNumber);
    Optional<RecoveryAttempt>
    findByRazorpayOrderId(String razorpayOrderId);
    Optional<RecoveryAttempt>
    findByRazorpayPaymentId(String razorpayPaymentId);

    Optional<RecoveryAttempt>
    findFirstByPaymentIdAndResultOrderByAttemptedAtDesc(
            Long paymentId,
            String result
    );

    long countByPaymentId(Long paymentId);

    List<RecoveryAttempt>
    findTop10ByOrderByAttemptedAtDesc();
}