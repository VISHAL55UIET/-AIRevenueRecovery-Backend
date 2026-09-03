package com.AIRevenueRecovery.repository;

import com.AIRevenueRecovery.entity.RecoverySaga;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RecoverySagaRepository extends JpaRepository<RecoverySaga, Long> {

    Optional<RecoverySaga> findBySagaId(String sagaId);
    Optional<RecoverySaga> findByPaymentIdAndStatus(Long paymentId, String status);
    Optional<RecoverySaga> findFirstByPaymentIdOrderByIdDesc(Long paymentId);
}