package com.AIRevenueRecovery.controller;

import com.AIRevenueRecovery.entity.Payment;
import com.AIRevenueRecovery.entity.RecoveryToken;
import com.AIRevenueRecovery.service.RecoveryTokenService;
import com.AIRevenueRecovery.repository.PaymentRepository;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/recovery-links")
public class RecoveryTokenController {

    private final RecoveryTokenService recoveryTokenService;
    private final PaymentRepository paymentRepository;

    public RecoveryTokenController(
            RecoveryTokenService recoveryTokenService,
            PaymentRepository paymentRepository) {

        this.recoveryTokenService =
                recoveryTokenService;

        this.paymentRepository =
                paymentRepository;
    }

    /*
     * Generate recovery link
     */
    @PostMapping("/payment/{paymentId}")
    public Map<String, Object> createRecoveryLink(
            @PathVariable Long paymentId) {

        Payment payment =
                paymentRepository.findById(paymentId)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Payment not found with ID: "
                                                + paymentId
                                ));

        RecoveryToken recoveryToken =
                recoveryTokenService.createToken(
                        payment
                );

        Map<String, Object> response =
                new LinkedHashMap<>();

        response.put(
                "paymentId",
                payment.getId()
        );

        response.put(
                "token",
                recoveryToken.getToken()
        );

        response.put(
                "expiresAt",
                recoveryToken.getExpiresAt()
        );

        response.put(
                "recoveryUrl",
                "/recover/"
                        + recoveryToken.getToken()
        );

        return response;
    }

    /*
     * Get payment information using recovery token
     */
    @GetMapping("/{token}")
    public Map<String, Object> getRecoveryDetails(
            @PathVariable String token) {

        RecoveryToken recoveryToken =
                recoveryTokenService.getValidToken(
                        token
                );

        Payment payment =
                recoveryToken.getPayment();

        Map<String, Object> response =
                new LinkedHashMap<>();

        response.put(
                "paymentId",
                payment.getId()
        );

        response.put(
                "amount",
                payment.getAmount()
        );

        response.put(
                "currency",
                payment.getCurrency()
        );

        response.put(
                "status",
                payment.getStatus()
        );
        response.put(
                "failureReason",
                payment.getFailureReason()
        );
        response.put(
                "expiresAt",
                recoveryToken.getExpiresAt()
        );
        return response;
    }
}