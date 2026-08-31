package com.AIRevenueRecovery.entity;

public class RecoveryMetricsResponse {

    private long totalPayments;
    private long successfulPayments;
    private long failedPayments;
    private long retryingPayments;
    private long recoveredPayments;
    private long totalRecoveryAttempts;
    private double recoveryRate;
    public RecoveryMetricsResponse() {
    }
    public RecoveryMetricsResponse(
            long totalPayments,
            long successfulPayments,
            long failedPayments,
            long retryingPayments,
            long recoveredPayments,
            long totalRecoveryAttempts,
            double recoveryRate) {

        this.totalPayments = totalPayments;
        this.successfulPayments = successfulPayments;
        this.failedPayments = failedPayments;
        this.retryingPayments = retryingPayments;
        this.recoveredPayments = recoveredPayments;
        this.totalRecoveryAttempts = totalRecoveryAttempts;
        this.recoveryRate = recoveryRate;
    }
    public long getTotalPayments() {
        return totalPayments;
    }
    public void setTotalPayments(long totalPayments) {
        this.totalPayments = totalPayments;
    }
    public long getSuccessfulPayments() {
        return successfulPayments;
    }
    public void setSuccessfulPayments(
            long successfulPayments) {

        this.successfulPayments = successfulPayments;
    }
    public long getFailedPayments() {
        return failedPayments;
    }
    public void setFailedPayments(long failedPayments) {
        this.failedPayments = failedPayments;
    }

    public long getRetryingPayments() {
        return retryingPayments;
    }

    public void setRetryingPayments(
            long retryingPayments) {

        this.retryingPayments = retryingPayments;
    }

    public long getRecoveredPayments() {
        return recoveredPayments;
    }

    public void setRecoveredPayments(
            long recoveredPayments) {

        this.recoveredPayments = recoveredPayments;
    }

    public long getTotalRecoveryAttempts() {
        return totalRecoveryAttempts;
    }

    public void setTotalRecoveryAttempts(
            long totalRecoveryAttempts) {

        this.totalRecoveryAttempts =
                totalRecoveryAttempts;
    }

    public double getRecoveryRate() {
        return recoveryRate;
    }

    public void setRecoveryRate(
            double recoveryRate) {

        this.recoveryRate = recoveryRate;
    }
}