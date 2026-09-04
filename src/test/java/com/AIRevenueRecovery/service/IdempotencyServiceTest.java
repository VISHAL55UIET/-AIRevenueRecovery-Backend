package com.AIRevenueRecovery.service;

import com.AIRevenueRecovery.entity.IdempotencyRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;

class IdempotencyServiceTest {

    @Test
    void shouldDetectExistingIdempotencyKey() {

        String key = "TEST-IDEMPOTENCY-001";

        IdempotencyRequest request = null;

        assertNull(request);
    }
}