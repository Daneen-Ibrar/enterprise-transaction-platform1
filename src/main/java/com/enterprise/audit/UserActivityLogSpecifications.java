package com.enterprise.audit;

import org.springframework.data.jpa.domain.Specification;
import jakarta.persistence.criteria.*;

import java.time.LocalDateTime;

public class UserActivityLogSpecifications {

    public static Specification<UserActivityLog> hasUserId(Long userId) {
        return (root, query, cb) -> {
            if (userId == null) return null;
            return cb.equal(root.get("userId"), userId);
        };
    }

    public static Specification<UserActivityLog> hasAction(String action) {
        return (root, query, cb) -> {
            if (action == null || action.isEmpty()) return null;
            return cb.equal(root.get("action"), action);
        };
    }

    public static Specification<UserActivityLog> createdAtAfter(LocalDateTime startDate) {
        return (root, query, cb) -> {
            if (startDate == null) return null;
            return cb.greaterThanOrEqualTo(root.get("createdAt"), startDate);
        };
    }

    public static Specification<UserActivityLog> createdAtBefore(LocalDateTime endDate) {
        return (root, query, cb) -> {
            if (endDate == null) return null;
            return cb.lessThanOrEqualTo(root.get("createdAt"), endDate);
        };
    }
}