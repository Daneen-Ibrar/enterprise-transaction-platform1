package com.enterprise.ratelimit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface RateLimitViolationRepository extends JpaRepository<RateLimitViolation, Long> {

    @Query("SELECT v FROM RateLimitViolation v WHERE v.tenantId = :tenantId ORDER BY v.createdAt DESC")
    List<RateLimitViolation> findAllByTenantIdOrderByCreatedAtDesc(@Param("tenantId") Long tenantId);

    @Query("SELECT v FROM RateLimitViolation v WHERE v.tenantId = :tenantId AND v.apiKeyPrefix = :apiKeyPrefix ORDER BY v.createdAt DESC")
    List<RateLimitViolation> findByTenantIdAndApiKeyPrefix(@Param("tenantId") Long tenantId, @Param("apiKeyPrefix") String apiKeyPrefix);

    @Query("SELECT COUNT(v) FROM RateLimitViolation v WHERE v.tenantId = :tenantId AND v.createdAt > :since")
    long countByTenantIdAndCreatedAtAfter(@Param("tenantId") Long tenantId, @Param("since") LocalDateTime since);

    @Query("SELECT v FROM RateLimitViolation v WHERE v.tenantId = :tenantId ORDER BY v.createdAt DESC LIMIT :limit")
    List<RateLimitViolation> findRecentByTenantId(@Param("tenantId") Long tenantId, @Param("limit") int limit);
}