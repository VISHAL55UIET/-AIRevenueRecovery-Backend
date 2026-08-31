package com.AIRevenueRecovery.service;

import com.AIRevenueRecovery.entity.Payment;
import com.AIRevenueRecovery.entity.RecoverySaga;
import com.AIRevenueRecovery.repository.PaymentRepository;
import com.AIRevenueRecovery.repository.RecoverySagaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class RecoverySagaOrchestrator {

    private final PaymentRepository paymentRepository;
    private final RecoverySagaRepository recoverySagaRepository;
    private final AIRecoveryDecisionService aiRecoveryDecisionService;
    private final AIRecoveryExecutionService aiRecoveryExecutionService;

    public RecoverySagaOrchestrator(
            PaymentRepository paymentRepository,
            RecoverySagaRepository recoverySagaRepository,
            AIRecoveryDecisionService aiRecoveryDecisionService,
            AIRecoveryExecutionService aiRecoveryExecutionService
    ) {
        this.paymentRepository = paymentRepository;
        this.recoverySagaRepository = recoverySagaRepository;
        this.aiRecoveryDecisionService = aiRecoveryDecisionService;
        this.aiRecoveryExecutionService = aiRecoveryExecutionService;
    }
    @Transactional(readOnly = true)
    public Map<String, Object> getSaga(Long paymentId) {

        Map<String, Object> response =
                new LinkedHashMap<>();

        Optional<RecoverySaga> sagaOptional =
                recoverySagaRepository
                        .findFirstByPaymentIdOrderByIdDesc(paymentId);

        if (sagaOptional.isEmpty()) {

            response.put("status", "NOT_FOUND");
            response.put(
                    "message",
                    "No Recovery Saga found for this payment"
            );
            response.put("paymentId", paymentId);

            return response;
        }

        RecoverySaga saga =
                sagaOptional.get();

        response.put(
                "status",
                saga.getStatus()
        );

        response.put(
                "sagaId",
                saga.getSagaId()
        );

        response.put(
                "paymentId",
                saga.getPaymentId()
        );

        response.put(
                "currentStep",
                saga.getCurrentStep()
        );

        response.put(
                "action",
                saga.getAction()
        );

        response.put(
                "failureReason",
                saga.getFailureReason()
        );

        response.put(
                "errorMessage",
                saga.getErrorMessage()
        );

        response.put(
                "startedAt",
                saga.getStartedAt()
        );

        response.put(
                "updatedAt",
                saga.getUpdatedAt()
        );

        response.put(
                "completedAt",
                saga.getCompletedAt()
        );

        return response;
    }
    @Transactional
    public Map<String, Object> resumeSaga(Long paymentId) {

        Map<String, Object> response =
                new LinkedHashMap<>();
        Optional<RecoverySaga> sagaOptional =
                recoverySagaRepository.findFirstByPaymentIdOrderByIdDesc(paymentId);

        if (sagaOptional.isEmpty()) {

            response.put("status", "NOT_FOUND");
            response.put(
                    "message",
                    "No Saga found for payment"
            );
            response.put("paymentId", paymentId);

            return response;
        }

        RecoverySaga saga = sagaOptional.get();
        if ("COMPLETED".equalsIgnoreCase(saga.getStatus()
        )) {
            response.put(
                    "status",
                    "ALREADY_COMPLETED"
            );

            response.put(
                    "sagaId",
                    saga.getSagaId()
            );

            response.put(
                    "paymentId",
                    paymentId
            );

            response.put(
                    "message",
                    "Saga is already completed"
            );

            return response;
        }

        Optional<Payment> paymentOptional =
                paymentRepository.findById(paymentId);

        if (paymentOptional.isEmpty()) {

            response.put(
                    "status",
                    "FAILED"
            );

            response.put(
                    "message",
                    "Payment not found"
            );
            return response;
        }

        Payment payment = paymentOptional.get();

        try {

            String currentStep = saga.getCurrentStep();
            if ("PAYMENT_VALIDATION".equalsIgnoreCase(currentStep
            ) || "AI_DECISION".equalsIgnoreCase(currentStep
            ) || "STARTED".equalsIgnoreCase(saga.getStatus()
            )) {
                saga.setCurrentStep("AI_DECISION");
                saga.setUpdatedAt(LocalDateTime.now()
                );
                recoverySagaRepository.save(saga);
                AIRecoveryDecisionService.RecoveryDecision decision = aiRecoveryDecisionService.decide(payment);

                saga.setAction(decision.action());
                saga.setCurrentStep("AI_DECISION_COMPLETED");
                saga.setUpdatedAt(LocalDateTime.now());
                recoverySagaRepository.save(saga);
                currentStep = "AI_DECISION_COMPLETED";
            }

            if ("AI_DECISION_COMPLETED".equalsIgnoreCase(currentStep)
                    || "RECOVERY_EXECUTION".equalsIgnoreCase(currentStep)
                    || "EXECUTION_FAILED".equalsIgnoreCase(currentStep)) {

                saga.setCurrentStep("RECOVERY_EXECUTION");
                saga.setUpdatedAt(LocalDateTime.now());
                recoverySagaRepository.save(saga);

                Map<String, Object> executionResult =
                        aiRecoveryExecutionService.executeRecovery(paymentId);

                saga.setCurrentStep("RECOVERY_EXECUTED");
                saga.setUpdatedAt(LocalDateTime.now());
                recoverySagaRepository.save(saga);

                saga.setCurrentStep("COMPLETED");
                saga.setStatus("COMPLETED");

                saga.setCompletedAt(LocalDateTime.now());
                saga.setUpdatedAt(LocalDateTime.now());

                recoverySagaRepository.save(saga);

                response.put("status", "COMPLETED");
                response.put(
                        "message",
                        "Saga successfully resumed and completed"
                );
                response.put("sagaId", saga.getSagaId());
                response.put("paymentId", paymentId);
                response.put("resumedFrom", currentStep);
                response.put("action", saga.getAction());
                response.put("executionResult", executionResult);
                response.put("completedAt", saga.getCompletedAt());
                return response;
            }
            response.put("status", "FAILED");
            response.put("message", "Saga is in an unsupported state");
            response.put("currentStep", saga.getCurrentStep());
            return response;
        } catch (Exception exception) {
            saga.setStatus("FAILED");

            saga.setErrorMessage(
                    exception.getMessage()
            );

            saga.setUpdatedAt(
                    LocalDateTime.now()
            );

            recoverySagaRepository.save(saga);

            response.put("status", "FAILED"
            );

            response.put("sagaId", saga.getSagaId()
            );

            response.put("paymentId", paymentId
            );

            response.put("currentStep", saga.getCurrentStep()
            );

            response.put("error", exception.getMessage()
            );

            return response;
        }
    }

    @Transactional
    public Map<String, Object> executeSaga(Long paymentId) {

        Map<String, Object> response = new LinkedHashMap<>();
        Optional<Payment> paymentOptional =
                paymentRepository.findById(paymentId);

        if (paymentOptional.isEmpty()) {

            response.put("status", "FAILED");
            response.put("message", "Payment not found");
            response.put("paymentId", paymentId);

            return response;
        }

        Payment payment = paymentOptional.get();
        Optional<RecoverySaga> existingSaga = recoverySagaRepository.findByPaymentIdAndStatus(paymentId, "COMPLETED");
        if (existingSaga.isPresent()) {
            RecoverySaga saga = existingSaga.get();
            response.put("status", "ALREADY_COMPLETED");
            response.put("message",
                    "Recovery Saga already completed for this payment");
            response.put("sagaId", saga.getSagaId());
            response.put("paymentId", paymentId);

            return response;
        }
        RecoverySaga saga = new RecoverySaga();

        String sagaId =
                "SAGA-" + UUID.randomUUID();

        LocalDateTime now =
                LocalDateTime.now();

        saga.setSagaId(sagaId);
        saga.setPaymentId(paymentId);
        saga.setCurrentStep("PAYMENT_VALIDATION");
        saga.setStatus("STARTED");
        saga.setFailureReason(
                payment.getFailureReason() != null
                        ? payment.getFailureReason().name()
                        : null
        );
        saga.setStartedAt(now);
        saga.setUpdatedAt(now);

        recoverySagaRepository.save(saga);
        try {
            saga.setCurrentStep("AI_DECISION");
            saga.setUpdatedAt(LocalDateTime.now());

            recoverySagaRepository.save(saga);

            AIRecoveryDecisionService.RecoveryDecision decision =
                    aiRecoveryDecisionService.decide(payment);
            String action = decision.action();
            saga.setAction(action);
            saga.setCurrentStep("AI_DECISION_COMPLETED");
            saga.setUpdatedAt(LocalDateTime.now());
            recoverySagaRepository.save(saga);
            saga.setCurrentStep("RECOVERY_EXECUTION");
            saga.setUpdatedAt(LocalDateTime.now());
            recoverySagaRepository.save(saga);
            Map<String, Object> executionResult = aiRecoveryExecutionService.executeRecovery(paymentId);
            saga.setCurrentStep("RECOVERY_EXECUTED");
            saga.setUpdatedAt(LocalDateTime.now());
            recoverySagaRepository.save(saga);
            saga.setCurrentStep("COMPLETION");
            saga.setStatus("COMPLETED");
            saga.setCurrentStep("COMPLETED");
            saga.setCompletedAt(LocalDateTime.now());
            saga.setUpdatedAt(LocalDateTime.now());

            recoverySagaRepository.save(saga);
            response.put("status", "COMPLETED");
            response.put("sagaId", saga.getSagaId());
            response.put("paymentId", paymentId);
            response.put("failureReason",
                    saga.getFailureReason());
            response.put("action", saga.getAction());
            response.put("executionResult",
                    executionResult);
            response.put("startedAt",
                    saga.getStartedAt());
            response.put("completedAt",
                    saga.getCompletedAt());

            return response;

        } catch (Exception exception) {
            saga.setStatus("FAILED");
            saga.setCurrentStep("FAILED");
            saga.setErrorMessage(
                    exception.getMessage()
            );
            saga.setUpdatedAt(LocalDateTime.now());

            recoverySagaRepository.save(saga);

            response.put("status", "FAILED");
            response.put("sagaId", saga.getSagaId());
            response.put("paymentId", paymentId);
            response.put("currentStep",
                    saga.getCurrentStep());
            response.put("error",
                    exception.getMessage());

            return response;
        }
    }
}