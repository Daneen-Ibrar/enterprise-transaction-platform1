package com.enterprise.invoice;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {
    List<Invoice> findByMerchantId(Long merchantId);
    List<Invoice> findByCustomerEmail(String customerEmail);
    List<Invoice> findByStatusAndRequiresApproval(String status, boolean requiresApproval);
}