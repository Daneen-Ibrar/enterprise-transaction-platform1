package com.enterprise.invoice;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ApprovalRuleRepository extends JpaRepository<ApprovalRule, Long> {
    List<ApprovalRule> findByActiveTrueOrderByPriorityAsc();

    // ===== NEW: Tenant‑aware methods =====
    @Query("SELECT r FROM ApprovalRule r WHERE r.tenantId = :tenantId AND r.active = true ORDER BY r.priority ASC")
    List<ApprovalRule> findByTenantIdAndActiveTrue(@Param("tenantId") Long tenantId);

    @Query("SELECT r FROM ApprovalRule r WHERE r.tenantId = :tenantId")
    List<ApprovalRule> findAllByTenantId(@Param("tenantId") Long tenantId);

    @Query("SELECT COUNT(r) FROM ApprovalRule r WHERE r.tenantId = :tenantId")
    long countByTenantId(@Param("tenantId") Long tenantId);
}