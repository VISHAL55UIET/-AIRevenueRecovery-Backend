package com.AIRevenueRecovery.service;

import com.AIRevenueRecovery.dto.RazorpayOrderResponse;
import com.AIRevenueRecovery.dto.RazorpayPaymentVerificationRequest;
import com.AIRevenueRecovery.entity.Customer;
import com.AIRevenueRecovery.entity.Payment;
import com.AIRevenueRecovery.entity.PaymentStatus;
import com.AIRevenueRecovery.entity.RecoveryAttempt;
import com.AIRevenueRecovery.exception.CustomerNotFoundException;
import com.AIRevenueRecovery.exception.PaymentNotFoundException;
import com.AIRevenueRecovery.repository.CustomerRepository;
import com.AIRevenueRecovery.repository.PaymentRepository;
import com.AIRevenueRecovery.repository.RecoveryAttemptRepository;
import com.razorpay.Order;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final CustomerRepository customerRepository;
    private final RecoveryOrchestratorService recoveryOrchestratorService;
    private final PaymentGatewayService paymentGatewayService;
    private final RecoveryAttemptRepository recoveryAttemptRepository;

    public PaymentService(
            PaymentRepository paymentRepository,
            CustomerRepository customerRepository,
            RecoveryOrchestratorService recoveryOrchestratorService,
            PaymentGatewayService paymentGatewayService,
            RecoveryAttemptRepository recoveryAttemptRepository
    ) {

        this.paymentRepository = paymentRepository;
        this.customerRepository = customerRepository;
        this.recoveryOrchestratorService =
                recoveryOrchestratorService;
        this.paymentGatewayService =
                paymentGatewayService;
        this.recoveryAttemptRepository =
                recoveryAttemptRepository;
    }

    public Payment createPayment(Payment payment) {

        if (payment.getCustomerId() != null) {

            String customerId =
                    payment.getCustomerId();

            Customer customer =
                    customerRepository
                            .findByCustomerId(customerId)
                            .orElseThrow(() ->
                                    new CustomerNotFoundException(
                                            "Customer not found with ID: "
                                                    + customerId
                                    )
                            );

            payment.setCustomer(customer);
        }

        Payment savedPayment =
                paymentRepository.save(payment);
        if (savedPayment.getStatus()
                == PaymentStatus.FAILED) {

            recoveryOrchestratorService
                    .startRecovery(savedPayment);
        }

        return savedPayment;
    }

    public List<Payment> getAllPayments() {

        return paymentRepository.findAll();
    }

    public List<Payment> getRecentPayments() {

        return paymentRepository
                .findTop10ByOrderByCreatedAtDesc();
    }

    public Payment getPaymentById(Long id) {

        return paymentRepository
                .findById(id)
                .orElseThrow(() ->
                        new PaymentNotFoundException(
                                "Payment not found with ID: "
                                        + id
                        )
                );
    }

    public Payment updatePayment(
            Payment payment
    ) {

        return paymentRepository.save(payment);
    }

    public void deletePayment(Long id) {

        paymentRepository.deleteById(id);
    }

    /*
     * Create Razorpay order for recovery/payment.
     */
    public RazorpayOrderResponse createRazorpayOrder(
            Long paymentId
    ) {

        Payment payment =
                getPaymentById(paymentId);
        long totalAttempts =
                recoveryAttemptRepository
                        .countByPaymentId(
                                payment.getId()
                        );
        int attemptNumber = payment.getRetryCount() + 1;
        Order order =
                paymentGatewayService.createOrder(
                        payment,
                        attemptNumber
                );

        Long amount =
                ((Number) order.get("amount"))
                        .longValue();

        return new RazorpayOrderResponse(
                order.get("id"),
                payment.getPaymentId(),
                amount,
                order.get("currency"),
                order.get("status")
        );
    }

    @Transactional
    public Payment verifyRazorpayPayment(
            Long paymentId,
            RazorpayPaymentVerificationRequest request
    ) {

        Payment payment =
                getPaymentById(paymentId);

        /*
         * Verify Razorpay signature.
         */
        boolean verified =
                paymentGatewayService.verifyPayment(
                        request.getRazorpayOrderId(),
                        request.getRazorpayPaymentId(),
                        request.getRazorpaySignature()
                );

        if (!verified) {

            throw new IllegalArgumentException(
                    "Razorpay payment verification failed"
            );
        }
        payment.setStatus(
                PaymentStatus.SUCCESS
        );

        payment.setNextRetryAt(null);

        payment.setUpdatedAt(
                LocalDateTime.now()
        );

        Payment savedPayment =
                paymentRepository.save(payment);
        List<RecoveryAttempt> attempts =
                recoveryAttemptRepository
                        .findByPaymentIdOrderByAttemptNumberAsc(
                                payment.getId()
                        );

        if (!attempts.isEmpty()) {

            RecoveryAttempt latestAttempt =
                    attempts.get(
                            attempts.size() - 1
                    );

            /*
             * Mark recovery attempt successful.
             */
            latestAttempt.setResult(
                    "SUCCESS"
            );

            latestAttempt.setAttemptedAt(
                    LocalDateTime.now()
            );

            recoveryAttemptRepository.save(
                    latestAttempt
            );
        }

        System.out.println(
                "================================="
        );

        System.out.println(
                "Payment recovered successfully"
        );

        System.out.println(
                "Payment ID: "
                        + savedPayment.getPaymentId()
        );

        System.out.println(
                "Database ID: "
                        + savedPayment.getId()
        );

        System.out.println(
                "Razorpay Payment ID: "
                        + request.getRazorpayPaymentId()
        );

        System.out.println(
                "Status: "
                        + savedPayment.getStatus()
        );

        System.out.println(
                "================================="
        );

        return savedPayment;
    }
}