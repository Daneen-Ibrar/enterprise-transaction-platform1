package com.enterprise.api;

import com.enterprise.audit.AuditRepository;
import com.enterprise.reconciliation.ReconciliationRecord;
import com.enterprise.reconciliation.ReconciliationRecordRepository;
import com.enterprise.reliability.DlqEntry;
import com.enterprise.reliability.DlqEntryRepository;
import com.enterprise.transaction.Transaction;
import com.enterprise.transaction.TransactionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Controller
@RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminViewController {

    private final TransactionRepository transactionRepository;
    private final AuditRepository auditRepository;
    private final ReconciliationRecordRepository reconciliationRecordRepository;
    private final DlqEntryRepository dlqEntryRepository;

    public AdminViewController(TransactionRepository transactionRepository,
                               AuditRepository auditRepository,
                               ReconciliationRecordRepository reconciliationRecordRepository,
                               DlqEntryRepository dlqEntryRepository) {
        this.transactionRepository = transactionRepository;
        this.auditRepository = auditRepository;
        this.reconciliationRecordRepository = reconciliationRecordRepository;
        this.dlqEntryRepository = dlqEntryRepository;
    }

    @GetMapping("/transactions")
    public String transactions(@RequestParam(defaultValue = "0") int page,
                               @RequestParam(defaultValue = "20") int size,
                               Model model) {
        Page<Transaction> transactions = transactionRepository.findAll(
            PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        );
        model.addAttribute("transactions", transactions);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", transactions.getTotalPages());
        return "admin/transactions";
    }

    

    @GetMapping("/audit")
    public String audit(@RequestParam(defaultValue = "0") int page,
                        @RequestParam(defaultValue = "20") int size,
                        Model model) {
        Page<com.enterprise.audit.AuditEvent> events = auditRepository.findAll(
            PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        );
        model.addAttribute("events", events);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", events.getTotalPages());
        return "admin/audit";
    }

@GetMapping("/reconciliation")
public String reconciliation(Model model) {
    List<ReconciliationRecord> records = reconciliationRecordRepository.findAll(
        PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"))
    ).getContent();
    model.addAttribute("records", records);
    return "admin/reconciliation";
}

    @GetMapping("/reconciliation/{id}")
    public String reconciliationDetail(@PathVariable Long id, Model model) {
        ReconciliationRecord record = reconciliationRecordRepository.findById(id).orElse(null);
        model.addAttribute("record", record);
        // In a full implementation, you'd also fetch the details.
        return "admin/reconciliation-detail";
    }

    

@GetMapping("/dlq")
public String dlq(@RequestParam(defaultValue = "0") int page,
                  @RequestParam(defaultValue = "20") int size,
                  Model model) {
    Page<DlqEntry> entries = dlqEntryRepository.findAll(
        PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
    );
    model.addAttribute("entries", entries);
    model.addAttribute("currentPage", page);
    model.addAttribute("totalPages", entries.getTotalPages());
    return "admin/dlq";
}
}