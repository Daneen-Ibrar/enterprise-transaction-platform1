package com.enterprise.invoice;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SuspicionRuleRepository extends JpaRepository<SuspicionRule, Long> {
    List<SuspicionRule> findByActiveTrueOrderByPriorityAsc();

    // ===== NEW: Tenant‑aware methods =====
    @Query("SELECT r FROM SuspicionRule r WHERE r.tenantId = :tenantId AND r.active = true ORDER BY r.priority ASC")
    List<SuspicionRule> findByTenantIdAndActiveTrue(@Param("tenantId") Long tenantId);

    @Query("SELECT r FROM SuspicionRule r WHERE r.tenantId = :tenantId")
    List<SuspicionRule> findAllByTenantId(@Param("tenantId") Long tenantId);

    @Query("SELECT COUNT(r) FROM SuspicionRule r WHERE r.tenantId = :tenantId")
    long countByTenantId(@Param("tenantId") Long tenantId);
}