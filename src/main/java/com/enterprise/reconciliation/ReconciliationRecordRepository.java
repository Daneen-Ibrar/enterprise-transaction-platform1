package com.enterprise.reconciliation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ReconciliationRecordRepository extends JpaRepository<ReconciliationRecord, Long> {
    List<ReconciliationRecord> findTop10ByOrderByCreatedAtDesc();
    Optional<ReconciliationRecord> findTopByOrderByCreatedAtDesc();

    // ===== NEW: Tenant‑aware findAll and count =====
    @Query("SELECT r FROM ReconciliationRecord r WHERE r.tenantId = :tenantId ORDER BY r.createdAt DESC")
    List<ReconciliationRecord> findAllByTenantId(@Param("tenantId") Long tenantId);

    @Query("SELECT COUNT(r) FROM ReconciliationRecord r WHERE r.tenantId = :tenantId")
    long countByTenantId(@Param("tenantId") Long tenantId);
}