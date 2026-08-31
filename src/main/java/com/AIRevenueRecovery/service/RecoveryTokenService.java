package com.AIRevenueRecovery.service;

import com.AIRevenueRecovery.entity.Payment;
import com.AIRevenueRecovery.entity.RecoveryToken;
import com.AIRevenueRecovery.repository.RecoveryTokenRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class RecoveryTokenService {
    private final RecoveryTokenRepository recoveryTokenRepository;
    public RecoveryTokenService(RecoveryTokenRepository recoveryTokenRepository) {
        this.recoveryTokenRepository = recoveryTokenRepository;
    }
    public RecoveryToken createToken(Payment payment) {
        RecoveryToken recoveryToken = new RecoveryToken();
        recoveryToken.setToken(UUID.randomUUID().toString());
        recoveryToken.setPayment(payment);
        recoveryToken.setCreatedAt(LocalDateTime.now());
        recoveryToken.setExpiresAt(LocalDateTime.now().plusHours(24));
        recoveryToken.setUsed(false);
        return recoveryTokenRepository.save(recoveryToken);
    }
    public RecoveryToken getValidToken(String token) {
        RecoveryToken recoveryToken = recoveryTokenRepository.findByToken(token)
                        .orElseThrow(() -> new RuntimeException("Recovery link is invalid"));
        if (recoveryToken.isUsed()) {
            throw new RuntimeException(
                    "Recovery link has already been used"
            );
        }
        if (recoveryToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("Recovery link has expired");
        }
        return recoveryToken;
    }
    public void markAsUsed(RecoveryToken recoveryToken) {
        recoveryToken.setUsed(true);
        recoveryTokenRepository.save(recoveryToken);
    }
}