package com.enterprise.api;

import com.enterprise.audit.AuditRepository;
import com.enterprise.reconciliation.ReconciliationRecordRepository;
import com.enterprise.reliability.DlqEntryRepository;
import com.enterprise.transaction.TransactionRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class DashboardController {

    private final TransactionRepository transactionRepository;
    private final AuditRepository auditRepository;
    private final ReconciliationRecordRepository reconciliationRecordRepository;
    private final DlqEntryRepository dlqEntryRepository;

    public DashboardController(TransactionRepository transactionRepository,
                               AuditRepository auditRepository,
                               ReconciliationRecordRepository reconciliationRecordRepository,
                               DlqEntryRepository dlqEntryRepository) {
        this.transactionRepository = transactionRepository;
        this.auditRepository = auditRepository;
        this.reconciliationRecordRepository = reconciliationRecordRepository;
        this.dlqEntryRepository = dlqEntryRepository;
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model, Authentication authentication) {
        // Summary statistics
        long totalTransactions = transactionRepository.count();
        long totalAuditEvents = auditRepository.count();
        long pendingDlq = dlqEntryRepository.countByStatus("PENDING");
        long reconciled = reconciliationRecordRepository.count();

        model.addAttribute("totalTransactions", totalTransactions);
        model.addAttribute("totalAuditEvents", totalAuditEvents);
        model.addAttribute("pendingDlq", pendingDlq);
        model.addAttribute("reconciled", reconciled);
        model.addAttribute("username", authentication.getName());
        model.addAttribute("roles", authentication.getAuthorities());

        return "dashboard";
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }
}