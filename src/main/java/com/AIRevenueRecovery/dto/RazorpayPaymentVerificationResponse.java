package com.AIRevenueRecovery.dto;

public class RazorpayPaymentVerificationResponse {
    private boolean verified;
    private String paymentId;
    private String message;
    public RazorpayPaymentVerificationResponse() {
    }
    public RazorpayPaymentVerificationResponse(boolean verified, String paymentId, String message) {
        this.verified = verified;
        this.paymentId = paymentId;
        this.message = message;
    }
    public boolean isVerified() {
        return verified;
    }
    public void setVerified(boolean verified) {
        this.verified = verified;
    }
    public String getPaymentId() {
        return paymentId;
    }
    public void setPaymentId(String paymentId) {
        this.paymentId = paymentId;
    }
    public String getMessage() {
        return message;
    }
    public void setMessage(String message) {
        this.message = message;
    }
}