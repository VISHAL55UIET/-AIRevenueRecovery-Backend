package com.AIRevenueRecovery.service;

import com.AIRevenueRecovery.entity.Payment;
import com.AIRevenueRecovery.entity.RecoverySaga;
import com.AIRevenueRecovery.repository.PaymentRepository;
import com.AIRevenueRecovery.repository.RecoverySagaRepository;
import com.AIRevenueRecovery.saga.RecoverySagaState;
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
    private final OutboxEventService outboxEventService;
    public RecoverySagaOrchestrator(
            PaymentRepository paymentRepository,
            RecoverySagaRepository recoverySagaRepository,
            AIRecoveryDecisionService aiRecoveryDecisionService,
            AIRecoveryExecutionService aiRecoveryExecutionService,
            OutboxEventService outboxEventService
    ) {
        this.paymentRepository = paymentRepository;
        this.recoverySagaRepository = recoverySagaRepository;
        this.aiRecoveryDecisionService = aiRecoveryDecisionService;
        this.aiRecoveryExecutionService = aiRecoveryExecutionService;
        this.outboxEventService = outboxEventService;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getSaga(Long paymentId) {

        Map<String, Object> response = new LinkedHashMap<>();

        Optional<RecoverySaga> sagaOptional = recoverySagaRepository.findFirstByPaymentIdOrderByIdDesc(paymentId);

        if (sagaOptional.isEmpty()) {
            response.put("status", "NOT_FOUND");
            response.put("message", "No Recovery Saga found for this payment"
            );
            response.put("paymentId", paymentId);
            return response;
        }

        RecoverySaga saga = sagaOptional.get();

        response.put("status", saga.getStatus());
        response.put("sagaId", saga.getSagaId());
        response.put("paymentId", saga.getPaymentId());
        response.put("currentStep", saga.getCurrentStep());
        response.put("action", saga.getAction());
        response.put("failureReason", saga.getFailureReason());
        response.put("errorMessage", saga.getErrorMessage());
        response.put("startedAt", saga.getStartedAt());
        response.put("updatedAt", saga.getUpdatedAt());
        response.put("completedAt", saga.getCompletedAt());

        return response;
    }

    @Transactional
    public Map<String, Object> resumeSaga(Long paymentId) {
        Map<String, Object> response = new LinkedHashMap<>();
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

        if (RecoverySagaState.COMPLETED.name()
                .equalsIgnoreCase(saga.getCurrentStep())) {

            response.put(
                    "status",
                    "ALREADY_COMPLETED"
            );
            response.put("sagaId", saga.getSagaId());
            response.put("paymentId", paymentId);
            response.put("message",
                    "Saga is already completed"
            );

            return response;
        }
        Optional<Payment> paymentOptional = paymentRepository.findById(paymentId);

        if (paymentOptional.isEmpty()) {
            response.put("status", "FAILED");
            response.put("message", "Payment not found");
            response.put("paymentId", paymentId);
            return response;
        }
        Payment payment = paymentOptional.get();

        try {

            RecoverySagaState currentState =
                    parseState(saga.getCurrentStep());

            String resumedFrom = currentState.name();

            if (currentState == RecoverySagaState.STARTED
                    || currentState == RecoverySagaState.PAYMENT_VALIDATION
                    || currentState == RecoverySagaState.FAILED) {

                transition(saga,
                        RecoverySagaState.AI_DECISION
                );

                recoverySagaRepository.save(saga);
                AIRecoveryDecisionService.RecoveryDecision decision = aiRecoveryDecisionService.decide(payment);

                saga.setAction(decision.action());
                transition(saga,
                        RecoverySagaState.AI_DECISION_COMPLETED
                );
                recoverySagaRepository.save(saga);
                currentState =
                        RecoverySagaState.AI_DECISION_COMPLETED;
            }
            if (currentState == RecoverySagaState.AI_DECISION_COMPLETED
                    || currentState == RecoverySagaState.RECOVERY_EXECUTION
                    || currentState == RecoverySagaState.RECOVERY_EXECUTED) {

                if (currentState != RecoverySagaState.RECOVERY_EXECUTION) {

                    transition(
                            saga,
                            RecoverySagaState.RECOVERY_EXECUTION
                    );

                    recoverySagaRepository.save(saga);
                }

                Map<String, Object> executionResult = aiRecoveryExecutionService.executeRecovery(paymentId);

                transition(saga,
                        RecoverySagaState.RECOVERY_EXECUTED
                );

                recoverySagaRepository.save(saga);
                transition(saga,
                        RecoverySagaState.COMPLETION
                );
                recoverySagaRepository.save(saga);
                transition(
                        saga, RecoverySagaState.COMPLETED
                );
                recoverySagaRepository.save(saga);
                response.put("status", "COMPLETED");
                response.put(
                        "message",
                        "Saga successfully resumed and completed"
                );
                response.put("sagaId", saga.getSagaId());
                response.put("paymentId", paymentId);
                response.put("resumedFrom", resumedFrom);
                response.put("action", saga.getAction());
                response.put(
                        "executionResult",
                        executionResult
                );
                response.put(
                        "completedAt",
                        saga.getCompletedAt()
                );

                return response;
            }

            response.put("status", "FAILED");
            response.put(
                    "message",
                    "Saga is in an unsupported state"
            );
            response.put(
                    "currentStep",
                    saga.getCurrentStep()
            );

            return response;

        } catch (Exception exception) {

            markSagaFailed(
                    saga,
                    exception
            );

            recoverySagaRepository.save(saga);

            response.put("status", "FAILED");
            response.put("sagaId", saga.getSagaId());
            response.put("paymentId", paymentId);
            response.put(
                    "currentStep",
                    saga.getCurrentStep()
            );
            response.put(
                    "error",
                    exception.getMessage()
            );

            return response;
        }
    }

    @Transactional
    public Map<String, Object> executeSaga(Long paymentId) {

        Map<String, Object> response =
                new LinkedHashMap<>();

        Optional<Payment> paymentOptional =
                paymentRepository.findById(paymentId);

        if (paymentOptional.isEmpty()) {

            response.put("status", "FAILED");
            response.put(
                    "message",
                    "Payment not found"
            );
            response.put("paymentId", paymentId);

            return response;
        }

        Payment payment = paymentOptional.get();
        Optional<RecoverySaga> existingSaga = recoverySagaRepository
                        .findByPaymentIdAndStatus(paymentId, RecoverySagaState.COMPLETED.name()
                        );
        if (existingSaga.isPresent()) {
            RecoverySaga saga =
                    existingSaga.get();
            response.put(
                    "status",
                    "ALREADY_COMPLETED"
            );
            response.put(
                    "message",
                    "Recovery Saga already completed for this payment"
            );
            response.put(
                    "sagaId",
                    saga.getSagaId()
            );
            response.put(
                    "paymentId",
                    paymentId
            );

            return response;
        }

        RecoverySaga saga =
                new RecoverySaga();

        String sagaId =
                "SAGA-" + UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();

        saga.setSagaId(sagaId);
        saga.setPaymentId(paymentId);
        saga.setCurrentStep(
                RecoverySagaState.STARTED.name()
        );
        saga.setStatus(
                RecoverySagaState.STARTED.name()
        );

        saga.setFailureReason(payment.getFailureReason() != null ? payment.getFailureReason().name()
                        : null
        );
        saga.setStartedAt(now);
        saga.setUpdatedAt(now);
        recoverySagaRepository.save(saga);
        publishStateEvent(
                saga, null, RecoverySagaState.STARTED
        );
        try {
            transition(saga,
                    RecoverySagaState.PAYMENT_VALIDATION
            );

            recoverySagaRepository.save(saga);
            transition(
                    saga, RecoverySagaState.AI_DECISION
            );

            recoverySagaRepository.save(saga);
            AIRecoveryDecisionService.RecoveryDecision decision =
                    aiRecoveryDecisionService.decide(payment);
            String action =
                    decision.action();
            saga.setAction(action);
            transition(
                    saga, RecoverySagaState.AI_DECISION_COMPLETED
            );
            recoverySagaRepository.save(saga);

            transition(
                    saga,
                    RecoverySagaState.RECOVERY_EXECUTION
            );

            recoverySagaRepository.save(saga);

            Map<String, Object> executionResult = aiRecoveryExecutionService.executeRecovery(paymentId);

            transition(saga,
                    RecoverySagaState.RECOVERY_EXECUTED
            );
            recoverySagaRepository.save(saga);
            transition(saga,
                    RecoverySagaState.COMPLETION
            );
            recoverySagaRepository.save(saga);
            transition(
                    saga,
                    RecoverySagaState.COMPLETED
            );

            recoverySagaRepository.save(saga);

            response.put(
                    "status", "COMPLETED"
            );
            response.put(
                    "sagaId", saga.getSagaId()
            );
            response.put("paymentId",
                    paymentId
            );
            response.put("failureReason",
                    saga.getFailureReason()
            );
            response.put("action",
                    saga.getAction()
            );
            response.put("executionResult",
                    executionResult
            );
            response.put(
                    "startedAt",
                    saga.getStartedAt()
            );
            response.put("completedAt",
                    saga.getCompletedAt()
            );
            return response;
        } catch (Exception exception) {

            markSagaFailed(saga, exception);
            recoverySagaRepository.save(saga);
            response.put(
                    "status",
                    "FAILED"
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
                    "currentStep",
                    saga.getCurrentStep()
            );
            response.put(
                    "error",
                    exception.getMessage()
            );

            return response;
        }
    }

    private void transition(RecoverySaga saga, RecoverySagaState nextState) {

        RecoverySagaState previousState = parseState(saga.getCurrentStep());
        saga.transitionTo(nextState);
        publishStateEvent(
                saga, previousState, nextState
        );
    }
    private void publishStateEvent(
            RecoverySaga saga,
            RecoverySagaState previousState,
            RecoverySagaState currentState) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventId", UUID.randomUUID().toString()
        );
        payload.put("sagaId", saga.getSagaId()
        );
        payload.put("paymentId",
                saga.getPaymentId()
        );
        payload.put(
                "previousState", previousState != null ? previousState.name()
                        : null
        );
        payload.put(
                "currentState", currentState.name()
        );

        payload.put(
                "action",
                saga.getAction()
        );
        payload.put(
                "occurredAt",
                LocalDateTime.now().toString()
        );
        outboxEventService.createEvent(
                "RecoverySaga",
                saga.getSagaId(),
                "RECOVERY_SAGA_" + currentState.name(),
                payload
        );
    }
    private RecoverySagaState parseState(String state) {
        if (state == null || state.isBlank()) {
            throw new IllegalStateException(
                    "Recovery Saga state cannot be null or blank"
            );
        }

        try {
            return RecoverySagaState.valueOf(
                    state.toUpperCase()
            );

        } catch (IllegalArgumentException exception) {

            throw new IllegalStateException(
                    "Unknown Recovery Saga state: " + state,
                    exception
            );
        }
    }
    private void markSagaFailed(RecoverySaga saga, Exception exception) {
        RecoverySagaState previousState = parseState(saga.getCurrentStep());
        saga.setStatus(RecoverySagaState.FAILED.name());
        saga.setErrorMessage(exception.getMessage());
        saga.setUpdatedAt(LocalDateTime.now());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventId", UUID.randomUUID().toString());
        payload.put("sagaId", saga.getSagaId());
        payload.put("paymentId", saga.getPaymentId());
        payload.put("previousState", previousState.name());
        payload.put("currentState", RecoverySagaState.FAILED.name());
        payload.put("error", exception.getMessage());
        payload.put("occurredAt", LocalDateTime.now().toString());
        outboxEventService.createEvent("RecoverySaga",
                saga.getSagaId(), "RECOVERY_SAGA_FAILED",
                payload
        );
    }
}