package com.AIRevenueRecovery;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class RateLimiterTest {

    @Test
    void shouldAllowRequestsWithinLimit() {

        RateLimiterConfig config =
                RateLimiterConfig.custom()
                        .limitForPeriod(10)
                        .limitRefreshPeriod(Duration.ofMinutes(1))
                        .timeoutDuration(Duration.ZERO)
                        .build();

        RateLimiter rateLimiter =
                RateLimiter.of("paymentApiTest", config);

        for (int i = 0; i < 10; i++) {
            assertTrue(
                    rateLimiter.acquirePermission(),
                    "Request " + (i + 1) + " should be allowed"
            );
        }
    }

    @Test
    void shouldRejectRequestAfterLimitIsExceeded() {

        RateLimiterConfig config =
                RateLimiterConfig.custom()
                        .limitForPeriod(10)
                        .limitRefreshPeriod(Duration.ofMinutes(1))
                        .timeoutDuration(Duration.ZERO)
                        .build();

        RateLimiter rateLimiter =
                RateLimiter.of("paymentApiTest", config);

        for (int i = 0; i < 10; i++) {
            assertTrue(rateLimiter.acquirePermission());
        }

        assertFalse(
                rateLimiter.acquirePermission(),
                "11th request should be rejected"
        );
    }
}