package com.enterprise.invoice;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {
    List<Invoice> findByMerchantId(Long merchantId);
    List<Invoice> findByCustomerEmail(String customerEmail);
    List<Invoice> findByStatusAndRequiresApproval(String status, boolean requiresApproval);
    List<Invoice> findByStatusIn(List<String> statuses);
    List<Invoice> findByStatus(String status);
    List<Invoice> findByStatusAndCustomerEmailContainingIgnoreCase(String status, String customerEmail);
    List<Invoice> findAllByIdIn(List<Long> ids);

    // Ordered methods
    List<Invoice> findByMerchantIdOrderByCreatedAtDesc(Long merchantId);
    List<Invoice> findByCustomerEmailOrderByCreatedAtDesc(String customerEmail);

    List<Invoice> findByMerchantIdAndStatus(Long merchantId, String status);

    // ===== REMOVED: findDistinctCustomerEmailsByMerchantId – caused SQL DISTINCT ordering error =====
}