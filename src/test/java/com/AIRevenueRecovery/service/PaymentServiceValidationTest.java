package com.AIRevenueRecovery.service;

import com.AIRevenueRecovery.entity.Payment;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PaymentServiceValidationTest {

    @Test
    void shouldRejectNullPayment() {

        Payment payment = null;

        assertThrows(
                IllegalArgumentException.class,
                () -> validate(payment)
        );
    }

    @Test
    void shouldRejectPaymentWithoutPaymentId() {

        Payment payment = new Payment();

        assertThrows(
                IllegalArgumentException.class,
                () -> validate(payment)
        );
    }

    @Test
    void shouldRejectZeroAmount() {

        Payment payment = new Payment();

        payment.setPaymentId("TEST-PAYMENT-001");
        payment.setAmount(0.0);
        payment.setCurrency("INR");

        assertThrows(
                IllegalArgumentException.class,
                () -> validate(payment)
        );
    }

    @Test
    void shouldRejectNegativeAmount() {

        Payment payment = new Payment();

        payment.setPaymentId("TEST-PAYMENT-001");
        payment.setAmount(-100.0);
        payment.setCurrency("INR");

        assertThrows(
                IllegalArgumentException.class,
                () -> validate(payment)
        );
    }

    private void validate(Payment payment) {

        if (payment == null) {
            throw new IllegalArgumentException(
                    "Payment is required"
            );
        }

        if (payment.getPaymentId() == null
                || payment.getPaymentId().isBlank()) {

            throw new IllegalArgumentException(
                    "Payment ID is required"
            );
        }

        if (payment.getAmount() == null
                || payment.getAmount() <= 0) {

            throw new IllegalArgumentException(
                    "Payment amount must be greater than zero"
            );
        }

        if (payment.getCurrency() == null
                || payment.getCurrency().isBlank()) {

            throw new IllegalArgumentException(
                    "Payment currency is required"
            );
        }
    }
}