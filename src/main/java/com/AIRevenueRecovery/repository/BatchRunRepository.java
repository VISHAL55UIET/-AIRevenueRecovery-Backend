package com.AIRevenueRecovery.repository;

import com.AIRevenueRecovery.batch.BatchRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BatchRunRepository extends JpaRepository<BatchRun, Long> {

    // Status ke base par batch runs search karne ke liye custom query method
    List<BatchRun> findByStatus(String status);

    // Job name ke base par historical batch runs fetch karne ke liye
    List<BatchRun> findByJobNameOrderByStartedAtDesc(String jobName);
}