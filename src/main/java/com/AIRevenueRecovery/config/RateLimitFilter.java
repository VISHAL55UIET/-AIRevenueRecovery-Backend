package com.AIRevenueRecovery.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final int PAYMENT_LIMIT = 30;
    private static final int DEFAULT_LIMIT = 60;

    private static final long WINDOW_SECONDS = 60;

    private final Map<String, RateLimitEntry> clients =
            new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String path = request.getRequestURI();

        // Only protect API endpoints
        if (!path.startsWith("/api/")) {
            filterChain.doFilter(request, response);
            return;
        }

        int limit = getLimit(path);

        String clientKey =
                request.getRemoteAddr() + ":" + getCategory(path);

        RateLimitEntry entry = clients.computeIfAbsent(
                clientKey,
                key -> new RateLimitEntry()
        );

        synchronized (entry) {

            long now = Instant.now().getEpochSecond();

            // Reset after 60 seconds
            if (now - entry.windowStart >= WINDOW_SECONDS) {
                entry.windowStart = now;
                entry.requestCount = 0;
            }

            if (entry.requestCount >= limit) {

                long retryAfter =
                        WINDOW_SECONDS -
                                (now - entry.windowStart);

                response.setStatus(
                        HttpStatus.TOO_MANY_REQUESTS.value()
                );

                response.setContentType("application/json");

                response.setHeader(
                        "Retry-After",
                        String.valueOf(Math.max(retryAfter, 1))
                );

                response.setHeader(
                        "X-RateLimit-Limit",
                        String.valueOf(limit)
                );

                response.setHeader(
                        "X-RateLimit-Remaining",
                        "0"
                );

                response.getWriter().write("""
                    {
                      "error": "Too Many Requests",
                      "message": "Rate limit exceeded. Please try again later.",
                      "retryAfterSeconds": %d
                    }
                    """.formatted(
                        Math.max(retryAfter, 1)
                ));

                return;
            }

            entry.requestCount++;

            response.setHeader(
                    "X-RateLimit-Limit",
                    String.valueOf(limit)
            );

            response.setHeader(
                    "X-RateLimit-Remaining",
                    String.valueOf(
                            Math.max(
                                    limit - entry.requestCount,
                                    0
                            )
                    )
            );
        }

        filterChain.doFilter(request, response);
    }

    private int getLimit(String path) {

        // Payment APIs: stricter limit
        if (path.startsWith("/api/payments")) {
            return PAYMENT_LIMIT;
        }

        // Other APIs
        return DEFAULT_LIMIT;
    }

    private String getCategory(String path) {

        if (path.startsWith("/api/payments")) {
            return "payments";
        }

        return "general";
    }

    private static class RateLimitEntry {

        private long windowStart =
                Instant.now().getEpochSecond();

        private int requestCount = 0;
    }
}