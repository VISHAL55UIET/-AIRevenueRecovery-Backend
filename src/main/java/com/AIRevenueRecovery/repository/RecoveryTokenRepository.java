package com.AIRevenueRecovery.repository;

import com.AIRevenueRecovery.entity.RecoveryToken;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface RecoveryTokenRepository
        extends JpaRepository<RecoveryToken, Long> {
    Optional<RecoveryToken> findByToken(String token);
}