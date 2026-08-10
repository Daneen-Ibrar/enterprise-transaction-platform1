package com.enterprise.api;

import com.enterprise.audit.AuditRepository;
import com.enterprise.reconciliation.ReconciliationRecord;
import com.enterprise.reconciliation.ReconciliationRecordRepository;
import com.enterprise.reliability.DlqEntry;
import com.enterprise.reliability.DlqEntryRepository;
import com.enterprise.transaction.Transaction;
import com.enterprise.transaction.TransactionRepository;
import com.enterprise.transaction.TransactionSpecifications;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Controller
@RequestMapping("/admin")
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MERCHANT_ADMIN')")
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
    public String transactions(
            @RequestParam(required = false) Long invoiceId,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) Long merchantId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) BigDecimal minAmount,
            @RequestParam(required = false) BigDecimal maxAmount,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Model model) {

        Specification<Transaction> spec = Specification
                .where(TransactionSpecifications.hasInvoiceId(invoiceId))
                .and(TransactionSpecifications.hasCustomerId(customerId))
                .and(TransactionSpecifications.hasMerchantId(merchantId))
                .and(TransactionSpecifications.hasStatus(status))
                .and(TransactionSpecifications.amountBetween(minAmount, maxAmount))
                .and(TransactionSpecifications.createdBetween(startDate, endDate));

        Page<Transaction> transactions = transactionRepository.findAll(spec,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));

        model.addAttribute("transactions", transactions);
        model.addAttribute("invoiceId", invoiceId);
        model.addAttribute("customerId", customerId);
        model.addAttribute("merchantId", merchantId);
        model.addAttribute("status", status);
        model.addAttribute("minAmount", minAmount);
        model.addAttribute("maxAmount", maxAmount);
        model.addAttribute("startDate", startDate);
        model.addAttribute("endDate", endDate);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", transactions.getTotalPages());

        return "admin/transactions";
    }

    // ⚠️ REMOVED: reconciliation() method – now handled by ReconciliationDetailController

    @GetMapping("/dlq")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
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