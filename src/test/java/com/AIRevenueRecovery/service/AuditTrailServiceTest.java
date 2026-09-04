package com.AIRevenueRecovery.service;

import com.AIRevenueRecovery.entity.AuditTrail;
import com.AIRevenueRecovery.repository.AuditTrailRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditTrailServiceTest {

    @Mock
    private AuditTrailRepository auditTrailRepository;

    @InjectMocks
    private AuditTrailService auditTrailService;

    @Test
    void shouldRecordAuditTrail() {

        AuditTrail savedAudit = new AuditTrail();

        savedAudit.setPaymentId(37L);
        savedAudit.setSagaId("SAGA-TEST-001");
        savedAudit.setPreviousState("AI_DECISION");
        savedAudit.setCurrentState("AI_DECISION_COMPLETED");
        savedAudit.setAction("SEND_PAYMENT_REMINDER");

        when(auditTrailRepository.save(any(AuditTrail.class)))
                .thenReturn(savedAudit);

        AuditTrail result = auditTrailService.record(
                37L,
                "SAGA-TEST-001",
                "AI_DECISION",
                "AI_DECISION_COMPLETED",
                "SEND_PAYMENT_REMINDER"
        );

        assertNotNull(result);
        assertEquals(37L, result.getPaymentId());
        assertEquals("SAGA-TEST-001", result.getSagaId());
        assertEquals("AI_DECISION", result.getPreviousState());
        assertEquals(
                "AI_DECISION_COMPLETED",
                result.getCurrentState()
        );
        assertEquals(
                "SEND_PAYMENT_REMINDER",
                result.getAction()
        );

        verify(auditTrailRepository, times(1))
                .save(any(AuditTrail.class));
    }

    @Test
    void shouldGetAuditTrailByPaymentId() {

        AuditTrail audit1 = new AuditTrail();
        audit1.setPaymentId(37L);
        audit1.setPreviousState(null);
        audit1.setCurrentState("STARTED");

        AuditTrail audit2 = new AuditTrail();
        audit2.setPaymentId(37L);
        audit2.setPreviousState("STARTED");
        audit2.setCurrentState("PAYMENT_VALIDATION");

        List<AuditTrail> audits = List.of(audit1, audit2);

        when(auditTrailRepository
                .findByPaymentIdOrderByOccurredAtAsc(37L))
                .thenReturn(audits);

        List<AuditTrail> result =
                auditTrailService.getByPaymentId(37L);

        assertNotNull(result);
        assertEquals(2, result.size());

        assertEquals("STARTED",
                result.get(0).getCurrentState());

        assertEquals("PAYMENT_VALIDATION",
                result.get(1).getCurrentState());

        verify(auditTrailRepository, times(1))
                .findByPaymentIdOrderByOccurredAtAsc(37L);
    }
}