package com.AIRevenueRecovery.exception;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GlobalExceptionHandlerTest {

    @Test
    void shouldReturn429ForRateLimitException() {

        GlobalExceptionHandler handler =
                new GlobalExceptionHandler();

        RateLimiter rateLimiter =
                RateLimiter.ofDefaults("paymentApi");

        RequestNotPermitted exception =
                RequestNotPermitted.createRequestNotPermitted(
                        rateLimiter
                );

        ResponseEntity<Map<String, Object>> response =
                handler.handleRateLimit(exception);

        assertEquals(
                HttpStatus.TOO_MANY_REQUESTS,
                response.getStatusCode()
        );

        assertEquals(
                429,
                response.getBody().get("status")
        );

        assertEquals(
                "Too Many Requests",
                response.getBody().get("error")
        );
    }
}