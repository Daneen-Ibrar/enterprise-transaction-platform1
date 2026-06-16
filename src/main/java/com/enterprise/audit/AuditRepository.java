package com.enterprise.audit;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface AuditRepository extends JpaRepository<AuditEvent, Long> {
    List<AuditEvent> findByUserIdOrderByCreatedAtAsc(Long userId);
    Optional<AuditEvent> findFirstByOrderByCreatedAtDesc();
}