package com.AIRevenueRecovery.service;

import com.AIRevenueRecovery.entity.CustomerRecoverySummary;
import com.AIRevenueRecovery.entity.Payment;
import com.AIRevenueRecovery.entity.PaymentStatus;
import com.AIRevenueRecovery.exception.CustomerNotFoundException;
import com.AIRevenueRecovery.repository.CustomerRepository;
import com.AIRevenueRecovery.repository.PaymentRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CustomerRecoveryService {

    private final CustomerRepository customerRepository;
    private final PaymentRepository paymentRepository;

    public CustomerRecoveryService(CustomerRepository customerRepository,
            PaymentRepository paymentRepository) {
        this.customerRepository = customerRepository;
        this.paymentRepository = paymentRepository;
    }
    public CustomerRecoverySummary getSummary(String customerId) {
        customerRepository.findByCustomerId(customerId)
                .orElseThrow(() -> new CustomerNotFoundException("Customer not found: " + customerId));
        List<Payment> payments = paymentRepository.findAll()
                .stream()
                .filter(payment -> customerId.equals(payment.getCustomerId())).toList();
        long totalPayments = payments.size();
        long successfulPayments = payments.stream()
                .filter(payment -> payment.getStatus() == PaymentStatus.SUCCESS).count();
        long failedPayments = payments.stream()
                .filter(payment -> payment.getStatus() == PaymentStatus.FAILED)
                .count();
        double totalAmount = payments.stream()
                .mapToDouble(Payment::getAmount)
                .sum();
        double recoveredAmount = payments.stream()
                .filter(payment ->
                        payment.getStatus() == PaymentStatus.SUCCESS)
                .mapToDouble(Payment::getAmount)
                .sum();
        double recoveryRate = totalPayments == 0
                ? 0.0
                : (successfulPayments * 100.0) / totalPayments;
        return new CustomerRecoverySummary(
                customerId, totalPayments, successfulPayments,
                failedPayments, totalAmount, recoveredAmount, recoveryRate
        );
    }
}