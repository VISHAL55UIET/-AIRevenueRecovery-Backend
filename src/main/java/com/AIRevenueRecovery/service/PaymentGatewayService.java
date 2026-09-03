package com.AIRevenueRecovery.service;

import com.AIRevenueRecovery.entity.Payment;
import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import com.razorpay.Utils;
import com.razorpay.Refund;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class PaymentGatewayService {
    private static final Logger log = LoggerFactory.getLogger(PaymentGatewayService.class);
    private final RazorpayClient razorpayClient;
    private final String razorpayKeySecret;
    private final String razorpayWebhookSecret;
    public PaymentGatewayService(
            @Value("${razorpay.key-id}") String keyId,
            @Value("${razorpay.key-secret}") String keySecret,
            @Value("${razorpay.webhook-secret}") String webhookSecret) {
        try {
            this.razorpayClient = new RazorpayClient(keyId, keySecret);
            this.razorpayKeySecret = keySecret;
            this.razorpayWebhookSecret = webhookSecret;
        } catch (RazorpayException exception) {
            throw new IllegalStateException(
                    "Failed to initialize Razorpay client", exception);
        }
    }

    @CircuitBreaker(
            name = "razorpay",
            fallbackMethod = "createOrderFallback"
    )
    @Retry(name = "razorpay")
    public Order createOrder(
            Payment payment,
            int attemptNumber) {

        validatePayment(payment, attemptNumber);

        try {

            long amountInPaise =
                    BigDecimal.valueOf(payment.getAmount())
                            .multiply(BigDecimal.valueOf(100))
                            .setScale(0, RoundingMode.HALF_UP)
                            .longValueExact();

            if (amountInPaise <= 0) {

                throw new IllegalArgumentException(
                        "Amount in paise must be greater than zero"
                );
            }

            JSONObject orderRequest =
                    new JSONObject();

            orderRequest.put(
                    "amount",
                    amountInPaise
            );

            orderRequest.put(
                    "currency",
                    payment.getCurrency()
            );

            orderRequest.put(
                    "receipt",
                    payment.getPaymentId()
            );

            JSONObject notes =
                    new JSONObject();

            notes.put(
                    "payment_id",
                    payment.getPaymentId()
            );

            notes.put(
                    "attempt_number",
                    attemptNumber
            );

            orderRequest.put(
                    "notes",
                    notes
            );

            Order order =
                    razorpayClient.orders.create(
                            orderRequest
                    );

            String razorpayOrderId =
                    order.get("id");

            if (razorpayOrderId == null
                    || razorpayOrderId.isBlank()) {

                throw new IllegalStateException(
                        "Razorpay did not return an order ID"
                );
            }

            log.info(
                    "Razorpay order created successfully. " +
                            "paymentId={}, attemptNumber={}, " +
                            "razorpayOrderId={}, amount={}, currency={}",
                    payment.getPaymentId(),
                    attemptNumber,
                    razorpayOrderId,
                    amountInPaise,
                    payment.getCurrency()
            );

            return order;

        } catch (RazorpayException exception) {

            log.error(
                    "Razorpay order creation failed. " +
                            "paymentId={}, attemptNumber={}, reason={}",
                    payment.getPaymentId(),
                    attemptNumber,
                    exception.getMessage(),
                    exception
            );

            throw new IllegalStateException(
                    "Razorpay order creation failed",
                    exception
            );
        }
    }


    private void validatePayment(
            Payment payment,
            int attemptNumber) {

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

        if (payment.getAmount() == null) {

            throw new IllegalArgumentException(
                    "Payment amount is required"
            );
        }

        if (payment.getAmount() <= 0) {

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

        if (attemptNumber <= 0) {

            throw new IllegalArgumentException(
                    "Attempt number must be greater than zero"
            );
        }
    }


    private Order createOrderFallback(
            Payment payment,
            int attemptNumber,
            Throwable throwable) {

        log.error(
                "Razorpay circuit breaker fallback. " +
                        "paymentId={}, attemptNumber={}, reason={}",
                payment != null
                        ? payment.getPaymentId()
                        : "UNKNOWN",
                attemptNumber,
                throwable.getMessage(),
                throwable
        );

        throw new IllegalStateException(
                "Razorpay payment service is temporarily unavailable. "
                        + "Please try again later.",
                throwable
        );
    }


    public boolean verifyWebhookSignature(
            String payload,
            String webhookSignature) {

        if (payload == null || payload.isBlank()) {

            throw new IllegalArgumentException(
                    "Webhook payload is required"
            );
        }

        if (webhookSignature == null
                || webhookSignature.isBlank()) {

            throw new IllegalArgumentException(
                    "Webhook signature is required"
            );
        }

        if (razorpayWebhookSecret == null
                || razorpayWebhookSecret.isBlank()) {

            throw new IllegalStateException(
                    "Razorpay webhook secret is not configured"
            );
        }

        try {

            Utils.verifyWebhookSignature(
                    payload,
                    webhookSignature,
                    razorpayWebhookSecret
            );

            log.info(
                    "Razorpay webhook signature verified"
            );

            return true;

        } catch (RazorpayException exception) {

            log.warn(
                    "Razorpay webhook signature verification failed"
            );

            return false;
        }
    }

    public boolean processPayment(
            Payment payment,
            int attemptNumber) {

        if (payment == null) {

            throw new IllegalArgumentException(
                    "Payment is required"
            );
        }

        try {

            Order order =
                    createOrder(
                            payment,
                            attemptNumber
                    );

            String orderId =
                    order.get("id");

            String orderStatus =
                    order.get("status");

            log.info(
                    "Razorpay recovery order initiated. " +
                            "paymentId={}, attemptNumber={}, " +
                            "razorpayOrderId={}, status={}",
                    payment.getPaymentId(),
                    attemptNumber,
                    orderId,
                    orderStatus
            );
            return false;

        } catch (Exception exception) {

            log.error(
                    "Recovery payment initiation failed. " +
                            "paymentId={}, attemptNumber={}, reason={}",
                    payment.getPaymentId(),
                    attemptNumber,
                    exception.getMessage(),
                    exception
            );

            return false;
        }
    }

    @CircuitBreaker(
            name = "razorpay",
            fallbackMethod = "verifyPaymentFallback"
    )
    public boolean verifyPayment(
            String razorpayOrderId,
            String razorpayPaymentId,
            String razorpaySignature) {

        if (razorpayOrderId == null
                || razorpayOrderId.isBlank()) {

            throw new IllegalArgumentException(
                    "Razorpay order ID is required"
            );
        }

        if (razorpayPaymentId == null
                || razorpayPaymentId.isBlank()) {

            throw new IllegalArgumentException(
                    "Razorpay payment ID is required"
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

            log.info(
                    "Razorpay payment signature verified. " +
                            "orderId={}, paymentId={}",
                    razorpayOrderId,
                    razorpayPaymentId
            );

            return true;

        } catch (RazorpayException exception) {

            log.warn(
                    "Razorpay signature verification failed. " +
                            "orderId={}, paymentId={}, reason={}",
                    razorpayOrderId,
                    razorpayPaymentId,
                    exception.getMessage()
            );

            return false;
        }
    }


    @CircuitBreaker(
            name = "razorpay",
            fallbackMethod = "capturePaymentFallback"
    )
    @Retry(name = "razorpay")
    public boolean capturePayment(
            String razorpayPaymentId,
            long amountInPaise) {

        if (razorpayPaymentId == null
                || razorpayPaymentId.isBlank()) {

            throw new IllegalArgumentException(
                    "Razorpay payment ID is required"
            );
        }

        if (amountInPaise <= 0) {

            throw new IllegalArgumentException(
                    "Capture amount must be greater than zero"
            );
        }

        try {

            JSONObject captureRequest =
                    new JSONObject();

            captureRequest.put(
                    "amount",
                    amountInPaise
            );

            com.razorpay.Payment razorpayPayment =
                    razorpayClient.payments.capture(
                            razorpayPaymentId,
                            captureRequest
                    );

            if (razorpayPayment == null) {
                throw new IllegalStateException(
                        "Razorpay did not return capture response"
                );
            }

            String status =
                    razorpayPayment.get("status");

            log.info(
                    "Razorpay payment captured successfully. " +
                            "razorpayPaymentId={}, amount={}, status={}",
                    razorpayPaymentId,
                    amountInPaise,
                    status
            );

            return "captured".equalsIgnoreCase(status);

        } catch (RazorpayException exception) {

            log.error(
                    "Razorpay payment capture failed. " +
                            "razorpayPaymentId={}, amount={}, reason={}",
                    razorpayPaymentId,
                    amountInPaise,
                    exception.getMessage(),
                    exception
            );

            throw new IllegalStateException(
                    "Razorpay payment capture failed",
                    exception
            );
        }
    }


// ============================================================
// CAPTURE PAYMENT FALLBACK
// ============================================================

    private boolean capturePaymentFallback(
            String razorpayPaymentId,
            long amountInPaise,
            Throwable throwable) {

        log.error(
                "Razorpay capture circuit breaker fallback. " +
                        "razorpayPaymentId={}, amount={}, reason={}",
                razorpayPaymentId,
                amountInPaise,
                throwable.getMessage(),
                throwable
        );

        throw new IllegalStateException(
                "Razorpay capture service is temporarily unavailable. "
                        + "Please try again later.",
                throwable
        );
    }
    @Retry(name = "razorpay")
    public com.razorpay.Payment fetchPayment(
            String razorpayPaymentId) {

        if (razorpayPaymentId == null
                || razorpayPaymentId.isBlank()) {

            throw new IllegalArgumentException(
                    "Razorpay payment ID is required"
            );
        }

        try {

            com.razorpay.Payment razorpayPayment =
                    razorpayClient.payments.fetch(
                            razorpayPaymentId
                    );

            if (razorpayPayment == null) {
                throw new IllegalStateException(
                        "Razorpay payment not found"
                );
            }

            log.info(
                    "Razorpay payment fetched successfully. " +
                            "razorpayPaymentId={}, status={}",
                    razorpayPaymentId,
                    razorpayPayment.get("status")
            );

            return razorpayPayment;

        } catch (RazorpayException exception) {

            log.error(
                    "Failed to fetch Razorpay payment. " +
                            "razorpayPaymentId={}, reason={}",
                    razorpayPaymentId,
                    exception.getMessage(),
                    exception
            );

            throw new IllegalStateException(
                    "Failed to fetch Razorpay payment",
                    exception
            );
        }
    }


// ============================================================
// FETCH PAYMENT FALLBACK
// ============================================================

    private com.razorpay.Payment fetchPaymentFallback(
            String razorpayPaymentId,
            Throwable throwable) {

        log.error(
                "Razorpay fetch payment circuit breaker fallback. " +
                        "razorpayPaymentId={}, reason={}",
                razorpayPaymentId,
                throwable.getMessage(),
                throwable
        );

        throw new IllegalStateException(
                "Razorpay payment service is temporarily unavailable. "
                        + "Please try again later.",
                throwable
        );
    }
    private boolean verifyPaymentFallback(
            String razorpayOrderId,
            String razorpayPaymentId,
            String razorpaySignature,
            Throwable throwable) {

        log.error(
                "Razorpay verification fallback triggered. " +
                        "orderId={}, paymentId={}, reason={}",
                razorpayOrderId,
                razorpayPaymentId,
                throwable.getMessage(),
                throwable
        );

        return false;
    }
    // ============================================================
// REFUND PAYMENT
// ============================================================

    @CircuitBreaker(
            name = "razorpay",
            fallbackMethod = "refundPaymentFallback"
    )
    @Retry(name = "razorpay")
    public boolean refundPayment(
            String razorpayPaymentId,
            long amountInPaise) {

        if (razorpayPaymentId == null
                || razorpayPaymentId.isBlank()) {

            throw new IllegalArgumentException(
                    "Razorpay payment ID is required"
            );
        }

        if (amountInPaise <= 0) {

            throw new IllegalArgumentException(
                    "Refund amount must be greater than zero"
            );
        }

        try {

            JSONObject refundRequest =
                    new JSONObject();

            refundRequest.put(
                    "amount",
                    amountInPaise
            );

            Refund refund =
                    razorpayClient.payments.refund(
                            razorpayPaymentId,
                            refundRequest
                    );

            String refundId =
                    refund.get("id");

            if (refundId == null
                    || refundId.isBlank()) {

                throw new IllegalStateException(
                        "Razorpay did not return refund ID"
                );
            }

            log.info(
                    "Razorpay refund created successfully. " +
                            "razorpayPaymentId={}, refundId={}, amount={}",
                    razorpayPaymentId,
                    refundId,
                    amountInPaise
            );

            return true;

        } catch (RazorpayException exception) {

            log.error(
                    "Razorpay refund failed. " +
                            "razorpayPaymentId={}, amount={}, reason={}",
                    razorpayPaymentId,
                    amountInPaise,
                    exception.getMessage(),
                    exception
            );

            throw new IllegalStateException(
                    "Razorpay refund failed",
                    exception
            );
        }
    }


// ============================================================
// REFUND FALLBACK
// ============================================================

    private boolean refundPaymentFallback(
            String razorpayPaymentId,
            long amountInPaise,
            Throwable throwable) {

        log.error(
                "Razorpay refund circuit breaker fallback. " +
                        "razorpayPaymentId={}, amount={}, reason={}",
                razorpayPaymentId,
                amountInPaise,
                throwable.getMessage(),
                throwable
        );

        throw new IllegalStateException(
                "Razorpay refund service is temporarily unavailable. "
                        + "Please try again later.",
                throwable
        );
    }
}