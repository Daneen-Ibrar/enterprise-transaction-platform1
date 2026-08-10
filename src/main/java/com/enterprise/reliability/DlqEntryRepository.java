package com.enterprise.reliability;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DlqEntryRepository extends JpaRepository<DlqEntry, Long> {
    List<DlqEntry> findByStatus(String status);
    long countByStatus(String status);

    // For scheduled processing – find entries with retry count below max
    List<DlqEntry> findByStatusAndFailureCountLessThan(String status, int maxFailureCount);

    // ===== NEW: Tenant‑aware count =====
    @Query("SELECT COUNT(d) FROM DlqEntry d WHERE d.tenantId = :tenantId AND d.status = :status")
    long countByTenantIdAndStatus(@Param("tenantId") Long tenantId, @Param("status") String status);
}