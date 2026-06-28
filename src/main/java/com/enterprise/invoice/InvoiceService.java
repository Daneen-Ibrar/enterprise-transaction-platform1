package com.enterprise.invoice;

import com.enterprise.identity.AppUser;
import com.enterprise.identity.UserRepository;
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
    private final UserRepository userRepository;

    public InvoiceService(InvoiceRepository invoiceRepository,
                          NotificationService notificationService,
                          UserRepository userRepository) {
        this.invoiceRepository = invoiceRepository;
        this.notificationService = notificationService;
        this.userRepository = userRepository;
    }

    @Transactional
    public Invoice createInvoice(BigDecimal amount, String description, String customerEmail,
                                 Long merchantId, boolean requiresApproval, String currency) {
        Invoice invoice = new Invoice(amount, description, customerEmail, merchantId);
        invoice.setCurrency(currency != null && !currency.isEmpty() ? currency : "GBP");
        invoice.setRequiresApproval(requiresApproval);
        if (requiresApproval) {
            invoice.setStatus("PENDING_APPROVAL");
        } else {
            invoice.setStatus("APPROVED");
        }
        invoice = invoiceRepository.save(invoice);

        // Notify merchant
        notificationService.createNotification(
            merchantId,
            "INVOICE_CREATED",
            "Invoice Created",
            String.format("Invoice #%d created for %.2f %s to %s", invoice.getId(), amount, invoice.getCurrency(), customerEmail),
            "/invoices/" + invoice.getId()
        );

        // If no approval required, notify customer
        if (!requiresApproval) {
            Long customerId = getUserIdByEmail(customerEmail);
            if (customerId != null) {
                notificationService.createNotification(
                    customerId,
                    "INVOICE_READY",
                    "Invoice Ready for Payment",
                    String.format("Invoice #%d for %.2f %s is ready to pay", invoice.getId(), amount, invoice.getCurrency()),
                    "/invoices/" + invoice.getId()
                );
            }
        }

        // If invoice requires approval, notify all admins
        if (requiresApproval) {
            List<AppUser> admins = userRepository.findByRolesName("ADMIN");
            for (AppUser admin : admins) {
                notificationService.createNotification(
                    admin.getId(),
                    "INVOICE_PENDING_APPROVAL",
                    "Invoice Requires Approval",
                    String.format("Invoice #%d for %.2f %s is pending approval", invoice.getId(), amount, invoice.getCurrency()),
                    "/admin/invoices/pending"
                );
            }
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

        notificationService.createNotification(
            invoice.getMerchantId(),
            "INVOICE_APPROVED",
            "Invoice Approved",
            String.format("Invoice #%d approved by Admin", invoiceId),
            "/invoices/" + invoiceId
        );

        Long customerId = getUserIdByEmail(invoice.getCustomerEmail());
        if (customerId != null) {
            notificationService.createNotification(
                customerId,
                "INVOICE_APPROVED",
                "Invoice Approved",
                String.format("Invoice #%d has been approved and is ready for payment", invoiceId),
                "/invoices/" + invoiceId
            );
        }
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
        Long customerId = getUserIdByEmail(invoice.getCustomerEmail());
        if (customerId != null) {
            notificationService.createNotification(
                customerId,
                "INVOICE_REJECTED",
                "Invoice Rejected",
                String.format("Invoice #%d was rejected", invoiceId),
                "/invoices/" + invoiceId
            );
        }
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

        notificationService.createNotification(
            invoice.getMerchantId(),
            "INVOICE_PAID",
            "Invoice Paid",
            String.format("Invoice #%d paid (Transaction #%d)", invoiceId, transactionId),
            "/transactions/" + transactionId
        );

        Long customerId = getUserIdByEmail(invoice.getCustomerEmail());
        if (customerId != null) {
            notificationService.createNotification(
                customerId,
                "INVOICE_PAID",
                "Invoice Paid",
                String.format("Invoice #%d was paid (Transaction #%d)", invoiceId, transactionId),
                "/transactions/" + transactionId
            );
        }
    }

    private Long getUserIdByEmail(String email) {
        return userRepository.findByEmail(email)
                .map(AppUser::getId)
                .orElse(null);
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