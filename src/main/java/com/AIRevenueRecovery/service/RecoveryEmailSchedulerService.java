package com.AIRevenueRecovery.service;

import com.AIRevenueRecovery.entity.Payment;
import com.AIRevenueRecovery.entity.PaymentStatus;
import com.AIRevenueRecovery.repository.PaymentRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class RecoveryEmailSchedulerService {
    private final PaymentRepository paymentRepository;
    private final EmailService emailService;
    private final AIRecoveryDecisionService aiRecoveryDecisionService;
    public RecoveryEmailSchedulerService(
            PaymentRepository paymentRepository,
            EmailService emailService,
            AIRecoveryDecisionService aiRecoveryDecisionService) {
        this.paymentRepository = paymentRepository;
        this.emailService = emailService;
        this.aiRecoveryDecisionService = aiRecoveryDecisionService;
    }
    @Scheduled(fixedRate = 60000)
    public void sendRecoveryEmails() {
        LocalDateTime cutoffTime = LocalDateTime.now().minusMinutes(24);
        List<Payment> payments = paymentRepository.findPaymentsReadyForRecoveryEmail(PaymentStatus.FAILED, cutoffTime);
        for (Payment payment : payments) {

            try {

                /*
                 * Ask AI / fallback rule engine for a decision.
                 */
                AIRecoveryDecisionService.RecoveryDecision decision =
                        aiRecoveryDecisionService.decide(payment);

                System.out.println("======================================");
                System.out.println("AI RECOVERY DECISION");
                System.out.println("Payment: " + payment.getPaymentId());
                System.out.println("Action: " + decision.action());
                System.out.println("Confidence: " + decision.confidence());
                System.out.println("Recommendation: " + decision.recommendation());
                System.out.println("======================================");

                /*
                 * IMPORTANT:
                 *
                 * Do not send an email for every AI decision.
                 * The action selected by AI must control what happens.
                 */
                switch (decision.action()) {

                    case "SEND_PAYMENT_REMINDER":

                        sendRecoveryEmail(payment, decision);

                        break;

                    case "REQUEST_ALTERNATE_PAYMENT":

                        sendRecoveryEmail(payment, decision);

                        break;

                    case "AUTOMATIC_RETRY":

                        /*
                         * Email scheduler should NOT send an email
                         * for automatic retry.
                         *
                         * A separate retry service/scheduler should
                         * execute the payment retry.
                         */
                        System.out.println(
                                "Automatic retry recommended for payment: "
                                        + payment.getPaymentId()
                        );

                        break;

                    case "BLOCK_RECOVERY":

                        /*
                         * Fraud / security case.
                         * Do not contact customer through recovery email.
                         */
                        System.out.println(
                                "Recovery blocked for payment: "
                                        + payment.getPaymentId()
                        );

                        break;

                    default:

                        System.err.println(
                                "Unknown recovery action: "
                                        + decision.action()
                                        + " for payment "
                                        + payment.getPaymentId()
                        );
                }

            } catch (Exception exception) {

                System.err.println(
                        "Failed recovery processing for payment "
                                + payment.getPaymentId()
                                + ": "
                                + exception.getMessage()
                );
            }
        }
    }

    private void sendRecoveryEmail(
            Payment payment,
            AIRecoveryDecisionService.RecoveryDecision decision
    ) {

        /*
         * Extra protection against duplicate emails.
         */
        if (payment.getRecoveryEmailSentAt() != null) {

            System.out.println(
                    "Recovery email already sent for payment: "
                            + payment.getPaymentId()
            );

            return;
        }

        emailService.sendPaymentRecoveryEmail(
                payment,
                decision.recommendation()
        );

        LocalDateTime now = LocalDateTime.now();

        payment.setRecoveryEmailSentAt(now);
        payment.setUpdatedAt(now);

        paymentRepository.save(payment);

        System.out.println(
                "Recovery email sent for payment: "
                        + payment.getPaymentId()
        );
    }
}