package com.enterprise.audit;

import org.springframework.data.jpa.domain.Specification;
import jakarta.persistence.criteria.*;

import java.time.LocalDateTime;

public class AuditEventSpecifications {

    public static Specification<AuditEvent> hasUserId(Long userId) {
        return (root, query, cb) -> userId == null ? null : cb.equal(root.get("userId"), userId);
    }

    public static Specification<AuditEvent> hasEventType(String eventType) {
        return (root, query, cb) -> eventType == null || eventType.isEmpty() ? null : cb.equal(root.get("eventType"), eventType);
    }

    public static Specification<AuditEvent> hasEntityType(String entityType) {
        return (root, query, cb) -> entityType == null || entityType.isEmpty() ? null : cb.equal(root.get("entityType"), entityType);
    }

    public static Specification<AuditEvent> createdBetween(LocalDateTime start, LocalDateTime end) {
        return (root, query, cb) -> {
            if (start == null && end == null) return null;
            Path<LocalDateTime> createdAt = root.get("createdAt");
            if (start != null && end != null) return cb.between(createdAt, start, end);
            if (start != null) return cb.greaterThanOrEqualTo(createdAt, start);
            return cb.lessThanOrEqualTo(createdAt, end);
        };
    }

    public static Specification<AuditEvent> hasTenantId(Long tenantId) {
        return (root, query, cb) -> tenantId == null ? null : cb.equal(root.get("tenantId"), tenantId);
    }
}