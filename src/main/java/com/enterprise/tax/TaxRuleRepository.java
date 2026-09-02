package com.enterprise.tax;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TaxRuleRepository extends JpaRepository<TaxRule, Long> {

    @Query("SELECT t FROM TaxRule t WHERE t.tenantId = :tenantId AND t.isActive = true ORDER BY t.countryCode")
    List<TaxRule> findByTenantIdAndActiveTrue(@Param("tenantId") Long tenantId);

    // ✅ FIXED: Use isActive instead of active
    Optional<TaxRule> findByTenantIdAndCountryCodeAndIsActiveTrue(Long tenantId, String countryCode);

    @Query("SELECT t FROM TaxRule t WHERE t.tenantId = :tenantId AND t.isDefault = true AND t.isActive = true")
    Optional<TaxRule> findDefaultByTenantId(@Param("tenantId") Long tenantId);

    @Query("SELECT t FROM TaxRule t WHERE t.tenantId = :tenantId AND t.isB2BExempt = true AND t.isActive = true")
    List<TaxRule> findB2BExemptRulesByTenantId(@Param("tenantId") Long tenantId);
}