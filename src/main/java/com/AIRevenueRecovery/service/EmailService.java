package com.AIRevenueRecovery.service;

import com.AIRevenueRecovery.entity.Customer;
import com.AIRevenueRecovery.entity.Payment;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {
    private final JavaMailSender mailSender;
    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }
    public void sendPaymentRecoveryEmail(
            Payment payment, String recommendation) {
        sendRecoveryEmail(payment, "PAYMENT_RECOVERY", recommendation);
    }
    public void sendPaymentReminderEmail(
            Payment payment,
            String recommendation) {
        sendRecoveryEmail(payment, "PAYMENT_REMINDER",recommendation);
    }

    public void sendAlternatePaymentEmail(
            Payment payment,
            String recommendation) {
        sendRecoveryEmail(payment, "ALTERNATE_PAYMENT", recommendation);
    }
    public void sendRecoverySuccessEmail(
            Payment payment) {

        validatePayment(payment);

        Customer customer = payment.getCustomer();

        String customerName =
                customer.getName() != null
                        ? customer.getName()
                        : "Customer";

        String subject =
                "Payment Recovered Successfully - "
                        + payment.getPaymentId();

        SimpleMailMessage message =
                new SimpleMailMessage();

        message.setTo(customer.getEmail());
        message.setSubject(subject);

        message.setText(
                "Hi " + customerName + ",\n\n"
                        + "Great news! Your payment has been successfully recovered.\n\n"
                        + "Payment ID: " + payment.getPaymentId() + "\n"
                        + "Amount: " + payment.getAmount()
                        + " " + payment.getCurrency() + "\n\n"
                        + "Thank you for completing your payment.\n\n"
                        + "Thank you,\n"
                        + "AI Revenue Recovery"
        );

        mailSender.send(message);
    }

    public void sendRecoveryFailedEmail(
            Payment payment,
            String recommendation) {

        sendRecoveryEmail(
                payment,
                "RECOVERY_FAILED",
                recommendation
        );
    }

    private void sendRecoveryEmail(Payment payment, String communicationType, String recommendation) {
        validatePayment(payment);
        Customer customer = payment.getCustomer();
        String customerName = customer.getName() != null ? customer.getName() : "Customer";
        String failureReason = payment.getFailureReason() != null ? payment.getFailureReason().toString() : "PAYMENT_FAILED";
        String subject;
        switch (communicationType) {
            case "PAYMENT_REMINDER":
                subject = "Payment Reminder - " + payment.getPaymentId();
                break;
            case "ALTERNATE_PAYMENT":
                subject = "Action Required: Update Payment Method - " + payment.getPaymentId();
                break;
            case "RECOVERY_FAILED":
                subject = "Payment Recovery Failed - " + payment.getPaymentId();
                break;

            default:
                subject =
                        "Payment Recovery - "
                                + payment.getPaymentId();
        }

        String recoveryMessage =
                resolveRecoveryMessage(
                        failureReason,
                        recommendation,
                        communicationType
                );

        SimpleMailMessage message =
                new SimpleMailMessage();

        message.setTo(customer.getEmail());
        message.setSubject(subject);

        message.setText(
                "Hi " + customerName + ",\n\n"
                        + recoveryMessage + "\n\n" + "Payment ID: " + payment.getPaymentId() + "\n" + "Amount: " + payment.getAmount()
                        + " " + payment.getCurrency() + "\n" + "Failure reason: " + failureReason + "\n\n" + "Thank you,\n"
                        + "AI Revenue Recovery"
        );
        mailSender.send(message);
    }

    private String resolveRecoveryMessage(
            String failureReason, String recommendation, String communicationType) {
        if (recommendation != null && !recommendation.isBlank()) {
            return recommendation;
        }
        if ("ALTERNATE_PAYMENT".equalsIgnoreCase(
                communicationType)) {
            return "Please update your payment method or use " + "an alternate payment method to complete your payment.";
        }
        if ("RECOVERY_FAILED".equalsIgnoreCase(communicationType)) {
            return "We were unable to recover your payment automatically. "
                    + "Please complete the payment manually using another payment method.";
        }
        switch (failureReason) {
            case "CARD_DECLINED":
                return "Your card payment was declined. " + "Please try another card or payment method.";
            case "INSUFFICIENT_FUNDS":
                return "Your payment could not be completed due to insufficient funds. " + "Please add sufficient funds and try again.";
            case "BANK_ERROR":
                return "Your bank could not process the payment at this time. " + "This may be a temporary issue. Please try again later.";
            case "NETWORK_ERROR":
                return "We experienced a temporary network issue while processing your payment. " + "Please try again later.";
            case "EXPIRED_CARD":
                return "Your card appears to be expired. " + "Please update your card or use another payment method.";
            case "FRAUD_DETECTED":
                return "Your payment could not be processed for security reasons. " + "Please contact your bank or use another payment method.";

            default:
                return "Your payment could not be completed. " + "Please try another payment method.";
        }
    }

    private void validatePayment(Payment payment) {

        if (payment == null) {
            throw new IllegalArgumentException(
                    "Payment cannot be null"
            );
        }

        Customer customer = payment.getCustomer();
        if (customer == null || customer.getEmail() == null || customer.getEmail().isBlank()) {
            throw new IllegalArgumentException("Customer email not found");
        }
    }
}