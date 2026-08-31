package com.AIRevenueRecovery.service;

import com.AIRevenueRecovery.entity.FailureReason;
import com.AIRevenueRecovery.entity.Payment;
import com.AIRevenueRecovery.entity.PaymentStatus;
import com.AIRevenueRecovery.entity.RecoveryAttempt;
import com.AIRevenueRecovery.repository.PaymentRepository;
import com.AIRevenueRecovery.repository.RecoveryAttemptRepository;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AIRecoveryService {
    private final RecoveryAttemptRepository recoveryAttemptRepository;
    private final PaymentRepository paymentRepository;
    private final RecoveryDecisionService recoveryDecisionService;
    private final ChatModel chatModel;
    public AIRecoveryService(
            RecoveryAttemptRepository recoveryAttemptRepository,
            PaymentRepository paymentRepository,
            RecoveryDecisionService recoveryDecisionService,
            ChatModel chatModel) {
        this.recoveryAttemptRepository = recoveryAttemptRepository;
        this.paymentRepository = paymentRepository;
        this.recoveryDecisionService = recoveryDecisionService;
        this.chatModel = chatModel;
    }
    public Map<String, Object> getSummary() {
        List<Payment> failedPayments = paymentRepository.findByStatus(PaymentStatus.FAILED);
        List<RecoveryAttempt> attempts = recoveryAttemptRepository.findAll();
        int totalAttempts = attempts.size();
        int successfulAttempts = 0,pendingAttempts = 0;
        double failedRevenue = 0.0;
        for (Payment payment : failedPayments) {
            if (payment.getAmount() != null) {
                failedRevenue += payment.getAmount();
            }
        }
        for (RecoveryAttempt attempt : attempts) {
            if ("SUCCESS".equalsIgnoreCase(
                    attempt.getResult())) {
                successfulAttempts++;
            }
            if ("PENDING".equalsIgnoreCase(
                    attempt.getResult())) {
                pendingAttempts++;
            }
        }
        double recoveryScore = 0.0;
        if (totalAttempts > 0) {
            recoveryScore = (successfulAttempts * 100.0) / totalAttempts;
        }
        double potentialRevenue = failedRevenue * (recoveryScore / 100.0);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("recoveryScore", recoveryScore);
        response.put("potentialRevenue", potentialRevenue);
        response.put("recommendedRetries", pendingAttempts);
        response.put("failedPayments", failedPayments.size());
        response.put("failedRevenue", failedRevenue);
        response.put("totalRecoveryAttempts", totalAttempts);
        response.put("successfulAttempts", successfulAttempts);
        response.put("lastAnalysis", "Just now");
        return response;
    }
    public Map<String, Object> generateInsights() {

        /*
         * ============================================================
         * REAL DATABASE DATA
         * ============================================================
         */

        List<RecoveryAttempt> attempts =
                recoveryAttemptRepository.findAll();

        List<Payment> failedPayments =
                paymentRepository.findByStatus(
                        PaymentStatus.FAILED
                );

        List<Map<String, Object>> recommendations =
                new ArrayList<>();

        /*
         * ============================================================
         * FAILURE REASON ANALYSIS
         * ============================================================
         */

        for (FailureReason failureReason : FailureReason.values()) {

            if (failureReason == FailureReason.UNKNOWN) {
                continue;
            }

            int total = 0;
            int successful = 0;
            int failed = 0;
            int pending = 0;

            /*
             * Count REAL recovery attempts from DB
             */

            for (RecoveryAttempt attempt : attempts) {

                if (attempt.getFailureReason() != failureReason) {
                    continue;
                }

                total++;

                String result = attempt.getResult();

                if ("SUCCESS".equalsIgnoreCase(result)) {
                    successful++;
                }

                if ("FAILED".equalsIgnoreCase(result)) {
                    failed++;
                }

                if ("PENDING".equalsIgnoreCase(result)
                        || "RETRY_SCHEDULED".equalsIgnoreCase(result)) {
                    pending++;
                }
            }

            /*
             * No historical data for this failure reason.
             */

            if (total == 0) {
                continue;
            }

            /*
             * REAL historical success rate
             */

            double successRate =
                    (successful * 100.0) / total;

            /*
             * Rule-based recommendation
             */

            String action =
                    recoveryDecisionService.decideAction(
                            failureReason
                    );

            /*
             * Build recommendation object
             */

            Map<String, Object> recommendation =
                    new LinkedHashMap<>();

            recommendation.put(
                    "failureReason",
                    failureReason
            );

            recommendation.put(
                    "totalAttempts",
                    total
            );

            recommendation.put(
                    "successful",
                    successful
            );

            recommendation.put(
                    "failed",
                    failed
            );

            recommendation.put(
                    "pending",
                    pending
            );

            recommendation.put(
                    "successRate",
                    successRate
            );

            recommendation.put(
                    "recommendedAction",
                    action
            );

            recommendations.add(
                    recommendation
            );
        }

        /*
         * ============================================================
         * AI ANALYSIS
         * ============================================================
         */

        StringBuilder prompt =
                new StringBuilder();

        prompt.append("""
            You are an AI payment recovery assistant.

            Analyze the following REAL payment recovery data.

            Failed payments: %d
            Recovery attempts: %d

            Failure reason analysis:
            """.formatted(
                        failedPayments.size(),
                        attempts.size()
                )
        );

        for (Map<String, Object> recommendation
                : recommendations) {

            prompt.append(
                    "\nFailure reason: "
                            + recommendation.get(
                            "failureReason"
                    )
            );
            prompt.append("\nTotal attempts: " + recommendation.get("totalAttempts"));
            prompt.append("\nSuccessful: " + recommendation.get("successful"));
            prompt.append("\nFailed: " + recommendation.get("failed"));
            prompt.append("\nPending: " + recommendation.get("pending"));
            prompt.append("\nSuccess rate: " + recommendation.get("successRate") + "%");
            prompt.append("\nRule-based action: " + recommendation.get("recommendedAction"));
            prompt.append("\n");
        }
        prompt.append("""
            Based ONLY on the supplied payment recovery data,
            provide a concise practical recommendation.

            Focus on:
            1. Which failure types should be retried.
            2. Which failure types should not be retried.
            3. Which recovery strategy is most appropriate.

            Do not invent payment IDs.
            Do not invent payment amounts.
            Do not invent statistics.

            Maximum 500 characters.
            Do not use markdown.
            """);

        String aiRecommendation =
                safeAiCall(
                        prompt.toString(),
                        "Use the rule-based recovery recommendations derived from the available payment recovery data."
                );

        Map<String, Object> response =
                new LinkedHashMap<>();

        response.put(
                "failedPayments",
                failedPayments.size()
        );

        response.put(
                "totalRecoveryAttempts",
                attempts.size()
        );

        response.put(
                "recommendations",
                recommendations
        );

        response.put(
                "aiRecommendation",
                aiRecommendation
        );

        response.put(
                "generatedAt",
                LocalDateTime.now()
        );

        return response;
    }

    public Map<String, Object> analyzeRecovery(
            FailureReason failureReason) {

        List<RecoveryAttempt> attempts =
                recoveryAttemptRepository.findAll();

        int totalAttempts = 0;
        int successfulAttempts = 0;

        for (RecoveryAttempt attempt : attempts) {

            if (failureReason ==
                    attempt.getFailureReason()) {

                totalAttempts++;

                if ("SUCCESS".equalsIgnoreCase(
                        attempt.getResult())) {

                    successfulAttempts++;
                }
            }
        }

        double successRate = 0.0;

        if (totalAttempts > 0) {

            successRate =
                    (successfulAttempts * 100.0)
                            / totalAttempts;
        }

        String recommendedAction =
                recoveryDecisionService.decideAction(
                        failureReason
                );

        String prompt = """
                You are an AI payment recovery assistant.

                Payment failure reason: %s
                Historical recovery attempts: %d
                Successful attempts: %d
                Historical success rate: %.2f%%

                Recommended recovery action: %s

                Return ONLY a concise recovery recommendation.
                Maximum 200 characters.
                Do not use markdown.
                Do not return JSON.
                """.formatted(
                failureReason,
                totalAttempts,
                successfulAttempts,
                successRate,
                recommendedAction
        );

        String fallback =
                buildFallbackRecommendation(
                        failureReason,
                        recommendedAction
                );

        String aiRecommendation =
                safeAiCall(
                        prompt,
                        fallback
                );

        double confidence =
                calculateConfidence(
                        totalAttempts
                );

        Map<String, Object> response =
                new LinkedHashMap<>();

        response.put(
                "failureReason",
                failureReason
        );

        response.put(
                "recommendedAction",
                recommendedAction
        );

        response.put(
                "successRate",
                successRate
        );

        response.put(
                "confidence",
                confidence
        );

        response.put(
                "aiRecommendation",
                aiRecommendation
        );

        return response;
    }


    public Map<String, Object> analyzePaymentRecovery(
            Long paymentId) {

        Payment payment =
                paymentRepository.findById(paymentId).orElseThrow(() -> new RuntimeException("Payment not found with ID: " + paymentId));
        List<RecoveryAttempt> attempts =
                recoveryAttemptRepository.findByPaymentIdOrderByAttemptNumberAsc(paymentId);
        int totalAttempts = attempts.size();
        int successfulAttempts = 0;
        int failedAttempts = 0;
        int pendingAttempts = 0;
        for (RecoveryAttempt attempt : attempts) {
            if ("SUCCESS".equalsIgnoreCase(attempt.getResult())) {
                successfulAttempts++;
            }
            if ("FAILED".equalsIgnoreCase(attempt.getResult())) {
                failedAttempts++;
            }
            if ("PENDING".equalsIgnoreCase(attempt.getResult())) {
                pendingAttempts++;
            }
        }
        double historicalSuccessRate = 0.0;
        if (totalAttempts > 0) {
            historicalSuccessRate = (successfulAttempts * 100.0) / totalAttempts;
        }
        FailureReason failureReason = payment.getFailureReason();
        String recommendedAction =
                recoveryDecisionService.decideAction(failureReason);
        double confidence = calculatePaymentConfidence(totalAttempts, historicalSuccessRate);
        int retryCount = payment.getRetryCount() == null ? 0: payment.getRetryCount();
        StringBuilder prompt = new StringBuilder();
        prompt.append("""
          You are an AI payment recovery decision engine.

                Analyse this payment and its recovery history.

                Payment ID: %s
                Customer ID: %s
                Amount: %s
                Currency: %s
                Payment Status: %s
                Failure Reason: %s
                Retry Count: %d

                Historical Recovery Data:
                Total Attempts: %d
                Successful Attempts: %d
                Failed Attempts: %d
                Pending Attempts: %d
                Historical Success Rate: %.2f%%

                Rule-Based Recommended Action:
                %s

                Select the most appropriate recovery strategy.

                Consider:
                - Failure reason
                - Retry count
                - Historical recovery success
                - Number of previous attempts
                - Payment amount
                - Whether another retry is reasonable

                Return ONLY a concise recommendation.
                Maximum 300 characters.
                Do not return JSON.
                Do not use markdown.
                """.formatted(
                payment.getPaymentId(),
                payment.getCustomerId(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getStatus(),
                failureReason,
                retryCount,
                totalAttempts,
                successfulAttempts,
                failedAttempts,
                pendingAttempts,
                historicalSuccessRate,
                recommendedAction
        ));

        String fallback = buildPaymentFallback(
                        failureReason,
                        recommendedAction,
                        retryCount);

        String aiRecommendation = safeAiCall(prompt.toString(), fallback);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("paymentId", payment.getPaymentId());
        response.put("databaseId", payment.getId());
        response.put("customerId", payment.getCustomerId());
        response.put("amount",
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
                failureReason
        );

        response.put(
                "retryCount",
                retryCount
        );

        response.put(
                "totalRecoveryAttempts",
                totalAttempts
        );

        response.put(
                "successfulAttempts",
                successfulAttempts
        );

        response.put(
                "failedAttempts",
                failedAttempts
        );

        response.put(
                "pendingAttempts",
                pendingAttempts
        );

        response.put(
                "historicalSuccessRate",
                historicalSuccessRate
        );

        response.put(
                "recommendedAction",
                recommendedAction
        );

        response.put(
                "confidence",
                confidence
        );

        response.put("aiRecommendation", aiRecommendation
        );
        response.put("analysisType", "PAYMENT_AWARE_AI"
        );
        response.put("generatedAt", "Just now"
        );
        return response;
    }
    private String safeAiCall(
            String prompt,
            String fallback) {

        try {

            String result =
                    chatModel.call(prompt);

            if (result == null ||
                    result.trim().isEmpty()) {

                System.out.println(
                        "AI returned empty response. Using fallback."
                );

                return fallback;
            }

            return result.trim();

        } catch (Exception e) {

            System.err.println(
                    "========================================"
            );

            System.err.println(
                    "GEMINI AI CALL FAILED"
            );

            System.err.println(
                    "Exception: "
                            + e.getClass().getName()
            );

            System.err.println(
                    "Message: "
                            + e.getMessage()
            );

            System.err.println(
                    "========================================"
            );

            return fallback;
        }
    }


    private String buildFallbackRecommendation(
            FailureReason failureReason,
            String recommendedAction) {

        if (failureReason == null) {

            return "Manual review is recommended because the payment failure reason is unknown.";
        }

        return switch (failureReason) {

            case INSUFFICIENT_FUNDS ->
                    "Insufficient funds detected. Avoid immediate repeated retries and ask the customer to add funds before retrying.";

            case CARD_DECLINED ->
                    "Card declined. Request an alternate payment method instead of repeatedly retrying the same card.";

            case NETWORK_ERROR ->
                    "Temporary network failure. A controlled retry is appropriate after a short delay.";

            case EXPIRED_CARD ->
                    "The card is expired. Ask the customer to update the card before attempting recovery.";

            case BANK_ERROR ->
                    "Temporary bank error. Wait briefly and retry the payment with a controlled retry limit.";

            case FRAUD_DETECTED ->
                    "Potential fraud detected. Block automated recovery and send the payment for manual review.";

            case UNKNOWN ->
                    "Unknown payment failure. Manual review is recommended before attempting recovery.";

            default ->
                    "Follow the rule-based recovery action: "
                            + recommendedAction;
        };
    }


    private String buildPaymentFallback(
            FailureReason failureReason,
            String recommendedAction,
            int retryCount) {

        if (failureReason == FailureReason.FRAUD_DETECTED) {

            return "Fraud risk detected. Automated recovery should be blocked and the payment reviewed manually.";
        }

        if (retryCount >= 3) {

            return "The payment has already been retried multiple times. Avoid another immediate retry and use an alternate recovery method.";
        }

        return switch (failureReason) {

            case INSUFFICIENT_FUNDS ->
                    "Insufficient funds. Ask the customer to add funds before attempting another payment.";

            case CARD_DECLINED ->
                    "The card was declined. Request an alternate payment method.";

            case NETWORK_ERROR ->
                    "The failure appears temporary. Retry after a short delay.";

            case EXPIRED_CARD ->
                    "The card is expired. Request an updated card before retrying.";

            case BANK_ERROR ->
                    "A temporary bank error occurred. Retry after a short delay.";

            case FRAUD_DETECTED ->
                    "Fraud risk detected. Block automated recovery.";

            case UNKNOWN ->
                    "Unknown failure reason. Manual review is recommended.";

            default ->
                    "Recommended action: " + recommendedAction;
        };
    }


    private double calculatePaymentConfidence(
            int totalAttempts,
            double historicalSuccessRate) {

        if (totalAttempts == 0) {
            return 0.40;
        }

        double dataConfidence;

        if (totalAttempts >= 10) {

            dataConfidence = 0.95;

        } else if (totalAttempts >= 5) {

            dataConfidence = 0.80;

        } else if (totalAttempts >= 2) {

            dataConfidence = 0.60;

        } else {

            dataConfidence = 0.40;
        }

        double successConfidence =
                historicalSuccessRate / 100.0;

        double confidence =
                (dataConfidence * 0.60)
                        + (successConfidence * 0.40);

        return Math.min(
                0.99,
                Math.max(
                        0.0,
                        confidence
                )
        );
    }


    private double calculateConfidence(int totalAttempts) {
        if (totalAttempts == 0) {
            return 0.0;
        }
        if (totalAttempts >= 10) {
            return 0.95;
        }
        if (totalAttempts >= 5) {
            return 0.80;
        }
        if (totalAttempts >= 2) {
            return 0.60;
        }
        return 0.40;
    }
}