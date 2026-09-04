package com.AIRevenueRecovery.controller;

import com.AIRevenueRecovery.entity.FailureReason;
import com.AIRevenueRecovery.entity.Payment;
import com.AIRevenueRecovery.repository.PaymentRepository;
import com.AIRevenueRecovery.service.AIRecoveryDecisionService;
import com.AIRevenueRecovery.service.AIRecoveryDecisionService;
import com.AIRevenueRecovery.service.AIRecoveryExecutionService;
import com.AIRevenueRecovery.service.AIRecoveryService;
import com.AIRevenueRecovery.service.RecoverySagaOrchestrator;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/ai-recovery")
public class AIRecoveryController {

    private final AIRecoveryService aiRecoveryService;
    private final AIRecoveryExecutionService aiRecoveryExecutionService;
    private final RecoverySagaOrchestrator recoverySagaOrchestrator;

    private final AIRecoveryDecisionService aiRecoveryDecisionService;
    private final PaymentRepository paymentRepository;

    public AIRecoveryController(
            AIRecoveryService aiRecoveryService,
            AIRecoveryExecutionService aiRecoveryExecutionService,
            RecoverySagaOrchestrator recoverySagaOrchestrator,
            AIRecoveryDecisionService aiRecoveryDecisionService,
            PaymentRepository paymentRepository) {

        this.aiRecoveryService = aiRecoveryService;
        this.aiRecoveryExecutionService = aiRecoveryExecutionService;
        this.recoverySagaOrchestrator = recoverySagaOrchestrator;
        this.aiRecoveryDecisionService = aiRecoveryDecisionService;
        this.paymentRepository = paymentRepository;
    }

    @GetMapping("/analyze/{failureReason}")
    public Map<String, Object> analyzeRecovery(
            @PathVariable FailureReason failureReason) {

        return aiRecoveryService.analyzeRecovery(failureReason);
    }

    @GetMapping("/payment/{paymentId}")
    public Map<String, Object> analyzePaymentRecovery(
            @PathVariable Long paymentId) {

        return aiRecoveryService.analyzePaymentRecovery(paymentId);
    }

    @GetMapping("/saga/{paymentId}")
    public Map<String, Object> getRecoverySaga(
            @PathVariable Long paymentId) {

        return recoverySagaOrchestrator.getSaga(paymentId);
    }

    @PostMapping("/saga/{paymentId}/resume")
    public Map<String, Object> resumeRecoverySaga(
            @PathVariable Long paymentId) {

        return recoverySagaOrchestrator.resumeSaga(paymentId);
    }

    @PostMapping("/execute/{paymentId}")
    public Map<String, Object> executeRecovery(
            @PathVariable Long paymentId) {

        return aiRecoveryExecutionService.executeRecovery(paymentId);
    }

    @PostMapping("/saga/{paymentId}")
    public Map<String, Object> executeRecoverySaga(
            @PathVariable Long paymentId) {

        return recoverySagaOrchestrator.executeSaga(paymentId);
    }

    @PostMapping("/decision/{paymentId}")
    public AIRecoveryDecisionService.RecoveryDecision getAIDecision(
            @PathVariable Long paymentId) {

        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() ->
                        new RuntimeException(
                                "Payment not found with ID: " + paymentId
                        ));

        return aiRecoveryDecisionService.decide(payment);
    }
}