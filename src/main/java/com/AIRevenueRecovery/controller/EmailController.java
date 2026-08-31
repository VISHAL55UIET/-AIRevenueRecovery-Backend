package com.AIRevenueRecovery.controller;

import com.AIRevenueRecovery.entity.Payment;
import com.AIRevenueRecovery.exception.PaymentNotFoundException;
import com.AIRevenueRecovery.repository.PaymentRepository;
import com.AIRevenueRecovery.service.AIRecoveryDecisionService;
import com.AIRevenueRecovery.service.EmailService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/email")
public class EmailController {
    private final EmailService emailService;
    private final PaymentRepository paymentRepository;
    private final AIRecoveryDecisionService aiRecoveryDecisionService;
    public EmailController(
            EmailService emailService,
            PaymentRepository paymentRepository,
            AIRecoveryDecisionService aiRecoveryDecisionService) {
        this.emailService = emailService;
        this.paymentRepository = paymentRepository;
        this.aiRecoveryDecisionService = aiRecoveryDecisionService;
    }

    @PostMapping("/send/{paymentId}")
    public String sendRecoveryEmail(
            @PathVariable Long paymentId) {

        Payment payment =
                paymentRepository.findById(paymentId)
                        .orElseThrow(() ->
                                new PaymentNotFoundException(
                                        "Payment not found with ID: "
                                                + paymentId
                                ));

        AIRecoveryDecisionService.RecoveryDecision decision =
                aiRecoveryDecisionService.decide(payment);

        emailService.sendPaymentRecoveryEmail(
                payment,
                decision.recommendation()
        );

        return "Recovery email sent successfully";
    }
}