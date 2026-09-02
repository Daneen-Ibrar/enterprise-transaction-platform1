package com.enterprise.subscription;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SubscriptionPlanRepository extends JpaRepository<SubscriptionPlan, Long> {

    List<SubscriptionPlan> findByTenantIdAndIsActiveTrue(Long tenantId);

    Optional<SubscriptionPlan> findByTenantIdAndName(Long tenantId, String name);

    @Query("SELECT p FROM SubscriptionPlan p WHERE p.tenantId = :tenantId AND p.isActive = true ORDER BY p.price ASC")
    List<SubscriptionPlan> findActiveByTenantId(@Param("tenantId") Long tenantId);
}