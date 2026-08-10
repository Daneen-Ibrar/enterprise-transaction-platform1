package com.enterprise.api;

import com.enterprise.audit.AuditRepository;
import com.enterprise.invoice.InvoiceService;
import com.enterprise.reconciliation.ReconciliationRecordRepository;
import com.enterprise.reliability.DlqEntryRepository;
import com.enterprise.transaction.TransactionRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/admin")
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MERCHANT_ADMIN')")
public class AdminRootController {

    private final TransactionRepository transactionRepository;
    private final AuditRepository auditRepository;
    private final DlqEntryRepository dlqEntryRepository;
    private final ReconciliationRecordRepository reconciliationRecordRepository;
    private final InvoiceService invoiceService;

    public AdminRootController(TransactionRepository transactionRepository,
                               AuditRepository auditRepository,
                               DlqEntryRepository dlqEntryRepository,
                               ReconciliationRecordRepository reconciliationRecordRepository,
                               InvoiceService invoiceService) {
        this.transactionRepository = transactionRepository;
        this.auditRepository = auditRepository;
        this.dlqEntryRepository = dlqEntryRepository;
        this.reconciliationRecordRepository = reconciliationRecordRepository;
        this.invoiceService = invoiceService;
    }

    @GetMapping
    public String adminRoot(Model model, Authentication authentication) {
        // Common stats
        long totalTransactions = transactionRepository.count();
        long totalAuditEvents = auditRepository.count();
        long pendingDlq = dlqEntryRepository.countByStatus("PENDING");
        long reconciled = reconciliationRecordRepository.count();

        model.addAttribute("totalTransactions", totalTransactions);
        model.addAttribute("totalAuditEvents", totalAuditEvents);
        model.addAttribute("pendingDlq", pendingDlq);
        model.addAttribute("reconciled", reconciled);

        // Suspicious invoices
        boolean isSuperAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_SUPER_ADMIN"));
        boolean isMerchantAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_MERCHANT_ADMIN"));

        if (isSuperAdmin || isMerchantAdmin) {
            long suspiciousCount = invoiceService.findAll().stream()
                    .filter(inv -> inv.getRiskLevel() != null && !"GREEN".equals(inv.getRiskLevel()))
                    .count();
            model.addAttribute("suspiciousCount", suspiciousCount);
        }

        model.addAttribute("isSuperAdmin", isSuperAdmin);
        model.addAttribute("isMerchantAdmin", isMerchantAdmin);
        model.addAttribute("roleBadge", isSuperAdmin ? "Super Admin" : "Merchant Admin");

         model.addAttribute("viewType", "admin");

        return "admin/dashboard";
    }
}