package com.enterprise.refund;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface RefundRuleRepository extends JpaRepository<RefundRule, Long> {
    List<RefundRule> findByActiveTrueOrderByRulePriorityAsc();

    // ===== NEW: Tenant‑aware methods =====
    @Query("SELECT r FROM RefundRule r WHERE r.tenantId = :tenantId AND r.active = true ORDER BY r.rulePriority ASC")
    List<RefundRule> findByTenantIdAndActiveTrue(@Param("tenantId") Long tenantId);

    @Query("SELECT r FROM RefundRule r WHERE r.tenantId = :tenantId")
    List<RefundRule> findAllByTenantId(@Param("tenantId") Long tenantId);

    @Query("SELECT COUNT(r) FROM RefundRule r WHERE r.tenantId = :tenantId")
    long countByTenantId(@Param("tenantId") Long tenantId);
}