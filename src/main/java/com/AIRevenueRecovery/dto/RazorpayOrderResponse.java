package com.AIRevenueRecovery.dto;

public class RazorpayOrderResponse {

    private String orderId;
    private String paymentId;
    private Long amount;
    private String currency;
    private String status;
    public RazorpayOrderResponse() {
    }
    public RazorpayOrderResponse(String orderId, String paymentId, Long amount, String currency, String status) {
        this.orderId = orderId;
        this.paymentId = paymentId;
        this.amount = amount;
        this.currency = currency;
        this.status = status;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getPaymentId() {
        return paymentId;
    }

    public void setPaymentId(String paymentId) {
        this.paymentId = paymentId;
    }

    public Long getAmount() {
        return amount;
    }

    public void setAmount(Long amount) {
        this.amount = amount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}