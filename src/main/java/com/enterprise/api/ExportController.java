package com.enterprise.api;

import com.enterprise.audit.AuditRepository;
import com.enterprise.reconciliation.ReconciliationRecordRepository;
import com.enterprise.transaction.TransactionRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.PrintWriter;

@RestController
@RequestMapping("/export")
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MERCHANT_ADMIN')")
public class ExportController {

    private final TransactionRepository transactionRepository;
    private final AuditRepository auditRepository;
    private final ReconciliationRecordRepository reconciliationRecordRepository;

    private static final int PAGE_SIZE = 1000; // configurable

    public ExportController(TransactionRepository transactionRepository,
                            AuditRepository auditRepository,
                            ReconciliationRecordRepository reconciliationRecordRepository) {
        this.transactionRepository = transactionRepository;
        this.auditRepository = auditRepository;
        this.reconciliationRecordRepository = reconciliationRecordRepository;
    }

    @GetMapping("/transactions/csv")
    public ResponseEntity<StreamingResponseBody> exportTransactionsCSV() {
        StreamingResponseBody stream = outputStream -> {
            PrintWriter writer = new PrintWriter(outputStream);
            writer.println("ID,Invoice,Customer,Merchant,Amount,Status,Created");
            int page = 0;
            Pageable pageable = PageRequest.of(page, PAGE_SIZE);
            var pageResult = transactionRepository.findAll(pageable);
            while (true) {
                var transactions = pageResult.getContent();
                for (var tx : transactions) {
                    writer.printf("%d,%d,%d,%d,%.2f,%s,%s%n",
                            tx.getId(), tx.getInvoiceId(), tx.getCustomerId(),
                            tx.getMerchantId(), tx.getAmount(), tx.getStatus(), tx.getCreatedAt());
                }
                writer.flush();
                if (pageResult.isLast()) {
                    break;
                }
                page++;
                pageable = PageRequest.of(page, PAGE_SIZE);
                pageResult = transactionRepository.findAll(pageable);
            }
        };

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=transactions.csv")
                .contentType(MediaType.TEXT_PLAIN)
                .body(stream);
    }

    // Additional export endpoints (audit, reconciliation) can follow the same pattern.
}