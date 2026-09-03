package com.AIRevenueRecovery.controller;

import com.AIRevenueRecovery.entity.FailureReason;
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
    public AIRecoveryController(
            AIRecoveryService aiRecoveryService, AIRecoveryExecutionService aiRecoveryExecutionService,
            RecoverySagaOrchestrator recoverySagaOrchestrator) {
        this.aiRecoveryService = aiRecoveryService;
        this.aiRecoveryExecutionService = aiRecoveryExecutionService;
        this.recoverySagaOrchestrator = recoverySagaOrchestrator;
    }
    @GetMapping("/analyze/{failureReason}")
    public Map<String, Object> analyzeRecovery(@PathVariable FailureReason failureReason) {
        return aiRecoveryService.analyzeRecovery(failureReason);
    }
    @GetMapping("/payment/{paymentId}")
    public Map<String, Object> analyzePaymentRecovery(
            @PathVariable Long paymentId) {
        return aiRecoveryService.analyzePaymentRecovery(paymentId);
    }
    @GetMapping("/saga/{paymentId}")
    public Map<String, Object> getRecoverySaga(@PathVariable Long paymentId) {
        return recoverySagaOrchestrator
                .getSaga(paymentId);
    }
    @PostMapping("/saga/{paymentId}/resume")
    public Map<String, Object> resumeRecoverySaga(@PathVariable Long paymentId) {
        return recoverySagaOrchestrator.resumeSaga(paymentId);
    }
    @PostMapping("/execute/{paymentId}")
    public Map<String, Object> executeRecovery(@PathVariable Long paymentId) {
        return aiRecoveryExecutionService.executeRecovery(paymentId);
    }
    @PostMapping("/saga/{paymentId}")
    public Map<String, Object> executeRecoverySaga(
            @PathVariable Long paymentId
    ) {

        return recoverySagaOrchestrator
                .executeSaga(paymentId);
    }
}