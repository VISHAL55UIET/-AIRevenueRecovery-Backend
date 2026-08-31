package com.AIRevenueRecovery.repository;

import com.AIRevenueRecovery.entity.IdempotencyRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
public interface IdempotencyRequestRepository extends JpaRepository<IdempotencyRequest, Long> {
    Optional<IdempotencyRequest> findByIdempotencyKey(String idempotencyKey);
    boolean existsByIdempotencyKey(String idempotencyKey);
    Optional<IdempotencyRequest> findByIdempotencyKeyAndRequestType(String idempotencyKey, String requestType);
}