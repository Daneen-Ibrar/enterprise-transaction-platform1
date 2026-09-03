package com.enterprise.subscription;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;  // ✅ ADD THIS IMPORT
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface CustomerSubscriptionRepository extends JpaRepository<CustomerSubscription, Long> {

    List<CustomerSubscription> findByCustomerIdOrderByCreatedAtDesc(Long customerId);

    List<CustomerSubscription> findByMerchantIdOrderByCreatedAtDesc(Long merchantId);

    // ✅ FIXED: Added Pageable import
    Page<CustomerSubscription> findByMerchantIdOrderByCreatedAtDesc(Long merchantId, Pageable pageable);


    @Query("SELECT s FROM CustomerSubscription s WHERE s.status = 'ACTIVE' AND s.nextBillingDate <= :date")
    List<CustomerSubscription> findActiveSubscriptionsDueForBilling(@Param("date") LocalDateTime date);

    @Query("SELECT s FROM CustomerSubscription s WHERE s.status = 'TRIALING' AND s.trialEndDate <= :date")
    List<CustomerSubscription> findTrialsExpiringOn(@Param("date") LocalDateTime date);

    long countByMerchantIdAndStatus(Long merchantId, CustomerSubscription.SubscriptionStatus status);

    @Modifying
    @Transactional
    @Query("UPDATE CustomerSubscription s SET s.status = :status, s.updatedAt = :updatedAt WHERE s.id = :id")
    void updateStatus(@Param("id") Long id,
                      @Param("status") CustomerSubscription.SubscriptionStatus status,
                      @Param("updatedAt") LocalDateTime updatedAt);

    // ============================================================
    // ADDITIONAL QUERY METHODS
    // ============================================================

    Optional<CustomerSubscription> findByCustomerIdAndPlanIdAndStatus(
        Long customerId, Long planId, CustomerSubscription.SubscriptionStatus status);

    @Query("SELECT COUNT(s) FROM CustomerSubscription s WHERE s.tenantId = :tenantId AND s.status = :status")
    long countByTenantIdAndStatus(@Param("tenantId") Long tenantId,
                                  @Param("status") CustomerSubscription.SubscriptionStatus status);

    @Query("SELECT COUNT(s) FROM CustomerSubscription s WHERE s.tenantId = :tenantId AND s.createdAt BETWEEN :start AND :end")
    long countByTenantIdAndCreatedAtBetween(@Param("tenantId") Long tenantId,
                                            @Param("start") LocalDateTime start,
                                            @Param("end") LocalDateTime end);
}