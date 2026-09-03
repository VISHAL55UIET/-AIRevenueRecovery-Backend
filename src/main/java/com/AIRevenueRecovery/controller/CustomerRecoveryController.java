package com.AIRevenueRecovery.controller;

import com.AIRevenueRecovery.entity.CustomerRecoverySummary;
import com.AIRevenueRecovery.service.CustomerRecoveryService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/customers")
public class CustomerRecoveryController {

    private final CustomerRecoveryService customerRecoveryService;
    public CustomerRecoveryController(
            CustomerRecoveryService customerRecoveryService) {
        this.customerRecoveryService = customerRecoveryService;
    }
    @GetMapping("/{customerId}/recovery-summary")
    public CustomerRecoverySummary getRecoverySummary(
            @PathVariable String customerId) {

        return customerRecoveryService.getSummary(customerId);
    }
}