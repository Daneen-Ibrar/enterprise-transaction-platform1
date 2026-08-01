package com.enterprise.transaction;

import org.springframework.data.jpa.domain.Specification;
import jakarta.persistence.criteria.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class TransactionSpecifications {

    public static Specification<Transaction> hasInvoiceId(Long invoiceId) {
        return (root, query, cb) -> invoiceId == null ? null : cb.equal(root.get("invoiceId"), invoiceId);
    }

    public static Specification<Transaction> hasCustomerId(Long customerId) {
        return (root, query, cb) -> customerId == null ? null : cb.equal(root.get("customerId"), customerId);
    }

    public static Specification<Transaction> hasMerchantId(Long merchantId) {
        return (root, query, cb) -> merchantId == null ? null : cb.equal(root.get("merchantId"), merchantId);
    }

    public static Specification<Transaction> hasStatus(String status) {
        return (root, query, cb) -> status == null || status.isEmpty() ? null : cb.equal(root.get("status"), TransactionStatus.valueOf(status));
    }

    public static Specification<Transaction> amountBetween(BigDecimal min, BigDecimal max) {
        return (root, query, cb) -> {
            if (min == null && max == null) return null;
            Path<BigDecimal> amount = root.get("amount");
            if (min != null && max != null) return cb.between(amount, min, max);
            if (min != null) return cb.greaterThanOrEqualTo(amount, min);
            return cb.lessThanOrEqualTo(amount, max);
        };
    }

    public static Specification<Transaction> createdBetween(LocalDateTime start, LocalDateTime end) {
        return (root, query, cb) -> {
            if (start == null && end == null) return null;
            Path<LocalDateTime> createdAt = root.get("createdAt");
            if (start != null && end != null) return cb.between(createdAt, start, end);
            if (start != null) return cb.greaterThanOrEqualTo(createdAt, start);
            return cb.lessThanOrEqualTo(createdAt, end);
        };
    }
}