
package com.AIRevenueRecovery.service;

import com.AIRevenueRecovery.batch.BatchRun;
import com.AIRevenueRecovery.repository.BatchRunRepository;
import com.AIRevenueRecovery.repository.PaymentRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class BatchSimulationService {

    private final BatchRunRepository batchRunRepository;
    private final PaymentRepository paymentRepository;

    public BatchSimulationService(
            BatchRunRepository batchRunRepository,
            PaymentRepository paymentRepository) {

        this.batchRunRepository = batchRunRepository;
        this.paymentRepository = paymentRepository;
    }

    @Transactional
    public BatchRun runBatchSimulation(Long batchRunId) {

        BatchRun batchRun = batchRunRepository.findById(batchRunId)
                .orElseThrow(() ->
                        new RuntimeException(
                                "Batch run not found: " + batchRunId
                        ));

        try {
            batchRun.setStatus("PROCESSING");

            long totalRecords = paymentRepository.count();

            batchRun.setTotalRecords((int) totalRecords);
            batchRun.setRecordsProcessed(0);
            batchRun.setRecordsFailed(0);

            batchRunRepository.save(batchRun);

            // Fetch and process payments here.
            // Recovery service integration will be added here.

            batchRun.setRecordsProcessed((int) totalRecords);
            batchRun.setStatus("COMPLETED");

        } catch (Exception e) {

            batchRun.setStatus("FAILED");
            batchRun.setRecordsFailed(
                    batchRun.getTotalRecords()
                            - batchRun.getRecordsProcessed()
            );

        }

        batchRun.setCompletedAt(LocalDateTime.now());

        return batchRunRepository.save(batchRun);
    }
}
