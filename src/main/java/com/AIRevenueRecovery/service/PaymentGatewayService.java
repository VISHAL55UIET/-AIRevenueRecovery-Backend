package com.AIRevenueRecovery.service;

import com.AIRevenueRecovery.entity.Payment;
import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import com.razorpay.Utils;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class PaymentGatewayService {

    private final RazorpayClient razorpayClient;
    private final String razorpayKeySecret;
    public PaymentGatewayService(
            @Value("${razorpay.key-id}") String keyId, @Value("${razorpay.key-secret}") String keySecret
    ) {
        try {
            this.razorpayClient = new RazorpayClient(keyId, keySecret);
            this.razorpayKeySecret = keySecret;
        } catch (RazorpayException exception) {
            throw new IllegalStateException(
                    "Failed to initialize Razorpay client",
                    exception
            );
        }
    }
    public Order createOrder(Payment payment, int attemptNumber) {
        if (payment == null) {
            throw new IllegalArgumentException("Payment is required");
        }
        if (payment.getAmount() == null) {
            throw new IllegalArgumentException("Payment amount is required");
        }
        if (payment.getAmount() <= 0) {
            throw new IllegalArgumentException("Payment amount must be greater than zero");
        }
        if (payment.getCurrency() == null || payment.getCurrency().isBlank()) {
            throw new IllegalArgumentException("Payment currency is required"
            );
        }

        if (attemptNumber <= 0) {

            throw new IllegalArgumentException(
                    "Attempt number must be greater than zero"
            );
        }
        try {
            long amountInPaise = Math.round(payment.getAmount() * 100);
            if (amountInPaise <= 0) {throw new IllegalArgumentException(
                        "Amount in paise must be greater than zero");
            }
            JSONObject orderRequest = new JSONObject();
            orderRequest.put("amount", amountInPaise);
            orderRequest.put("currency", payment.getCurrency());
            orderRequest.put("receipt", payment.getPaymentId());
            JSONObject notes = new JSONObject();
            notes.put("payment_id", payment.getPaymentId());
            notes.put("attempt_number", attemptNumber);
            orderRequest.put("notes", notes);
            Order order = razorpayClient.orders.create(orderRequest);
            System.out.println("=================================");
            System.out.println("Razorpay order created");
            System.out.println("Payment ID: " + payment.getPaymentId());
            System.out.println("Attempt Number: " + attemptNumber);
            System.out.println("Amount: " + payment.getAmount() + " " + payment.getCurrency());
            System.out.println("Amount in Paise: " + amountInPaise);
            System.out.println("Razorpay Order ID: " + order.get("id"));
            System.out.println("Razorpay Order Status: "+ order.get("status"));
            System.out.println("=================================");
            return order;
        } catch (RazorpayException exception) {
            throw new IllegalStateException("Razorpay order creation failed: " + exception.getMessage(), exception);
        }
    }
    public boolean processPayment(Payment payment, int attemptNumber) {
        if (payment == null) {throw new IllegalArgumentException("Payment is required");
        }
        try {
            Order order = createOrder(payment, attemptNumber);

            String orderId = order.get("id");
            String orderStatus = order.get("status");
            System.out.println(
                    "================================="
            );

            System.out.println("Recovery retry initiated"
            );
            System.out.println("Payment ID: " + payment.getPaymentId()
            );
            System.out.println("Attempt Number: " + attemptNumber
            );
            System.out.println("Razorpay Order ID: " + orderId);
            System.out.println("Razorpay Order Status: " + orderStatus);
            System.out.println("Actual customer payment requires " + "Razorpay Checkout/payment flow.");
            System.out.println("=================================");
            return false;
        } catch (Exception exception) {
            System.err.println("Recovery retry failed: " + exception.getMessage());
            return false;
        }
    }
    public boolean verifyPayment(String razorpayOrderId, String razorpayPaymentId, String razorpaySignature
    ) {
        if (razorpayOrderId == null || razorpayOrderId.isBlank()) {
            throw new IllegalArgumentException("Razorpay order ID is required"
            );
        }
        if (razorpayPaymentId == null || razorpayPaymentId.isBlank()) {
            throw new IllegalArgumentException("Razorpay payment ID is required"
            );
        }

        if (razorpaySignature == null
                || razorpaySignature.isBlank()) {

            throw new IllegalArgumentException(
                    "Razorpay signature is required"
            );
        }

        try {

            JSONObject attributes =
                    new JSONObject();

            attributes.put(
                    "razorpay_order_id",
                    razorpayOrderId
            );

            attributes.put(
                    "razorpay_payment_id",
                    razorpayPaymentId
            );

            attributes.put(
                    "razorpay_signature",
                    razorpaySignature
            );

            Utils.verifyPaymentSignature(
                    attributes,
                    razorpayKeySecret
            );

            System.out.println(
                    "Razorpay payment signature verified successfully"
            );

            return true;

        } catch (RazorpayException exception) {

            System.err.println(
                    "Razorpay signature verification failed: "
                            + exception.getMessage()
            );

            return false;
        }
    }
}