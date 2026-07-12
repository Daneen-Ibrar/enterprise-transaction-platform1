package com.enterprise.api;

import com.enterprise.audit.AuditRepository;
import com.enterprise.reconciliation.ReconciliationRecordRepository;
import com.enterprise.transaction.TransactionRepository;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/export")
@PreAuthorize("hasRole('ADMIN')")
public class ExportController {

    private final TransactionRepository transactionRepository;
    private final AuditRepository auditRepository;
    private final ReconciliationRecordRepository reconciliationRecordRepository;

    public ExportController(TransactionRepository transactionRepository,
                            AuditRepository auditRepository,
                            ReconciliationRecordRepository reconciliationRecordRepository) {
        this.transactionRepository = transactionRepository;
        this.auditRepository = auditRepository;
        this.reconciliationRecordRepository = reconciliationRecordRepository;
    }

    @GetMapping("/transactions/csv")
    public ResponseEntity<String> exportTransactionsCSV() {
        var transactions = transactionRepository.findAll();
        String csv = "ID,Invoice,Customer,Merchant,Amount,Status,Created\n" +
                transactions.stream()
                        .map(tx -> String.format("%d,%d,%d,%d,%.2f,%s,%s",
                                tx.getId(), tx.getInvoiceId(), tx.getCustomerId(),
                                tx.getMerchantId(), tx.getAmount(), tx.getStatus(), tx.getCreatedAt()))
                        .collect(Collectors.joining("\n"));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=transactions.csv")
                .contentType(MediaType.TEXT_PLAIN)
                .body(csv);
    }

    // Similarly add endpoints for audit and reconciliation CSV/JSON
}