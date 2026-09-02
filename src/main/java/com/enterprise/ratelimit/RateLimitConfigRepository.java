package com.enterprise.ratelimit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RateLimitConfigRepository extends JpaRepository<RateLimitConfig, Long> {

    Optional<RateLimitConfig> findByApiKeyPrefixAndTenantId(String apiKeyPrefix, Long tenantId);

    @Query("SELECT r FROM RateLimitConfig r WHERE r.tenantId = :tenantId")
    List<RateLimitConfig> findAllByTenantId(@Param("tenantId") Long tenantId);

    List<RateLimitConfig> findByApiKeyPrefix(String apiKeyPrefix);
}