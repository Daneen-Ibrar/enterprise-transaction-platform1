package com.enterprise.xero.repository;

import com.enterprise.xero.entity.XeroToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface XeroTokenRepository extends JpaRepository<XeroToken, Long> {

    Optional<XeroToken> findByTenantId(Long tenantId);

    @Query("SELECT t FROM XeroToken t WHERE t.tenantId = :tenantId AND t.expiresAt > CURRENT_TIMESTAMP")
    Optional<XeroToken> findValidTokenByTenantId(@Param("tenantId") Long tenantId);

    void deleteByTenantId(Long tenantId);

    // ✅ Check if tenant has custom credentials
    @Query("SELECT CASE WHEN COUNT(t) > 0 THEN true ELSE false END FROM XeroToken t WHERE t.tenantId = :tenantId AND t.clientId IS NOT NULL AND t.clientSecret IS NOT NULL")
    boolean hasCustomCredentials(@Param("tenantId") Long tenantId);
}