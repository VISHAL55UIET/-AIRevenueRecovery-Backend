package com.AIRevenueRecovery.controller;

import com.AIRevenueRecovery.dto.PaymentRequest;
import com.AIRevenueRecovery.dto.RazorpayOrderResponse;
import com.AIRevenueRecovery.dto.RazorpayPaymentVerificationRequest;
import com.AIRevenueRecovery.entity.FailureReason;
import com.AIRevenueRecovery.entity.Payment;
import com.AIRevenueRecovery.entity.PaymentStatus;
import com.AIRevenueRecovery.entity.IdempotencyRequest;
import com.AIRevenueRecovery.service.IdempotencyService;
import com.AIRevenueRecovery.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;
    private final IdempotencyService idempotencyService;

    public PaymentController(
            PaymentService paymentService,
            IdempotencyService idempotencyService) {

        this.paymentService = paymentService;
        this.idempotencyService = idempotencyService;
    }

    @PostMapping
    public Payment createPayment(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody PaymentRequest request) {

        IdempotencyRequest existingRequest =
                idempotencyService.getByKey(idempotencyKey);

        if (existingRequest != null) {

            throw new RuntimeException(
                    "Duplicate request. Idempotency-Key already exists: "
                            + idempotencyKey
            );
        }

        Payment payment = new Payment();

        payment.setPaymentId(
                request.getPaymentId()
        );

        payment.setCustomerId(
                request.getCustomerId()
        );

        payment.setAmount(
                request.getAmount()
        );

        payment.setCurrency(
                request.getCurrency()
        );

        payment.setStatus(
                PaymentStatus.valueOf(
                        request.getStatus().toUpperCase()
                )
        );

        if (request.getFailureReason() != null
                && !request.getFailureReason().isBlank()) {

            payment.setFailureReason(
                    FailureReason.valueOf(
                            request.getFailureReason().toUpperCase()
                    )
            );
        }

        IdempotencyRequest idempotencyRequest =
                idempotencyService.createRequest(
                        idempotencyKey,
                        "CREATE_PAYMENT",
                        null
                );

        Payment savedPayment =
                paymentService.createPayment(payment);

        idempotencyService.attachPayment(
                idempotencyKey,
                savedPayment
        );

        return savedPayment;
    }

    @GetMapping
    public List<Payment> getAllPayments() {

        return paymentService.getAllPayments();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Payment> getPaymentById(
            @PathVariable Long id) {

        return ResponseEntity.ok(
                paymentService.getPaymentById(id)
        );
    }

    @PutMapping("/{id}")
    public ResponseEntity<Payment> updatePayment(
            @PathVariable Long id,
            @RequestBody Payment payment) {

        paymentService.getPaymentById(id);

        payment.setId(id);

        return ResponseEntity.ok(
                paymentService.updatePayment(payment)
        );
    }

    @GetMapping("/recent")
    public List<Payment> getRecentPayments() {

        return paymentService.getRecentPayments();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePayment(
            @PathVariable Long id) {

        paymentService.getPaymentById(id);

        paymentService.deletePayment(id);

        return ResponseEntity.noContent().build();
    }
    @PostMapping("/{paymentId}/razorpay-order")
    public RazorpayOrderResponse createRazorpayOrder(
            @PathVariable Long paymentId) {

        return paymentService.createRazorpayOrder(
                paymentId
        );
    }
    @PostMapping("/{paymentId}/razorpay-verify")
    public ResponseEntity<Payment> verifyRazorpayPayment(
            @PathVariable Long paymentId,
            @RequestBody RazorpayPaymentVerificationRequest request) {

        Payment payment =
                paymentService.verifyRazorpayPayment(
                        paymentId,
                        request
                );

        return ResponseEntity.ok(payment);
    }
}