package com.AIRevenueRecovery.entity;

public class RevenueAnalyticsResponse {

    private double totalRevenue;
    private double successfulRevenue;
    private double failedRevenue;
    private double retryingRevenue;
    private double recoveredRevenue;
    private double recoveryRate;
    public RevenueAnalyticsResponse() {
    }
    public RevenueAnalyticsResponse(
            double totalRevenue,
            double successfulRevenue,
            double failedRevenue,
            double retryingRevenue,
            double recoveredRevenue,
            double recoveryRate) {
        this.totalRevenue = totalRevenue;
        this.successfulRevenue = successfulRevenue;
        this.failedRevenue = failedRevenue;
        this.retryingRevenue = retryingRevenue;
        this.recoveredRevenue = recoveredRevenue;
        this.recoveryRate = recoveryRate;
    }

    public double getTotalRevenue() {
        return totalRevenue;
    }
    public void setTotalRevenue(double totalRevenue) {
        this.totalRevenue = totalRevenue;
    }
    public double getSuccessfulRevenue() {
        return successfulRevenue;
    }
    public void setSuccessfulRevenue(double successfulRevenue) {
        this.successfulRevenue = successfulRevenue;
    }
    public double getFailedRevenue() {
        return failedRevenue;
    }
    public void setFailedRevenue(double failedRevenue) {
        this.failedRevenue = failedRevenue;
    }
    public double getRetryingRevenue() {
        return retryingRevenue;
    }
    public void setRetryingRevenue(double retryingRevenue) {
        this.retryingRevenue = retryingRevenue;
    }
    public double getRecoveredRevenue() {
        return recoveredRevenue;
    }
    public void setRecoveredRevenue(double recoveredRevenue) {
        this.recoveredRevenue = recoveredRevenue;
    }
    public double getRecoveryRate() {
        return recoveryRate;
    }
    public void setRecoveryRate(double recoveryRate) {
        this.recoveryRate = recoveryRate;
    }
}