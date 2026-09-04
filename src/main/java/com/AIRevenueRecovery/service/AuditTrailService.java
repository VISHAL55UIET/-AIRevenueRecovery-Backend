package com.AIRevenueRecovery.service;

import com.AIRevenueRecovery.entity.AuditTrail;
import com.AIRevenueRecovery.repository.AuditTrailRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AuditTrailService {

    private final AuditTrailRepository auditTrailRepository;

    public AuditTrailService(
            AuditTrailRepository auditTrailRepository) {
        this.auditTrailRepository = auditTrailRepository;
    }

    @Transactional
    public AuditTrail record(
            Long paymentId,
            String sagaId,
            String previousState,
            String currentState,
            String action) {

        AuditTrail auditTrail = new AuditTrail();

        auditTrail.setPaymentId(paymentId);
        auditTrail.setSagaId(sagaId);
        auditTrail.setPreviousState(previousState);
        auditTrail.setCurrentState(currentState);
        auditTrail.setAction(action);

        return auditTrailRepository.save(auditTrail);
    }

    public List<AuditTrail> getByPaymentId(Long paymentId) {
        return auditTrailRepository
                .findByPaymentIdOrderByOccurredAtAsc(paymentId);
    }
}