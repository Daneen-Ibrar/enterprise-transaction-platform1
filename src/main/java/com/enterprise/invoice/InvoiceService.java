package com.enterprise.invoice;

import com.enterprise.notification.NotificationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class InvoiceService {

    private final InvoiceRepository invoiceRepository;
    private final NotificationService notificationService;

    public InvoiceService(InvoiceRepository invoiceRepository,
                          NotificationService notificationService) {
        this.invoiceRepository = invoiceRepository;
        this.notificationService = notificationService;
    }

    @Transactional
    public Invoice createInvoice(BigDecimal amount, String description, String customerEmail,
                                 Long merchantId, boolean requiresApproval) {
        Invoice invoice = new Invoice(amount, description, customerEmail, merchantId);
        invoice.setRequiresApproval(requiresApproval);
        if (requiresApproval) {
            invoice.setStatus("PENDING_APPROVAL");
        } else {
            invoice.setStatus("APPROVED");
        }
        invoice = invoiceRepository.save(invoice);

        // Notify merchant (creation)
        notificationService.createNotification(
            merchantId,
            "INVOICE_CREATED",
            "Invoice Created",
            String.format("Invoice #%d created for %.2f to %s", invoice.getId(), amount, customerEmail),
            "/invoices/" + invoice.getId()
        );

        // Notify customer (if approved immediately)
        if (!requiresApproval) {
            // We'll notify customer that invoice is ready for payment
            // but we need to map customer email to userId. This is a placeholder; we'll later resolve.
            // For now, we'll skip or send a generic one.
        }
        return invoice;
    }

    @Transactional
    public Invoice approveInvoice(Long invoiceId, Long adminId) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new RuntimeException("Invoice not found"));
        if (!"PENDING_APPROVAL".equals(invoice.getStatus())) {
            throw new IllegalStateException("Invoice is not pending approval");
        }
        invoice.setStatus("APPROVED");
        invoice.setUpdatedAt(LocalDateTime.now());
        invoice = invoiceRepository.save(invoice);

        // Notify merchant
        notificationService.createNotification(
            invoice.getMerchantId(),
            "INVOICE_APPROVED",
            "Invoice Approved",
            String.format("Invoice #%d approved by Admin", invoiceId),
            "/invoices/" + invoiceId
        );
        return invoice;
    }

    @Transactional
    public Invoice rejectInvoice(Long invoiceId, Long adminId) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new RuntimeException("Invoice not found"));
        if (!"PENDING_APPROVAL".equals(invoice.getStatus())) {
            throw new IllegalStateException("Invoice is not pending approval");
        }
        invoice.setStatus("REJECTED");
        invoice.setUpdatedAt(LocalDateTime.now());
        invoice = invoiceRepository.save(invoice);

        notificationService.createNotification(
            invoice.getMerchantId(),
            "INVOICE_REJECTED",
            "Invoice Rejected",
            String.format("Invoice #%d was rejected by Admin", invoiceId),
            "/invoices/" + invoiceId
        );
        return invoice;
    }

    @Transactional
    public void markAsPaid(Long invoiceId, Long transactionId) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new RuntimeException("Invoice not found"));
        if (!"APPROVED".equals(invoice.getStatus())) {
            throw new IllegalStateException("Invoice is not approved");
        }
        invoice.setStatus("PAID");
        invoice.setUpdatedAt(LocalDateTime.now());
        invoiceRepository.save(invoice);

        // Notify merchant
        notificationService.createNotification(
            invoice.getMerchantId(),
            "INVOICE_PAID",
            "Invoice Paid",
            String.format("Invoice #%d paid (Transaction #%d)", invoiceId, transactionId),
            "/transactions/" + transactionId
        );
    }

    public List<Invoice> getInvoicesForMerchant(Long merchantId) {
        return invoiceRepository.findByMerchantId(merchantId);
    }

    public List<Invoice> getInvoicesForCustomer(String email) {
        return invoiceRepository.findByCustomerEmail(email);
    }

    public List<Invoice> getPendingApprovalInvoices() {
        return invoiceRepository.findByStatusAndRequiresApproval("PENDING_APPROVAL", true);
    }

    public Optional<Invoice> findById(Long id) {
        return invoiceRepository.findById(id);
    }
}