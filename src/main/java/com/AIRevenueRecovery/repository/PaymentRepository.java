package com.AIRevenueRecovery.repository;

import com.AIRevenueRecovery.entity.FailureReason;
import com.AIRevenueRecovery.entity.Payment;
import com.AIRevenueRecovery.entity.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    List<Payment> findByStatus(PaymentStatus status);
    List<Payment> findByFailureReason(FailureReason failureReason);
    List<Payment> findByCustomerId(String customerId);
    List<Payment> findTop10ByOrderByCreatedAtDesc();
    @Query("""
            SELECT p
            FROM Payment p
            WHERE p.status = :status
            AND (
                p.nextRetryAt IS NULL
                OR p.nextRetryAt <= :currentTime
            )
            """)
    List<Payment> findPaymentsReadyForRetry(
            @Param("status") PaymentStatus status,
            @Param("currentTime") LocalDateTime currentTime
    );

    @Query("""
            SELECT p
            FROM Payment p
            WHERE p.status = :status
            AND p.createdAt <= :cutoffTime
            AND p.recoveryEmailSentAt IS NULL
            """)
    List<Payment> findPaymentsReadyForRecoveryEmail(
            @Param("status") PaymentStatus status,
            @Param("cutoffTime") LocalDateTime cutoffTime
    );
    @Query("""
        SELECT
            FUNCTION('DATE', p.createdAt) AS date,
            COALESCE(SUM(p.amount), 0) AS revenue,
            COALESCE(
                SUM(
                    CASE
                        WHEN p.status = com.AIRevenueRecovery.entity.PaymentStatus.SUCCESS
                        THEN p.amount
                        ELSE 0
                    END
                ),
                0
            ) AS recovered
        FROM Payment p
        WHERE p.createdAt >= :startDate
        GROUP BY FUNCTION('DATE', p.createdAt)
        ORDER BY FUNCTION('DATE', p.createdAt)
        """)
    List<Object[]> getDailyRevenueAnalytics(
            @Param("startDate") LocalDateTime startDate
    );

    @Query("""
            SELECT COALESCE(SUM(p.amount), 0)
            FROM Payment p
            WHERE p.status = :status
            """)
    Double getTotalAmountByStatus(@Param("status") PaymentStatus status);
    long countByStatus(PaymentStatus status);
    @Query("""
            SELECT COUNT(p)
            FROM Payment p
            WHERE p.status IN :statuses
            """)
    long countByStatuses(@Param("statuses") List<PaymentStatus> statuses);

    @Query("""
            SELECT COUNT(DISTINCT p.customerId)
            FROM Payment p
            WHERE p.status IN (
                com.AIRevenueRecovery.entity.PaymentStatus.CREATED,
                com.AIRevenueRecovery.entity.PaymentStatus.RETRYING,
                com.AIRevenueRecovery.entity.PaymentStatus.SUCCESS
            )
            """)
    long countActiveCustomers();
    long countByCreatedAtGreaterThanEqual(
            LocalDateTime startDate
    );

    long countByStatusAndCreatedAtGreaterThanEqual(
            PaymentStatus status,
            LocalDateTime startDate
    );

    @Query("""
            SELECT COUNT(p)
            FROM Payment p
            WHERE p.status IN :statuses
            AND p.createdAt >= :startDate
            """)
    long countPendingPaymentsByPeriod(
            @Param("statuses") List<PaymentStatus> statuses,
            @Param("startDate") LocalDateTime startDate
    );
    @Query("""
            SELECT COALESCE(SUM(p.amount), 0)
            FROM Payment p
            WHERE p.status = :status
            AND p.createdAt >= :startDate
            """)
    Double getTotalAmountByStatusAndCreatedAtGreaterThanEqual(
            @Param("status") PaymentStatus status,
            @Param("startDate") LocalDateTime startDate
    );


    /**
     * Active customers within selected period.
     */
    @Query("""
            SELECT COUNT(DISTINCT p.customerId)
            FROM Payment p
            WHERE p.createdAt >= :startDate
            AND p.status IN (
                com.AIRevenueRecovery.entity.PaymentStatus.CREATED,
                com.AIRevenueRecovery.entity.PaymentStatus.RETRYING,
                com.AIRevenueRecovery.entity.PaymentStatus.SUCCESS
            )
            """)
    long countActiveCustomersByPeriod(
            @Param("startDate") LocalDateTime startDate
    );
    @Query(value = """
        SELECT
            DATE_FORMAT(p.created_at, '%Y-%m-01') AS month,

            COALESCE(SUM(p.amount), 0) AS revenue,

            COALESCE(
                SUM(
                    CASE
                        WHEN p.status = 'SUCCESS'
                        THEN p.amount
                        ELSE 0
                    END
                ),
                0
            ) AS recovered

        FROM payments p

        WHERE p.created_at >=
              DATE_SUB(
                  DATE_FORMAT(CURDATE(), '%Y-%m-01'),
                  INTERVAL 6 MONTH
              )

        GROUP BY DATE_FORMAT(
            p.created_at,
            '%Y-%m-01'
        )

        ORDER BY DATE_FORMAT(
            p.created_at,
            '%Y-%m-01'
        )
        """, nativeQuery = true)
    List<Object[]> getMonthlyRevenueAnalytics();
}