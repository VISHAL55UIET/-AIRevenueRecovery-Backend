package com.AIRevenueRecovery.service;

import com.AIRevenueRecovery.entity.FailureReason;
import com.AIRevenueRecovery.entity.Payment;
import com.AIRevenueRecovery.entity.RecoveryAttempt;
import com.AIRevenueRecovery.repository.RecoveryAttemptRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AIRecoveryDecisionService {
    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;
    private final RecoveryDecisionService recoveryDecisionService;
    private final RecoveryAttemptRepository recoveryAttemptRepository;
    private static final double MIN_AI_CONFIDENCE = 0.70;
    private static final int MAX_RETRIES = 3;
    public AIRecoveryDecisionService(ChatClient.Builder chatClientBuilder,
            RecoveryDecisionService recoveryDecisionService, RecoveryAttemptRepository recoveryAttemptRepository) {
        this.chatClient = chatClientBuilder.build();
        this.objectMapper = new ObjectMapper();
        this.recoveryDecisionService = recoveryDecisionService;
        this.recoveryAttemptRepository = recoveryAttemptRepository;
    }

    public RecoveryDecision decide(Payment payment) {
        if (payment == null) {
            throw new IllegalArgumentException("Payment is required");
        }
        FailureReason failureReason = payment.getFailureReason() != null ? payment.getFailureReason() : FailureReason.UNKNOWN;
        String failureReasonName = failureReason.name();
        int retryCount = payment.getRetryCount() != null ? payment.getRetryCount() : 0;
        if (failureReason == FailureReason.FRAUD_DETECTED) {
            Map<String, Object> reasoning = buildReasoning(failureReason, retryCount, "BLOCK_RECOVERY");
            return new RecoveryDecision("BLOCK_RECOVERY", "Fraud detected. Block further payment recovery attempts to mitigate security risk.", 1.0, reasoning);}
        if (retryCount >= MAX_RETRIES) {
            Map<String, Object> reasoning = buildReasoning(failureReason, retryCount, "BLOCK_RECOVERY");
            return new RecoveryDecision(
                    "BLOCK_RECOVERY",
                    "Maximum retry limit reached. Block further recovery attempts.", 1.0,
                    reasoning);
        }
        HistoricalData historicalData = getHistoricalData(failureReason);
        String adaptiveAction = recoveryDecisionService.decideAction(failureReason, retryCount);
        String prompt = """
                You are an AI payment recovery decision engine.
                Analyze the failed payment using:
                1. Current payment information
                2. Historical recovery outcomes
                3. Adaptive recovery strategy
                4. Safety rules

                =====================================================
                CURRENT PAYMENT
                =====================================================

                Payment ID: %s
                Amount: %s
                Currency: %s
                Failure Reason: %s
                Current Retry Count: %d

                =====================================================
                HISTORICAL RECOVERY DATA
                =====================================================

                Historical Attempts: %d
                Historical Successful: %d
                Historical Failed: %d
                Historical Pending: %d
                Historical Success Rate: %.2f%%

                =====================================================
                ADAPTIVE STRATEGY
                =====================================================

                Recommended Strategy:
                %s

                The adaptive strategy is generated from historical
                recovery outcomes stored in the database.

                =====================================================
                ALLOWED ACTIONS
                =====================================================

                AUTOMATIC_RETRY
                REQUEST_ALTERNATE_PAYMENT
                SEND_PAYMENT_REMINDER
                BLOCK_RECOVERY

                =====================================================
                SAFETY RULES
                =====================================================

                FRAUD_DETECTED:
                BLOCK_RECOVERY

                Retry count >= 3:
                BLOCK_RECOVERY

                EXPIRED_CARD:
                REQUEST_ALTERNATE_PAYMENT

                CARD_DECLINED:
                REQUEST_ALTERNATE_PAYMENT

                NETWORK_ERROR:
                AUTOMATIC_RETRY

                BANK_ERROR:
                AUTOMATIC_RETRY

                INSUFFICIENT_FUNDS:
                SEND_PAYMENT_REMINDER

                UNKNOWN:
                SEND_PAYMENT_REMINDER

                =====================================================
                EXPLAINABILITY
                =====================================================

                Explain the decision using the actual historical
                data provided above.

                Do not invent historical numbers.

                The reasoning must explain:
                - failure reason
                - retry count
                - historical recovery performance
                - adaptive strategy
                - why the final action was selected

                =====================================================
                OUTPUT
                =====================================================

                Return ONLY valid JSON.

                Required format:

                {
                  "action": "REQUEST_ALTERNATE_PAYMENT",
                  "recommendation": "Card decline has poor historical recovery performance. Use an alternate payment method.",
                  "confidence": 0.92,
                  "reasoning": {
                    "failureReason": "CARD_DECLINED",
                    "retryCount": 2,
                    "historicalAttempts": 30,
                    "historicalSuccessful": 2,
                    "historicalFailed": 6,
                    "historicalPending": 0,
                    "historicalSuccessRate": 6.67,
                    "adaptiveStrategy": "REQUEST_ALTERNATE_PAYMENT",
                    "decisionReason": "Historical recovery performance is low and the payment has already been retried."
                  }
                }

                confidence must be between 0.0 and 1.0.
                """.formatted(
                payment.getPaymentId(),
                payment.getAmount(),
                payment.getCurrency(),
                failureReasonName,
                retryCount,
                historicalData.totalAttempts(),
                historicalData.successful(),
                historicalData.failed(),
                historicalData.pending(),
                historicalData.successRate(),
                adaptiveAction
        );

        try {
            String response = chatClient.prompt().user(prompt).call().content();
            System.out.println("========== AI RESPONSE ==========");
            System.out.println(response);
            System.out.println("=================================");
            if (response == null || response.isBlank()) {
                return adaptiveFallback(failureReason,
                        retryCount, historicalData, adaptiveAction);
            }
            RecoveryDecision decision = parseResponse(response);
            if (!isValidAction(decision.action())) {
                System.err.println("Invalid AI action: " + decision.action());
                return adaptiveFallback(failureReason,
                        retryCount, historicalData, adaptiveAction);
            }
            if (decision.confidence() == null
                    || decision.confidence() < MIN_AI_CONFIDENCE) {
                System.out.println("AI confidence too low: " + decision.confidence());
                return adaptiveFallback(
                        failureReason, retryCount, historicalData, adaptiveAction
                );
            }
            if (failureReason ==
                    FailureReason.FRAUD_DETECTED) {
                return new RecoveryDecision(
                        "BLOCK_RECOVERY",
                        "Fraud detected. Block further payment recovery attempts.",
                        1.0, buildReasoning(failureReason, retryCount, "BLOCK_RECOVERY"));
            }
            if (retryCount >= MAX_RETRIES) {
                return new RecoveryDecision(
                        "BLOCK_RECOVERY", "Maximum retry limit reached. Block further recovery attempts.",
                        1.0, buildReasoning(failureReason, retryCount, "BLOCK_RECOVERY"));
            }
            Map<String, Object> reasoning = new LinkedHashMap<>();
            reasoning.put("failureReason", failureReason.name()
            );
            reasoning.put("retryCount", retryCount
            );
            reasoning.put("historicalAttempts", historicalData.totalAttempts()
            );
            reasoning.put("historicalSuccessful", historicalData.successful()
            );
            reasoning.put("historicalFailed", historicalData.failed()
            );

            reasoning.put("historicalPending", historicalData.pending()
            );
            reasoning.put("historicalSuccessRate", historicalData.successRate()
            );
            reasoning.put("adaptiveStrategy", adaptiveAction
            );
            reasoning.put("decisionReason", decision.reasoning() != null ? decision.reasoning().get("decisionReason") : decision.recommendation()
            );
            return new RecoveryDecision(decision.action(),
                    decision.recommendation(), decision.confidence(), reasoning
            );

        } catch (Exception e) {
            System.err.println(
                    "========== AI ERROR =========="
            );
            e.printStackTrace();

            System.err.println(
                    "=============================="
            );
            return adaptiveFallback(failureReason,
                    retryCount, historicalData, adaptiveAction);
        }
    }
    private HistoricalData getHistoricalData(FailureReason failureReason) {
        List<RecoveryAttempt> attempts = recoveryAttemptRepository.findAll();
        int total = 0,successful = 0,failed = 0,pending = 0;
        for (RecoveryAttempt attempt : attempts) {
            if (attempt == null) {
                continue;
            }
            if (attempt.getFailureReason() != failureReason) {
                continue;
            }

            total++;
            String result = attempt.getResult();
            if ("SUCCESS".equalsIgnoreCase(result)) {
                successful++;
            } else if ("FAILED".equalsIgnoreCase(result)) {
                failed++;
            } else if ("PENDING".equalsIgnoreCase(result)) {
                pending++;
            }
        }
        double successRate = total == 0 ? 0.0 : (successful * 100.0) / total;
        return new HistoricalData(total, successful,
                failed, pending, successRate);
    }
    private RecoveryDecision parseResponse(String response) {
        try {
            String json = response.trim();
            if (json.startsWith("```")) {
                json = json
                        .replaceFirst("^```json\\s*", "")
                        .replaceFirst("^```\\s*", "")
                        .replaceFirst("\\s*```$", ""
                        )
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
            String action = actionNode.asText();

            String recommendation = recommendationNode.asText();
            double confidence = confidenceNode.asDouble();
            Map<String, Object> reasoning = new LinkedHashMap<>();
            if (reasoningNode != null && reasoningNode.isObject()) {
                reasoningNode.fields().forEachRemaining(entry ->
                                reasoning.put(entry.getKey(), objectMapper.convertValue(
                                        entry.getValue(), Object.class)));
            }
            return new RecoveryDecision(action, recommendation, confidence, reasoning);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to parse AI response: " + response, e);
        }
    }
    private boolean isValidAction(String action) {
        if (action == null) {
            return false;
        }
        return action.equals("AUTOMATIC_RETRY") || action.equals("REQUEST_ALTERNATE_PAYMENT")
                || action.equals("SEND_PAYMENT_REMINDER") || action.equals("BLOCK_RECOVERY");
    }
    private RecoveryDecision adaptiveFallback(FailureReason failureReason, int retryCount,
            HistoricalData historicalData, String adaptiveAction) {
        String action = adaptiveAction != null ? convertAdaptiveAction(adaptiveAction) : "SEND_PAYMENT_REMINDER";
        String recommendation = buildRecommendation(failureReason, adaptiveAction);
        double confidence = calculateFallbackConfidence(
                        failureReason, action
                );

        Map<String, Object> reasoning = buildReasoning(failureReason, retryCount, adaptiveAction);
        reasoning.put("historicalAttempts", historicalData.totalAttempts()
        );
        reasoning.put("historicalSuccessful",
                historicalData.successful()
        );
        reasoning.put("historicalFailed", historicalData.failed()
        );
        reasoning.put("historicalPending", historicalData.pending()
        );
        reasoning.put("historicalSuccessRate", historicalData.successRate()
        );
        reasoning.put("fallbackUsed", true
        );
        return new RecoveryDecision(action,
                recommendation, confidence, reasoning
        );
    }
    private String convertAdaptiveAction(String action) {
        if (action == null) {
            return "SEND_PAYMENT_REMINDER";
        }
        return switch (action) {
            case "RETRY_AFTER_BALANCE_CHECK" ->
                    "SEND_PAYMENT_REMINDER";
            case "RETRY_PAYMENT" ->
                    "AUTOMATIC_RETRY";
            case "RETRY_AFTER_DELAY" ->
                    "AUTOMATIC_RETRY";

            case "REQUEST_CARD_UPDATE" ->
                    "REQUEST_ALTERNATE_PAYMENT";

            case "REQUEST_ALTERNATE_PAYMENT" ->
                    "REQUEST_ALTERNATE_PAYMENT";

            case "BLOCK_RECOVERY" ->
                    "BLOCK_RECOVERY";

            default ->
                    "SEND_PAYMENT_REMINDER";
        };
    }
    private Map<String, Object> buildReasoning(FailureReason failureReason, int retryCount, String adaptiveAction) {
        Map<String, Object> reasoning = new LinkedHashMap<>();
        reasoning.put("failureReason", failureReason != null ? failureReason.name() : "UNKNOWN");
        reasoning.put("retryCount", retryCount);
        reasoning.put("adaptiveStrategy", adaptiveAction);
        return reasoning;
    }
    private String buildRecommendation(FailureReason reason, String action) {
        if (action == null) {
            return "Payment failed. Review the payment and determine the safest recovery action.";
        }
        return switch (action) {
            case "BLOCK_RECOVERY" ->
                    "Further recovery attempts are blocked because continuing recovery is unsafe or the retry limit has been reached.";
            case "REQUEST_ALTERNATE_PAYMENT" ->
                    "Historical recovery performance indicates that an alternate payment method is more appropriate.";
            case "RETRY_PAYMENT" ->
                    "A temporary payment failure was detected. Retry the payment.";

            case "RETRY_AFTER_DELAY" ->
                    "A temporary failure was detected. Retry the payment after a short delay.";

            case "RETRY_AFTER_BALANCE_CHECK" ->
                    "Insufficient funds detected. Ask the customer to update their balance and retry.";

            case "REQUEST_CARD_UPDATE" ->
                    "The card has expired. Ask the customer to update their card details.";

            default ->
                    "Payment failed. Ask the customer to review their payment method and retry.";
        };
    }

    /*
     * =========================================================
     * FALLBACK CONFIDENCE
     * =========================================================
     */

    private double calculateFallbackConfidence(
            FailureReason reason,
            String action
    ) {

        if (reason == FailureReason.FRAUD_DETECTED) {
            return 1.0;
        }

        if ("BLOCK_RECOVERY".equals(action)) {
            return 1.0;
        }

        if (reason == FailureReason.EXPIRED_CARD) {
            return 0.95;
        }

        if (reason == FailureReason.CARD_DECLINED) {
            return 0.90;
        }

        if (reason == FailureReason.NETWORK_ERROR) {
            return 0.90;
        }

        if (reason == FailureReason.BANK_ERROR) {
            return 0.90;
        }

        if (reason == FailureReason.INSUFFICIENT_FUNDS) {
            return 0.85;
        }

        return 0.70;
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