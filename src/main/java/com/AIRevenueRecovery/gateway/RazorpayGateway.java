
package com.AIRevenueRecovery.gateway;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class RazorpayGateway implements PaymentGateway {

    @Override
    public Map<String, Object> processPayment(
            Long paymentId,
            Double amount) {

        Map<String, Object> response = new HashMap<>();

        // Razorpay API integration will go here.
        response.put("success", false);
        response.put("paymentId", paymentId);
        response.put("amount", amount);
        response.put("status", "NOT_CONFIGURED");
        response.put("message", "Razorpay integration is not configured");

        return response;
    }

    @Override
    public Map<String, Object> processRefund(
            Long paymentId,
            Double amount) {

        Map<String, Object> response = new HashMap<>();

        // Razorpay refund API integration will go here.
        response.put("success", false);
        response.put("paymentId", paymentId);
        response.put("amount", amount);
        response.put("status", "NOT_CONFIGURED");
        response.put("message", "Razorpay refund integration is not configured");

        return response;
    }
}
