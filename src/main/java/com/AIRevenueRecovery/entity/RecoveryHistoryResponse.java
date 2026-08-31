package com.AIRevenueRecovery.entity;

import java.util.List;

public class RecoveryHistoryResponse {

    private String paymentId;
    private PaymentStatus status;
    private Integer retryCount;
    private Integer totalAttempts;
    private boolean successful;
    private List<RecoveryAttemptHistoryResponse> attempts;
    public RecoveryHistoryResponse() {
    }
    public RecoveryHistoryResponse(
            String paymentId,
            PaymentStatus status,
            Integer retryCount,
            Integer totalAttempts,
            boolean successful,
            List<RecoveryAttemptHistoryResponse> attempts) {

        this.paymentId = paymentId;
        this.status = status;
        this.retryCount = retryCount;
        this.totalAttempts = totalAttempts;
        this.successful = successful;
        this.attempts = attempts;
    }

    public String getPaymentId() {
        return paymentId;
    }

    public void setPaymentId(String paymentId) {
        this.paymentId = paymentId;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public void setStatus(PaymentStatus status) {
        this.status = status;
    }

    public Integer getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(Integer retryCount) {
        this.retryCount = retryCount;
    }

    public Integer getTotalAttempts() {
        return totalAttempts;
    }

    public void setTotalAttempts(
            Integer totalAttempts) {

        this.totalAttempts = totalAttempts;
    }

    public boolean isSuccessful() {
        return successful;
    }

    public void setSuccessful(boolean successful) {
        this.successful = successful;
    }

    public List<RecoveryAttemptHistoryResponse>
    getAttempts() {

        return attempts;
    }

    public void setAttempts(
            List<RecoveryAttemptHistoryResponse> attempts) {

        this.attempts = attempts;
    }
}