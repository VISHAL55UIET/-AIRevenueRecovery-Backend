
package com.AIRevenueRecovery.gateway;

import java.util.Map;

public interface PaymentGateway {

    Map<String, Object> processPayment(
            Long paymentId,
            Double amount
    );

    Map<String, Object> processRefund(
            Long paymentId,
            Double amount
    );
}
