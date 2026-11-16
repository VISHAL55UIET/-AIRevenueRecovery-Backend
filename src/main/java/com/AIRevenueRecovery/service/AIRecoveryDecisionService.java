package com.AIRevenueRecovery.service;

import com.AIRevenueRecovery.entity.FailureReason;
import com.AIRevenueRecovery.entity.Payment;
import com.AIRevenueRecovery.policy.PolicyEngine;
import com.AIRevenueRecovery.policy.RecoveryActions;
import com.AIRevenueRecovery.repository.RecoveryAttemptRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.AIRevenueRecovery.policy.RecoveryActions.*;

/**
 * Asks the LLM to choose ONE action from the set that policy allows for this
 * payment. The result is only a proposal: RecoveryPlanService passes it through
 * PolicyEngine before anything runs.
 */
@Service
public class AIRecoveryDecisionService {

    private static final Logger log = LoggerFactory.getLogger(AIRecoveryDecisionService.class);

    private static final double MIN_AI_CONFIDENCE = 0.70;

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;
    private final RecoveryDecisionService recoveryDecisionService;
    private final RecoveryAttemptRepository recoveryAttemptRepository;
    private final PolicyEngine policyEngine;

    public AIRecoveryDecisionService(
            ChatClient.Builder chatClientBuilder,
            ObjectMapper objectMapper,
            RecoveryDecisionService recoveryDecisionService,
            RecoveryAttemptRepository recoveryAttemptRepository,
            PolicyEngine policyEngine) {

        this.chatClient = chatClientBuilder.build();
        this.objectMapper = objectMapper;
        this.recoveryDecisionService = recoveryDecisionService;
        this.recoveryAttemptRepository = recoveryAttemptRepository;
        this.policyEngine = policyEngine;
    }

    public RecoveryDecision decide(Payment payment) {

        if (payment == null) {
            throw new IllegalArgumentException("Payment is required");
        }

        FailureReason reason = payment.getFailureReason() != null
                ? payment.getFailureReason()
                : FailureReason.UNKNOWN;

        int retryCount = payment.getRetryCount() != null ? payment.getRetryCount() : 0;

        Set<String> allowed = policyEngine.allowedActions(reason, retryCount);
        HistoricalData history = loadHistory(reason);
        String adaptive = loadAdaptiveAction(reason, retryCount);

        // Policy leaves nothing to choose from: skip the LLM.
        if (reason == FailureReason.FRAUD_DETECTED) {
            return new RecoveryDecision(
                    BLOCK_RECOVERY,
                    "Fraud detected. Block further payment recovery attempts.",
                    1.0,
                    reasoning(reason, retryCount, allowed, history, adaptive,
                            BLOCK_RECOVERY, "Fraud failures are never retried.", false));
        }

        try {

            String prompt = buildPrompt(payment, reason, retryCount, allowed, history, adaptive);

            String response = chatClient.prompt().user(prompt).call().content();

            if (response == null || response.isBlank()) {
                return fallback(reason, retryCount, allowed, history, adaptive,
                        "AI returned an empty response.");
            }

            RecoveryDecision parsed = parseResponse(response);

            if (!isKnown(parsed.action())) {
                return fallback(reason, retryCount, allowed, history, adaptive,
                        "AI proposed an unknown action: " + parsed.action());
            }

            if (parsed.confidence() == null || parsed.confidence() < MIN_AI_CONFIDENCE) {
                return fallback(reason, retryCount, allowed, history, adaptive,
                        "AI confidence too low: " + parsed.confidence());
            }

            Object aiReason = parsed.reasoning() != null
                    ? parsed.reasoning().get("decisionReason")
                    : null;

            String decisionReason = aiReason != null
                    ? String.valueOf(aiReason)
                    : parsed.recommendation();

            return new RecoveryDecision(
                    parsed.action(),
                    parsed.recommendation(),
                    parsed.confidence(),
                    reasoning(reason, retryCount, allowed, history, adaptive,
                            parsed.action(), decisionReason, false));

        } catch (Exception exception) {

            log.warn("AI decision failed for payment {}. Using fallback. reason={}",
                    payment.getPaymentId(), exception.toString());

            return fallback(reason, retryCount, allowed, history, adaptive,
                    "AI call failed: " + exception.getClass().getSimpleName());
        }
    }

    // ============================================================
    // PROMPT
    // ============================================================

    private String buildPrompt(
            Payment payment,
            FailureReason reason,
            int retryCount,
            Set<String> allowed,
            HistoricalData history,
            String adaptive) {

        return """
                You are a payment recovery decision engine.
                Choose exactly ONE action for the failed payment below.

                CURRENT PAYMENT
                Payment ID: %s
                Amount: %s %s
                Failure reason: %s
                Retry count so far: %d

                HISTORICAL RECOVERY DATA (same failure reason)
                Attempts: %d, successful: %d, failed: %d, pending: %d, success rate: %.2f%%

                ADAPTIVE STRATEGY (derived from past outcomes)
                %s

                ALLOWED ACTIONS (choose only from this list, anything else is rejected)
                %s

                Rules:
                - Prefer automated recovery. Choose ESCALATE_TO_HUMAN only if no other listed action fits.
                - Use the historical data above. Do not invent numbers.
                - Explain why this action fits the failure reason, retry count and history.

                Return ONLY valid JSON in this format:

                {
                  "action": "REQUEST_ALTERNATE_PAYMENT",
                  "recommendation": "Card declines recover poorly on retry. Ask for another payment method.",
                  "confidence": 0.92,
                  "reasoning": {
                    "decisionReason": "Historical recovery for this failure reason is low and the payment was already retried."
                  }
                }

                confidence must be between 0.0 and 1.0.
                """.formatted(
                payment.getPaymentId(),
                payment.getAmount(),
                payment.getCurrency(),
                reason.name(),
                retryCount,
                history.totalAttempts(),
                history.successful(),
                history.failed(),
                history.pending(),
                history.successRate(),
                adaptive != null ? adaptive : "NO_ADAPTIVE_STRATEGY",
                String.join(", ", allowed)
        );
    }

    // ============================================================
    // PARSE
    // ============================================================

    private RecoveryDecision parseResponse(String response) {

        try {

            String json = response.trim();

            // Strip ```json ... ``` fences if the model adds them.
            if (json.startsWith("```")) {
                json = json
                        .replaceFirst("^```json\\s*", "")
                        .replaceFirst("^```\\s*", "")
                        .replaceFirst("\\s*```$", "")
                        .trim();
            }

            JsonNode root = objectMapper.readTree(json);

            JsonNode actionNode = root.get("action");
            JsonNode recommendationNode = root.get("recommendation");
            JsonNode confidenceNode = root.get("confidence");
            JsonNode reasoningNode = root.get("reasoning");

            if (actionNode == null || recommendationNode == null || confidenceNode == null) {
                throw new IllegalStateException("AI response missing required fields: " + json);
            }

            Map<String, Object> reasoning = new LinkedHashMap<>();

            if (reasoningNode != null && reasoningNode.isObject()) {
                reasoningNode.fields().forEachRemaining(entry ->
                        reasoning.put(
                                entry.getKey(),
                                objectMapper.convertValue(entry.getValue(), Object.class)));
            }

            return new RecoveryDecision(
                    actionNode.asText().trim().toUpperCase(),
                    recommendationNode.asText(),
                    confidenceNode.asDouble(),
                    reasoning);

        } catch (Exception exception) {
            throw new IllegalStateException("Unable to parse AI response: " + response, exception);
        }
    }

    // ============================================================
    // FALLBACK (deterministic, no LLM)
    // ============================================================

    private RecoveryDecision fallback(
            FailureReason reason,
            int retryCount,
            Set<String> allowed,
            HistoricalData history,
            String adaptive,
            String why) {

        String action = adaptive != null
                ? convertAdaptiveAction(adaptive)
                : policyEngine.defaultAction(reason, retryCount);

        if (!allowed.contains(action)) {
            action = policyEngine.defaultAction(reason, retryCount);
        }

        return new RecoveryDecision(
                action,
                buildRecommendation(action),
                fallbackConfidence(reason, action),
                reasoning(reason, retryCount, allowed, history, adaptive, action,
                        "Fallback decision: " + why
                                + " Chosen from failure reason, retry count, history and adaptive strategy.",
                        true));
    }

    private String convertAdaptiveAction(String action) {

        return switch (action) {
            case "RETRY_PAYMENT", "RETRY_AFTER_DELAY", "AUTOMATIC_RETRY" -> AUTOMATIC_RETRY;
            case "REQUEST_CARD_UPDATE", "REQUEST_ALTERNATE_PAYMENT" -> REQUEST_ALTERNATE_PAYMENT;
            case "BLOCK_RECOVERY" -> BLOCK_RECOVERY;
            case "RETRY_AFTER_BALANCE_CHECK", "SEND_PAYMENT_REMINDER" -> SEND_PAYMENT_REMINDER;
            default -> SEND_PAYMENT_REMINDER;
        };
    }

    private String buildRecommendation(String action) {

        return switch (action) {
            case BLOCK_RECOVERY ->
                    "Further recovery is blocked because continuing is unsafe or not permitted.";
            case REQUEST_ALTERNATE_PAYMENT ->
                    "Ask the customer to pay with another payment method.";
            case AUTOMATIC_RETRY ->
                    "A temporary payment failure was detected. Retry the payment.";
            case ESCALATE_TO_HUMAN ->
                    "No automated action fits. Hand this payment to a human.";
            default ->
                    "Payment failed. Ask the customer to review their payment method and retry.";
        };
    }

    private double fallbackConfidence(FailureReason reason, String action) {

        if (BLOCK_RECOVERY.equals(action)) {
            return 1.0;
        }

        return switch (reason) {
            case FRAUD_DETECTED -> 1.0;
            case EXPIRED_CARD -> 0.95;
            case CARD_DECLINED, NETWORK_ERROR, BANK_ERROR -> 0.90;
            case INSUFFICIENT_FUNDS -> 0.85;
            case INCORRECT_CVC -> 0.0;
            case PROCESSING_ERROR -> 0.0;
            case AUTHENTICATION_REQUIRED -> 0.0;
            case UNKNOWN -> 0.70;
        };
    }
    private HistoricalData loadHistory(FailureReason reason) {

        try {

            int successful = 0;
            int failed = 0;
            int pending = 0;

            for (Object[] row : recoveryAttemptRepository.countResultsByFailureReason(reason)) {

                String result = row[0] == null ? "" : row[0].toString().trim().toUpperCase();
                int count = ((Number) row[1]).intValue();

                switch (result) {
                    case "SUCCESS" -> successful += count;
                    case "", "PENDING", "PENDING_CHECKOUT" -> pending += count;
                    default -> failed += count;   // FAILED, EXPIRED, ...
                }
            }

            int total = successful + failed + pending;
            double rate = total == 0 ? 0.0 : (successful * 100.0) / total;

            return new HistoricalData(total, successful, failed, pending, rate);

        } catch (Exception exception) {
            log.warn("Historical data lookup failed: {}", exception.toString());
            return new HistoricalData(0, 0, 0, 0, 0.0);
        }
    }

    private String loadAdaptiveAction(FailureReason reason, int retryCount) {

        try {
            return recoveryDecisionService.decideAction(reason, retryCount);
        } catch (Exception exception) {
            log.warn("Adaptive strategy failed: {}", exception.toString());
            return null;
        }
    }

    // ============================================================
    // REASONING (built from OUR data, never trusting the LLM's numbers)
    // ============================================================

    private Map<String, Object> reasoning(
            FailureReason reason,
            int retryCount,
            Set<String> allowed,
            HistoricalData history,
            String adaptive,
            String proposedAction,
            String decisionReason,
            boolean fallbackUsed) {

        Map<String, Object> map = new LinkedHashMap<>();

        map.put("failureReason", reason.name());
        map.put("retryCount", retryCount);
        map.put("allowedActions", List.copyOf(allowed));
        map.put("historicalAttempts", history.totalAttempts());
        map.put("historicalSuccessful", history.successful());
        map.put("historicalFailed", history.failed());
        map.put("historicalPending", history.pending());
        map.put("historicalSuccessRate", history.successRate());
        map.put("adaptiveStrategy", adaptive);
        map.put("proposedAction", proposedAction);
        map.put("decisionReason", decisionReason);
        map.put("fallbackUsed", fallbackUsed);

        return map;
    }

    private record HistoricalData(
            int totalAttempts,
            int successful,
            int failed,
            int pending,
            double successRate
    ) {
    }

    public record RecoveryDecision(
            String action,
            String recommendation,
            Double confidence,
            Map<String, Object> reasoning
    ) {
    }
}