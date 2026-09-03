package com.AIRevenueRecovery.service;

import com.AIRevenueRecovery.entity.Payment;
import com.AIRevenueRecovery.entity.PaymentStatus;
import com.AIRevenueRecovery.entity.RecoveryAttempt;
import com.AIRevenueRecovery.entity.RecoveryPlan;
import com.AIRevenueRecovery.entity.RecoveryPlanStep;
import com.AIRevenueRecovery.repository.PaymentRepository;
import com.AIRevenueRecovery.repository.RecoveryAttemptRepository;
import com.AIRevenueRecovery.repository.RecoveryPlanRepository;
import com.AIRevenueRecovery.repository.RecoveryPlanStepRepository;
import jakarta.transaction.Transactional;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class RazorpayWebhookService {

    private static final Logger log = LoggerFactory.getLogger(RazorpayWebhookService.class);
    private static final String PAYMENT_CAPTURED = "payment.captured";
    private static final String PAYMENT_FAILED = "payment.failed";
    private static final String SUCCESS = "SUCCESS";
    private static final String FAILED = "FAILED";
    private static final String COMPLETED = "COMPLETED";
    private final RecoveryAttemptRepository recoveryAttemptRepository;
    private final PaymentRepository paymentRepository;
    private final RecoveryPlanRepository recoveryPlanRepository;
    private final RecoveryPlanStepRepository recoveryPlanStepRepository;
    public RazorpayWebhookService(
            RecoveryAttemptRepository recoveryAttemptRepository,
            PaymentRepository paymentRepository,
            RecoveryPlanRepository recoveryPlanRepository,
            RecoveryPlanStepRepository recoveryPlanStepRepository) {
        this.recoveryAttemptRepository = recoveryAttemptRepository;
        this.paymentRepository = paymentRepository;
        this.recoveryPlanRepository = recoveryPlanRepository;
        this.recoveryPlanStepRepository = recoveryPlanStepRepository;
    }
    @Transactional
    public void processWebhook(String payload) {
        if (payload == null || payload.isBlank()) {
            throw new IllegalArgumentException(
                    "Webhook payload is required"
            );
        }
        try {
            JSONObject webhook = new JSONObject(payload);
            String event = webhook.optString("event", "");
            if (event.isBlank()) {
                log.warn("Razorpay webhook event is missing"
                );
                return;
            }
            log.info("Razorpay webhook received. event={}", event);
            switch (event) {
                case PAYMENT_CAPTURED -> handlePaymentCaptured(webhook);
                case PAYMENT_FAILED -> handlePaymentFailed(webhook);
                default -> log.info("Ignoring unsupported Razorpay webhook. event={}", event
                );
            }

        } catch (Exception exception) {
            log.error(
                    "Failed to process Razorpay webhook. reason={}", exception.getMessage(), exception
            );
            throw new IllegalStateException("Failed to process Razorpay webhook", exception);
        }
    }


    private void handlePaymentCaptured(JSONObject webhook) {
        JSONObject payload = webhook.optJSONObject("payload");
        if (payload == null) {
            log.warn("payment.captured webhook payload is missing");
            return;
        }
        JSONObject paymentEntity = getEntity(payload, "payment");
        if (paymentEntity == null) {
            log.warn("Payment entity missing in payment.captured webhook");
            return;
        }
        String razorpayPaymentId = paymentEntity.optString(
                "id",
                        null);
        String razorpayOrderId = paymentEntity.optString("order_id",
                        null
                );
        if (razorpayPaymentId == null || razorpayPaymentId.isBlank()) {
            log.warn(
                    "Razorpay payment ID missing in payment.captured webhook"
            );
            return;
        }
        if (razorpayOrderId == null || razorpayOrderId.isBlank()) {
            log.warn(
                    "Razorpay order ID missing in payment.captured webhook. " + "paymentId={}",
                    razorpayPaymentId
            );
            return;
        }
        RecoveryAttempt attempt = recoveryAttemptRepository.findByRazorpayOrderId(razorpayOrderId).orElse(null);
        if (attempt == null) {
            log.warn("No recovery attempt found for Razorpay order. " + "orderId={}, razorpayPaymentId={}",
                    razorpayOrderId, razorpayPaymentId);
            return;
        }
        if (SUCCESS.equalsIgnoreCase(attempt.getResult())) {
            log.info("Duplicate payment.captured webhook ignored. " +
                            "orderId={}, paymentId={}", razorpayOrderId, razorpayPaymentId
            );
            return;
        }
        Payment payment = attempt.getPayment();
        if (payment == null) {
            log.error("Recovery attempt has no payment. attemptId={}",
                    attempt.getId()
            );

            return;
        }
        RecoveryAttempt existingPaymentAttempt =
                recoveryAttemptRepository.findByRazorpayPaymentId(razorpayPaymentId).orElse(null);
        if (existingPaymentAttempt != null
                && !existingPaymentAttempt.getId().equals(attempt.getId())) {
            log.warn(
                    "Razorpay payment ID already belongs to another " + "recovery attempt. razorpayPaymentId={}, " +
                            "attemptId={}",
                    razorpayPaymentId,
                    attempt.getId()
            );

            return;
        }

        LocalDateTime now = LocalDateTime.now();
        attempt.setRazorpayPaymentId(razorpayPaymentId);
        attempt.setResult(SUCCESS);
        attempt.setAttemptedAt(now);
        recoveryAttemptRepository.save(attempt);
        payment.setStatus(PaymentStatus.RECOVERED);
        payment.setNextRetryAt(null);
        payment.setUpdatedAt(now);
        paymentRepository.save(payment);
        completeRecoveryPlan(payment, now);
        log.info("Payment recovery completed via Razorpay webhook. " +
                        "paymentId={}, razorpayOrderId={}, " +
                        "razorpayPaymentId={}, attemptId={}",
                payment.getId(),
                razorpayOrderId, razorpayPaymentId, attempt.getId()
        );
    }
    private void handlePaymentFailed(JSONObject webhook) {
        JSONObject payload = webhook.optJSONObject("payload");

        if (payload == null) {
            log.warn("payment.failed webhook payload is missing"
            );
            return;
        }
        JSONObject paymentEntity = getEntity(payload, "payment");
        if (paymentEntity == null) {
            log.warn("Payment entity missing in payment.failed webhook");
            return;
        }

        String razorpayPaymentId = paymentEntity.optString("id",
                null);
        String razorpayOrderId = paymentEntity.optString("order_id", null);

        if (razorpayOrderId == null || razorpayOrderId.isBlank()) {
            log.warn("Order ID missing in payment.failed webhook"
            );
            return;
        }
        RecoveryAttempt attempt = recoveryAttemptRepository
                        .findByRazorpayOrderId(razorpayOrderId).orElse(null);

        if (attempt == null) {
            log.warn(
                    "No recovery attempt found for failed Razorpay order. " +
                            "orderId={}",
                    razorpayOrderId
            );
            return;
        }
        if (SUCCESS.equalsIgnoreCase(attempt.getResult())) {
            log.info(
                    "Ignoring payment.failed webhook because attempt " + "is already successful. orderId={}",
                    razorpayOrderId
            );
            return;
        }
        Payment payment = attempt.getPayment();
        if (payment == null) {
            log.warn(
                    "Recovery attempt has no payment. attemptId={}", attempt.getId()
            );

            return;
        }

        LocalDateTime now = LocalDateTime.now();
        if (razorpayPaymentId != null && !razorpayPaymentId.isBlank()) {
            RecoveryAttempt existing =
                    recoveryAttemptRepository.findByRazorpayPaymentId(razorpayPaymentId
                            ).orElse(null);
            if (existing != null && !existing.getId().equals(attempt.getId())) {
                log.warn(
                        "Razorpay payment ID already belongs to another " +
                                "recovery attempt. razorpayPaymentId={}, " +
                                "attemptId={}",
                        razorpayPaymentId,
                        attempt.getId()
                );
                return;
            }
            attempt.setRazorpayPaymentId(razorpayPaymentId);
        }
        attempt.setResult(FAILED);
        attempt.setAttemptedAt(now);
        recoveryAttemptRepository.save(attempt);
        payment.setStatus(PaymentStatus.FAILED);

        payment.setUpdatedAt(now);
        paymentRepository.save(payment);
        log.info(
                "Razorpay payment failure processed. " +
                        "paymentId={}, orderId={}, razorpayPaymentId={}, " + "attemptId={}",
                payment.getId(), razorpayOrderId,
                razorpayPaymentId, attempt.getId()
        );
    }
    private void completeRecoveryPlan(Payment payment, LocalDateTime now) {

        RecoveryPlan plan = recoveryPlanRepository.findByPaymentId(payment.getId()).orElse(null);
        if (plan == null) {
            log.warn("Recovery plan not found for recovered payment. " + "paymentId={}", payment.getId());
            return;
        }
        List<RecoveryPlanStep> steps = recoveryPlanStepRepository.findByRecoveryPlanId(plan.getId());

        for (RecoveryPlanStep step : steps) {
            String status = normalizeStatus(step.getStatus());
            if ("WAITING_FOR_PAYMENT".equals(status) || "PROCESSING".equals(status)
                    || "SCHEDULED".equals(status)) {
                step.setStatus(COMPLETED);
                step.setResult("PAYMENT_CAPTURED_VIA_WEBHOOK");
                step.setExecutedAt(now);
                step.setUpdatedAt(now);
                recoveryPlanStepRepository.save(step);
            }
        }
        plan.setStatus(COMPLETED);
        plan.setNextAction(null);
        plan.setNextActionAt(null);
        plan.setUpdatedAt(now);
        recoveryPlanRepository.save(plan);
    }
    private JSONObject getEntity(JSONObject payload, String entityName) {

        JSONObject wrapper = payload.optJSONObject(entityName);
        if (wrapper == null) {
            return null;
        }
        return wrapper.optJSONObject(
                "entity"
        );
    }
    private String normalizeStatus(String status) {
        if (status == null) {
            return "";
        }
        return status.trim().toUpperCase();
    }
}