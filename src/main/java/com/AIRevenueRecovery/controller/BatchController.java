
package com.AIRevenueRecovery.controller;

import com.AIRevenueRecovery.batch.BatchRun;
import com.AIRevenueRecovery.repository.BatchRunRepository;
import com.AIRevenueRecovery.service.BatchSimulationService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/batch")
public class BatchController {

    private final BatchRunRepository batchRunRepository;
    private final BatchSimulationService batchSimulationService;

    public BatchController(
            BatchRunRepository batchRunRepository,
            BatchSimulationService batchSimulationService) {

        this.batchRunRepository = batchRunRepository;
        this.batchSimulationService = batchSimulationService;
    }

    /**
     * Manually trigger a new batch processing job.
     */
    @PostMapping("/run")
    public ResponseEntity<Map<String, Object>> runBatchJob(
            @RequestParam(defaultValue = "RECOVERY_JOB") String jobName) {

        BatchRun batchRun = BatchRun.builder()
                .jobName(jobName)
                .status("STARTED")
                .startedAt(LocalDateTime.now())
                .totalRecords(0)
                .recordsProcessed(0)
                .recordsFailed(0)
                .build();

        BatchRun savedRun = batchRunRepository.save(batchRun);

        // Trigger batch execution when simulation is implemented.
        // batchSimulationService.runBatchSimulation(savedRun.getId());

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Batch job initiated successfully",
                "batchRunId", savedRun.getId(),
                "status", savedRun.getStatus()
        ));
    }

    /**
     * Check the status of a particular batch run.
     */
    @GetMapping("/status/{id}")
    public ResponseEntity<BatchRun> getBatchStatus(
            @PathVariable Long id) {

        BatchRun batchRun = batchRunRepository.findById(id)
                .orElseThrow(() ->
                        new RuntimeException("Batch run not found with ID: " + id));

        return ResponseEntity.ok(batchRun);
    }

    /**
     * Fetch all previous batch runs.
     */
    @GetMapping("/history")
    public ResponseEntity<List<BatchRun>> getAllBatchRuns() {

        List<BatchRun> runs = batchRunRepository.findAll();

        return ResponseEntity.ok(runs);
    }
}
