package com.AIRevenueRecovery.service;

import com.AIRevenueRecovery.entity.IdempotencyRequest;
import com.AIRevenueRecovery.entity.Payment;
import com.AIRevenueRecovery.repository.IdempotencyRequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;

@Service
public class IdempotencyService {

    private final IdempotencyRequestRepository idempotencyRequestRepository;

    public IdempotencyService(
            IdempotencyRequestRepository idempotencyRequestRepository) {

        this.idempotencyRequestRepository =
                idempotencyRequestRepository;
    }

    @Transactional
    public IdempotencyRequest createRequest(
            String idempotencyKey,
            String requestType,
            Long resourceId) {

        validateKey(idempotencyKey);

        if (requestType == null || requestType.isBlank()) {
            throw new RuntimeException("Request type is required");
        }

        IdempotencyRequest existingRequest =
                idempotencyRequestRepository
                        .findByIdempotencyKey(idempotencyKey)
                        .orElse(null);

        if (existingRequest != null) {
            return existingRequest;
        }

        String requestHash = generateRequestHash(
                idempotencyKey,
                requestType,
                resourceId
        );

        IdempotencyRequest request =
                new IdempotencyRequest();

        request.setIdempotencyKey(
                idempotencyKey
        );

        request.setRequestType(
                requestType
        );

        request.setRequestHash(
                requestHash
        );

        request.setCreatedAt(
                LocalDateTime.now()
        );

        return idempotencyRequestRepository.save(
                request
        );
    }

    public IdempotencyRequest getByKey(
            String idempotencyKey) {

        validateKey(idempotencyKey);

        return idempotencyRequestRepository
                .findByIdempotencyKey(idempotencyKey)
                .orElse(null);
    }

    @Transactional
    public IdempotencyRequest attachPayment(
            String idempotencyKey,
            Payment payment) {

        IdempotencyRequest request =
                idempotencyRequestRepository
                        .findByIdempotencyKey(idempotencyKey)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Idempotency request not found"
                                ));

        request.setPayment(payment);

        return idempotencyRequestRepository.save(
                request
        );
    }

    private String generateRequestHash(
            String idempotencyKey,
            String requestType,
            Long resourceId) {

        String input =
                idempotencyKey
                        + ":"
                        + requestType
                        + ":"
                        + String.valueOf(resourceId);

        try {

            MessageDigest digest =
                    MessageDigest.getInstance("SHA-256");

            byte[] hash =
                    digest.digest(
                            input.getBytes(StandardCharsets.UTF_8)
                    );

            StringBuilder hexString =
                    new StringBuilder();

            for (byte b : hash) {

                String hex =
                        Integer.toHexString(
                                0xff & b
                        );

                if (hex.length() == 1) {
                    hexString.append('0');
                }

                hexString.append(hex);
            }

            return hexString.toString();

        } catch (NoSuchAlgorithmException e) {

            throw new RuntimeException(
                    "SHA-256 algorithm not available",
                    e
            );
        }
    }

    private void validateKey(
            String idempotencyKey) {

        if (idempotencyKey == null
                || idempotencyKey.isBlank()) {

            throw new RuntimeException(
                    "Idempotency key is required"
            );
        }

        if (idempotencyKey.length() > 255) {

            throw new RuntimeException(
                    "Idempotency key must not exceed 255 characters"
            );
        }
    }
}