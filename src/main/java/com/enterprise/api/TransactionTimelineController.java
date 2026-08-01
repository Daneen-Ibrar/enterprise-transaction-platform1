package com.enterprise.api;

import com.enterprise.audit.AuditEvent;
import com.enterprise.audit.AuditRepository;
import com.enterprise.transaction.Transaction;
import com.enterprise.transaction.TransactionRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

@Controller
@RequestMapping("/transactions")
@PreAuthorize("hasAnyRole('ADMIN', 'MERCHANT', 'CUSTOMER')")
public class TransactionTimelineController {

    private final TransactionRepository transactionRepository;
    private final AuditRepository auditRepository;

    public TransactionTimelineController(TransactionRepository transactionRepository,
                                         AuditRepository auditRepository) {
        this.transactionRepository = transactionRepository;
        this.auditRepository = auditRepository;
    }

    @GetMapping("/{id}/timeline")
    public String timeline(@PathVariable Long id, Model model) {
        Transaction tx = transactionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Transaction not found"));
        List<AuditEvent> events = auditRepository.findByEntityTypeAndEntityIdOrderByCreatedAtAsc("Transaction", id);
        model.addAttribute("transaction", tx);
        model.addAttribute("events", events);
        return "transactions/timeline";
    }
}