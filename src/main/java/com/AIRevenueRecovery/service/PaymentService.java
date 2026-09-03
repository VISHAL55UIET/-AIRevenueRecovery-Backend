package com.AIRevenueRecovery.service;

import com.AIRevenueRecovery.dto.RazorpayOrderResponse;
import com.AIRevenueRecovery.dto.RazorpayPaymentVerificationRequest;
import com.AIRevenueRecovery.entity.Customer;
import com.AIRevenueRecovery.entity.Payment;
import com.AIRevenueRecovery.entity.PaymentStatus;
import com.AIRevenueRecovery.entity.RecoveryAttempt;
import com.AIRevenueRecovery.entity.RecoveryPlan;
import com.AIRevenueRecovery.entity.RecoveryPlanStep;
import com.AIRevenueRecovery.exception.CustomerNotFoundException;
import com.AIRevenueRecovery.exception.PaymentNotFoundException;
import com.AIRevenueRecovery.repository.CustomerRepository;
import com.AIRevenueRecovery.repository.PaymentRepository;
import com.AIRevenueRecovery.repository.RecoveryAttemptRepository;
import com.AIRevenueRecovery.repository.RecoveryPlanRepository;
import com.AIRevenueRecovery.repository.RecoveryPlanStepRepository;
import com.razorpay.Order;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
@Service
public class PaymentService {
    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);
    private final PaymentRepository paymentRepository;
    private final CustomerRepository customerRepository;
    private final RecoveryOrchestratorService recoveryOrchestratorService;
    private final PaymentGatewayService paymentGatewayService;
    private final RecoveryAttemptRepository recoveryAttemptRepository;
    private final RecoveryPlanRepository recoveryPlanRepository;
    private final RecoveryPlanStepRepository recoveryPlanStepRepository;

    public PaymentService(
            PaymentRepository paymentRepository,
            CustomerRepository customerRepository,
            RecoveryOrchestratorService recoveryOrchestratorService,
            PaymentGatewayService paymentGatewayService,
            RecoveryAttemptRepository recoveryAttemptRepository,
            RecoveryPlanRepository recoveryPlanRepository,
            RecoveryPlanStepRepository recoveryPlanStepRepository) {

        this.paymentRepository = paymentRepository;
        this.customerRepository = customerRepository;
        this.recoveryOrchestratorService = recoveryOrchestratorService;
        this.paymentGatewayService = paymentGatewayService;
        this.recoveryAttemptRepository = recoveryAttemptRepository;
        this.recoveryPlanRepository = recoveryPlanRepository;
        this.recoveryPlanStepRepository = recoveryPlanStepRepository;
    }

    @Transactional
    public Payment createPayment(Payment payment) {

        if (payment == null) {
            throw new IllegalArgumentException("Payment is required");
        }

        if (payment.getPaymentId() == null || payment.getPaymentId().isBlank()) {

            throw new IllegalArgumentException("Payment ID is required");
        }

        if (payment.getAmount() == null || payment.getAmount() <= 0) {
            throw new IllegalArgumentException("Payment amount must be greater than zero");
        }

        if (payment.getCurrency() == null || payment.getCurrency().isBlank()) {
            throw new IllegalArgumentException("Payment currency is required");
        }

        if (payment.getRetryCount() == null) {
            payment.setRetryCount(0);
        }

        if (payment.getCustomerId() != null && !payment.getCustomerId().isBlank()) {
            String customerId = payment.getCustomerId();
            Customer customer = customerRepository.findByCustomerId(customerId).orElseThrow(() ->
                                    new CustomerNotFoundException("Customer not found with ID: " + customerId));
            payment.setCustomer(customer);
        }

        Payment savedPayment =
                paymentRepository.save(payment);

        if (savedPayment.getStatus() == PaymentStatus.FAILED) {

            recoveryOrchestratorService
                    .startRecovery(savedPayment);
        }

        return savedPayment;
    }
    public List<Payment> getAllPayments() {
        return paymentRepository.findAll();
    }
    public List<Payment> getRecentPayments() {
        return paymentRepository.findTop10ByOrderByCreatedAtDesc();
    }
    public Payment getPaymentById(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("Payment ID is required");
        }
        return paymentRepository.findById(id).orElseThrow(() -> new PaymentNotFoundException("Payment not found with ID: "
                                        + id));
    }
    @Transactional
    public Payment updatePayment(Payment payment) {
        if (payment == null || payment.getId() == null) {
            throw new IllegalArgumentException(
                    "Payment with valid ID is required"
            );
        }
        getPaymentById(payment.getId());
        return paymentRepository.save(payment);
    }
    @Transactional
    public void deletePayment(Long id) {
        Payment payment = getPaymentById(id);
        paymentRepository.delete(payment);
    }
    @Transactional
    public RazorpayOrderResponse createRazorpayOrder(Long paymentId) {
        Payment payment = getPaymentById(paymentId);
        if (payment.getStatus() == PaymentStatus.RECOVERED) {
            throw new IllegalStateException("Payment has already been recovered");
        }
        if (payment.getStatus() != PaymentStatus.FAILED
                && payment.getStatus() != PaymentStatus.RETRYING) {
            throw new IllegalStateException(
                    "Razorpay order cannot be created for payment status: " + payment.getStatus());
        }

        int attemptNumber = payment.getRetryCount() == null ? 1 : payment.getRetryCount() + 1;
        RecoveryAttempt attempt =
                recoveryAttemptRepository.findByPaymentIdAndAttemptNumber(payment.getId(), attemptNumber).orElse(null);
        if (attempt != null
                && attempt.getRazorpayOrderId() != null
                && !attempt.getRazorpayOrderId().isBlank()) {
            return buildOrderResponseFromAttempt(payment, attempt);
        }
        if (attempt == null) {
            attempt = new RecoveryAttempt();
            attempt.setPayment(payment);
            attempt.setFailureReason(payment.getFailureReason());
            attempt.setAttemptNumber(attemptNumber);
            attempt.setAction("RAZORPAY_CHECKOUT");
            attempt.setResult("PENDING_CHECKOUT");
            attempt.setAttemptedAt(LocalDateTime.now());

            attempt = recoveryAttemptRepository.save(
                            attempt
                    );
        }

        Order order = paymentGatewayService.createOrder(payment, attemptNumber);
        String razorpayOrderId = order.get("id");

        if (razorpayOrderId == null || razorpayOrderId.isBlank()) {

            throw new IllegalStateException(
                    "Razorpay did not return an order ID"
            );
        }

        attempt.setRazorpayOrderId(razorpayOrderId);
        attempt.setResult("PENDING_CHECKOUT");
        attempt.setAttemptedAt(LocalDateTime.now());
        recoveryAttemptRepository.save(attempt);
        log.info("Razorpay order created. paymentId={}, " +
                        "attemptNumber={}, razorpayOrderId={}", payment.getPaymentId(), attemptNumber, razorpayOrderId);
        Long amount = ((Number) order.get("amount")).longValue();
        return new RazorpayOrderResponse(
                razorpayOrderId, payment.getPaymentId(),
                amount, order.get("currency"), order.get("status")
        );
    }
    @Transactional
    public Payment verifyRazorpayPayment(Long paymentId, RazorpayPaymentVerificationRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Payment verification request is required");
        }
        if (request.getRazorpayOrderId() == null || request.getRazorpayOrderId().isBlank()) {
            throw new IllegalArgumentException("Razorpay order ID is required");
        }

        if (request.getRazorpayPaymentId() == null || request.getRazorpayPaymentId().isBlank()) {

            throw new IllegalArgumentException(
                    "Razorpay payment ID is required"
            );
        }
        if (request.getRazorpaySignature() == null || request.getRazorpaySignature().isBlank()) {
            throw new IllegalArgumentException(
                    "Razorpay signature is required"
            );
        }
        Payment payment = getPaymentById(paymentId);
        RecoveryAttempt attempt = recoveryAttemptRepository.findByRazorpayOrderId(request.getRazorpayOrderId()).orElseThrow(() -> new IllegalArgumentException("Recovery attempt not found for Razorpay order: "
                                                + request.getRazorpayOrderId()));
        if (attempt.getPayment() == null
                || attempt.getPayment().getId() == null || !attempt.getPayment().getId().equals(payment.getId())) {
            throw new IllegalArgumentException("Razorpay order does not belong to this payment");
        }

        if ("SUCCESS".equalsIgnoreCase(attempt.getResult()) && payment.getStatus() == PaymentStatus.RECOVERED) {
            return payment;
        }
        boolean verified = paymentGatewayService.verifyPayment(request.getRazorpayOrderId(), request.getRazorpayPaymentId(), request.getRazorpaySignature());

        if (!verified) {
            throw new IllegalArgumentException(
                    "Razorpay payment verification failed"
            );
        }
        LocalDateTime now = LocalDateTime.now();
        attempt.setResult("SUCCESS");
        attempt.setRazorpayPaymentId(request.getRazorpayPaymentId());
        attempt.setAttemptedAt(now);
        recoveryAttemptRepository.save(attempt);
        payment.setStatus(PaymentStatus.RECOVERED);
        payment.setNextRetryAt(null);
        payment.setUpdatedAt(now);
        Payment savedPayment = paymentRepository.save(payment);
        RecoveryPlan recoveryPlan = recoveryPlanRepository.findByPaymentId(payment.getId()
                        )
                        .orElse(null);

        if (recoveryPlan != null) {
            RecoveryPlanStep waitingStep = findWaitingStep(recoveryPlan);
            if (waitingStep != null) {
                waitingStep.setStatus("COMPLETED"
                );
                waitingStep.setResult("PAYMENT_VERIFIED"
                );
                waitingStep.setExecutedAt(now);
                waitingStep.setUpdatedAt(now);
                recoveryPlanStepRepository.save(waitingStep);
                recoveryPlan.setCurrentStep(waitingStep.getStepNumber());
            }

            recoveryPlan.setStatus(
                    "COMPLETED"
            );

            recoveryPlan.setNextAction(null);
            recoveryPlan.setNextActionAt(null);
            recoveryPlan.setUpdatedAt(now);
        }

        log.info(
                "Payment recovered successfully. " +
                        "paymentId={}, databaseId={}, " + "razorpayOrderId={}, razorpayPaymentId={}",
                savedPayment.getPaymentId(), savedPayment.getId(),
                request.getRazorpayOrderId(), request.getRazorpayPaymentId()
        );
        return savedPayment;
    }
    @Transactional
    public Payment capturePayment(Long paymentId, String razorpayPaymentId) {
        if (razorpayPaymentId == null || razorpayPaymentId.isBlank()) {
            throw new IllegalArgumentException(
                    "Razorpay payment ID is required"
            );
        }
        Payment payment = getPaymentById(paymentId);
        if (payment.getStatus() == PaymentStatus.RECOVERED) {
            return payment;
        }
        if (payment.getStatus() != PaymentStatus.FAILED && payment.getStatus() != PaymentStatus.RETRYING) {
            throw new IllegalStateException("Payment cannot be captured with status: " + payment.getStatus());
        }
        long amountInPaise = Math.round(payment.getAmount() * 100);
        boolean captured = paymentGatewayService.capturePayment(razorpayPaymentId, amountInPaise);
        if (!captured) {
            throw new IllegalStateException("Razorpay payment capture failed");
        }
        LocalDateTime now = LocalDateTime.now();
        payment.setStatus(PaymentStatus.RECOVERED
        );
        payment.setNextRetryAt(null);
        payment.setUpdatedAt(now);
        Payment savedPayment = paymentRepository.save(payment);
        log.info("Payment captured and recovered. " + "paymentId={}, razorpayPaymentId={}, amount={}",
                savedPayment.getPaymentId(), razorpayPaymentId,
                savedPayment.getAmount()
        );

        return savedPayment;
    }


    public com.razorpay.Payment fetchPayment(
            String razorpayPaymentId) {

        if (razorpayPaymentId == null || razorpayPaymentId.isBlank()) {
            throw new IllegalArgumentException(
                    "Razorpay payment ID is required"
            );
        }

        return paymentGatewayService.fetchPayment(razorpayPaymentId);
    }

    @Transactional
    public Payment refundPayment(Long paymentId, String razorpayPaymentId, Double refundAmount) {
        if (razorpayPaymentId == null || razorpayPaymentId.isBlank()) {
            throw new IllegalArgumentException("Razorpay payment ID is required");
        }
        Payment payment = getPaymentById(paymentId);
        if (payment.getStatus() != PaymentStatus.RECOVERED) {
            throw new IllegalStateException("Only recovered payments can be refunded");
        }
        if (refundAmount == null
                || refundAmount <= 0) {
            throw new IllegalArgumentException(
                    "Refund amount must be greater than zero"
            );
        }
        if (refundAmount > payment.getAmount()) {
            throw new IllegalArgumentException(
                    "Refund amount cannot exceed payment amount"
            );
        }
        long refundAmountInPaise = Math.round(refundAmount * 100);
        boolean refunded = paymentGatewayService.refundPayment(razorpayPaymentId, refundAmountInPaise);
        if (!refunded) {
            throw new IllegalStateException(
                    "Razorpay refund failed"
            );
        }

        LocalDateTime now = LocalDateTime.now();
        payment.setUpdatedAt(now);

        Payment savedPayment =
                paymentRepository.save(payment);

        log.info("Payment refunded successfully. " +
                        "paymentId={}, razorpayPaymentId={}, refundAmount={}",
                savedPayment.getPaymentId(),
                razorpayPaymentId,
                refundAmount
        );
        return savedPayment;
    }
    private RecoveryPlanStep findWaitingStep(RecoveryPlan recoveryPlan) {
        if (recoveryPlan == null || recoveryPlan.getId() == null) {
            return null;
        }
        List<RecoveryPlanStep> steps = recoveryPlanStepRepository.findByRecoveryPlanId(recoveryPlan.getId());
        for (RecoveryPlanStep step : steps) {
            if ("WAITING_FOR_PAYMENT".equalsIgnoreCase(step.getStatus())) {
                return step;
            }
        }
        return null;
    }
    private RazorpayOrderResponse buildOrderResponseFromAttempt(Payment payment, RecoveryAttempt attempt) {
        long amountInPaise = Math.round(payment.getAmount() * 100);
        return new RazorpayOrderResponse(
                attempt.getRazorpayOrderId(), payment.getPaymentId(), amountInPaise, payment.getCurrency(), "created");
    }
}