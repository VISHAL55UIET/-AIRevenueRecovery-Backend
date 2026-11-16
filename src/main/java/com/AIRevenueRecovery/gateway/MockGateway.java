
package com.AIRevenueRecovery.gateway;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
public class MockGateway {

    /**
     * Simulate payment processing.
     */
    public Map<String, Object> processPayment(
            Long paymentId,
            Double amount) {

        boolean success = Math.random() > 0.2;

        return Map.of(
                "success", success,
                "paymentId", paymentId,
                "amount", amount,
                "transactionId", UUID.randomUUID().toString(),
                "status", success ? "SUCCESS" : "FAILED",
                "gateway", "MOCK_GATEWAY"
        );
    }

    /**
     * Simulate refund processing.
     */
    public Map<String, Object> processRefund(
            Long paymentId,
            Double amount) {

        return Map.of(
                "success", true,
                "paymentId", paymentId,
                "amount", amount,
                "refundId", UUID.randomUUID().toString(),
                "status", "REFUNDED",
                "gateway", "MOCK_GATEWAY"
        );
    }
}
