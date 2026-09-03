package com.AIRevenueRecovery.controller;

import com.AIRevenueRecovery.service.PaymentGatewayService;
import com.AIRevenueRecovery.service.RazorpayWebhookService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/webhooks/razorpay")
public class RazorpayWebhookController {

    private final PaymentGatewayService paymentGatewayService;
    private final RazorpayWebhookService razorpayWebhookService;
    public RazorpayWebhookController(PaymentGatewayService paymentGatewayService, RazorpayWebhookService razorpayWebhookService) {
        this.paymentGatewayService = paymentGatewayService;
        this.razorpayWebhookService = razorpayWebhookService;
    }

    @PostMapping
    public ResponseEntity<Void> handleWebhook(
            @RequestHeader(value = "X-Razorpay-Signature", required = false
            )
            String signature,
            @RequestBody String payload) {
        boolean verified = paymentGatewayService.verifyWebhookSignature(
                        payload, signature);
        if (!verified) {
            return ResponseEntity.badRequest().build();
        }
        razorpayWebhookService.processWebhook(payload);
        return ResponseEntity.ok().build();
    }
}