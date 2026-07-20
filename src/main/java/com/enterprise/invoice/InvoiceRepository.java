package com.enterprise.invoice;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {
    List<Invoice> findByMerchantId(Long merchantId);
    List<Invoice> findByCustomerEmail(String customerEmail);
    List<Invoice> findByStatusAndRequiresApproval(String status, boolean requiresApproval);

    // For re-evaluation – fetch by multiple statuses
    List<Invoice> findByStatusIn(List<String> statuses);

    // For admin rejected invoice view
    List<Invoice> findByStatus(String status);
    List<Invoice> findByStatusAndCustomerEmailContainingIgnoreCase(String status, String customerEmail);

    // ===== BULK OPERATION =====
    List<Invoice> findAllByIdIn(List<Long> ids);
}