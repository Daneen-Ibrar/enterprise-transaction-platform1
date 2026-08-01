package com.enterprise.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AuditRepository extends JpaRepository<AuditEvent, Long>, JpaSpecificationExecutor<AuditEvent> {
    List<AuditEvent> findByUserIdOrderByCreatedAtAsc(Long userId);
    Optional<AuditEvent> findFirstByOrderByCreatedAtDesc();
    Optional<AuditEvent> findFirstByTenantIdOrderByCreatedAtDesc(Long tenantId);

    @Query("SELECT DISTINCT a.eventType FROM AuditEvent a WHERE a.tenantId = :tenantId ORDER BY a.eventType")
    List<String> findDistinctEventTypesByTenantId(@Param("tenantId") Long tenantId);

    // ===== Transaction timeline =====
    List<AuditEvent> findByEntityTypeAndEntityIdOrderByCreatedAtAsc(String entityType, Long entityId);

    // ===== Paginated by tenant for verification =====
    Page<AuditEvent> findByTenantIdOrderByCreatedAtAsc(Long tenantId, Pageable pageable);
}