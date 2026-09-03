package com.AIRevenueRecovery.service;

import com.AIRevenueRecovery.dto.RecoveryIntelligenceResponse;
import com.AIRevenueRecovery.entity.Payment;
import com.AIRevenueRecovery.entity.PaymentStatus;
import com.AIRevenueRecovery.repository.PaymentRepository;
import org.springframework.stereotype.Service;
import java.util.List;
@Service
public class RecoveryIntelligenceService {
    private final PaymentRepository paymentRepository;
    public RecoveryIntelligenceService(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }
    public RecoveryIntelligenceResponse analyzePayment(Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId).orElseThrow(() -> new RuntimeException("Payment not found with id: " + paymentId));
        String customerId = payment.getCustomerId();
        List<Payment> customerPayments = paymentRepository.findByCustomerId(customerId);
        int successfulPayments = 0;
        int failedPayments = 0;
        for (Payment p : customerPayments) {
            if (p.getStatus() == PaymentStatus.SUCCESS) {
                successfulPayments++;
            }
            if (p.getStatus() == PaymentStatus.FAILED) {
                failedPayments++;
            }
        }

        int score = calculateScore(payment, successfulPayments, failedPayments);
        String priority = getPriority(score);
        String action = getRecommendedAction(payment, score);
        String reason = getReason(payment, successfulPayments, failedPayments);
        return new RecoveryIntelligenceResponse(
                payment.getPaymentId(),
                payment.getAmount(),
                score, priority, action,
                reason, successfulPayments, failedPayments
        );
    }
    private int calculateScore(Payment payment, int successfulPayments, int failedPayments) {
        int score = 50;
        if (payment.getFailureReason() != null) {
            switch (payment.getFailureReason()) {
                case INSUFFICIENT_FUNDS:
                    score += 20;
                    break;
                    default:
                    score += 5;
                    break;
            }
        }
        if (successfulPayments > 0) {
            score += 15;
        }
        if (failedPayments > 3) {
            score -= 10;
        }
        Integer retryCount = payment.getRetryCount();
        if (retryCount == null || retryCount == 0) {
            score += 10;
        } else if (retryCount >= 3) {
            score -= 15;
        }
        if (payment.getAmount() != null) {
            if (payment.getAmount() <= 500) {
                score += 10;
            } else if (payment.getAmount() >= 10000) {
                score -= 10;
            }
        }
        return Math.max(0, Math.min(100, score));
    }
    private String getPriority(int score) {
        if (score >= 75) {
            return "HIGH";
        }
        if (score >= 50) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private String getRecommendedAction(Payment payment, int score) {
        if (score >= 80) {
            return "RETRY_NOW";
        }
        if (score >= 65) {
            return "RETRY_SOON";
        }
        if (score >= 50) {
            return "SEND_REMINDER";
        }
        return "MANUAL_REVIEW";
    }

    private String getReason(Payment payment, int successfulPayments, int failedPayments) {
        StringBuilder reason = new StringBuilder();
        if (payment.getFailureReason() != null) {
            reason.append("Failure reason: ").append(payment.getFailureReason());
        }
        if (successfulPayments > 0) {
            if (reason.length() > 0) {
                reason.append(". ");
            }
            reason.append("Customer has "
            ).append(successfulPayments
            ).append(" previous successful payment(s)");
        }
        if (payment.getRetryCount() == null || payment.getRetryCount() == 0) {
            if (reason.length() > 0) {reason.append(". ");
            }
            reason.append("No retry attempts have been made yet");
        }
        if (reason.length() == 0) {
            return "Insufficient historical data for recovery analysis";
        }
        return reason.toString();
    }
}