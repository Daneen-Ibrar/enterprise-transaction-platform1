package com.enterprise.reconciliation;

import com.enterprise.ledger.LedgerEntry;
import com.enterprise.ledger.LedgerRepository;
import com.enterprise.transaction.Transaction;
import com.enterprise.transaction.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationService.class);

    private final TransactionRepository transactionRepository;
    private final LedgerRepository ledgerRepository;
    private final ReconciliationRecordRepository recordRepository;
    private final ReconciliationDetailRepository detailRepository;

    public ReconciliationService(TransactionRepository transactionRepository,
                                 LedgerRepository ledgerRepository,
                                 ReconciliationRecordRepository recordRepository,
                                 ReconciliationDetailRepository detailRepository) {
        this.transactionRepository = transactionRepository;
        this.ledgerRepository = ledgerRepository;
        this.recordRepository = recordRepository;
        this.detailRepository = detailRepository;
    }

    @Transactional
    public ReconciliationRecord runReconciliation() {
        log.info("Starting reconciliation run");
        LocalDateTime startedAt = LocalDateTime.now();

        // Fetch all transactions (or those since last reconciliation – but we keep it simple)
        List<Transaction> transactions = transactionRepository.findAll();
        int total = transactions.size();
        int matched = 0;
        int mismatched = 0;
        int missingLedgerCount = 0;
        int amountMismatchCount = 0;

        // Group ledger entries by transaction_id
        List<LedgerEntry> allLedgers = ledgerRepository.findAll();
        Map<Long, List<LedgerEntry>> ledgersByTx = allLedgers.stream()
                .collect(Collectors.groupingBy(LedgerEntry::getTransactionId));

        ReconciliationRecord record = new ReconciliationRecord();
        record.setStartedAt(startedAt);
        record.setTotalTransactions(total);

        // We'll collect details for mismatches
        List<ReconciliationDetail> details = new java.util.ArrayList<>();

        for (Transaction tx : transactions) {
            List<LedgerEntry> entries = ledgersByTx.getOrDefault(tx.getId(), java.util.Collections.emptyList());
            boolean mismatch = false;

            // Check if any ledger entries exist
            if (entries.isEmpty()) {
                missingLedgerCount++;
                mismatched++;
                mismatch = true;
                ReconciliationDetail detail = new ReconciliationDetail();
                detail.setTransactionId(tx.getId());
                detail.setDiscrepancyType("MISSING_LEDGER");
                detail.setExpectedAmount(tx.getAmount());
                detail.setActualAmount(BigDecimal.ZERO);
                detail.setDescription("No ledger entries found for transaction");
                details.add(detail);
                continue;
            }

            // Compute sum of amounts (should match transaction amount)
            BigDecimal sumDebit = entries.stream()
                    .filter(e -> "DEBIT".equals(e.getEntryType().name()))
                    .map(LedgerEntry::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal sumCredit = entries.stream()
                    .filter(e -> "CREDIT".equals(e.getEntryType().name()))
                    .map(LedgerEntry::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            // For a simple transaction, we expect one debit and one credit of the same amount.
            // We'll check total debit equals transaction amount and total credit equals transaction amount.
            if (sumDebit.compareTo(tx.getAmount()) != 0 || sumCredit.compareTo(tx.getAmount()) != 0) {
                amountMismatchCount++;
                mismatched++;
                mismatch = true;
                ReconciliationDetail detail = new ReconciliationDetail();
                detail.setTransactionId(tx.getId());
                detail.setDiscrepancyType("AMOUNT_MISMATCH");
                detail.setExpectedAmount(tx.getAmount());
                detail.setActualAmount(sumDebit); // or sumCredit
                detail.setDescription(String.format("Debit sum: %.2f, Credit sum: %.2f", sumDebit, sumCredit));
                details.add(detail);
            } else {
                matched++;
            }
        }

        // Save reconciliation record
        record.setCompletedAt(LocalDateTime.now());
        record.setStatus(mismatched == 0 ? "PASS" : "FAIL");
        record.setMatchedTransactions(matched);
        record.setMismatchedTransactions(mismatched);
        record.setMissingLedgerCount(missingLedgerCount);
        record.setAmountMismatchCount(amountMismatchCount);
        record.setDetails(String.format("Total: %d, Matched: %d, Mismatched: %d", total, matched, mismatched));

        ReconciliationRecord savedRecord = recordRepository.save(record);

        // Save details
        for (ReconciliationDetail detail : details) {
            detail.setReconciliationRecord(savedRecord);
            detailRepository.save(detail);
        }

        log.info("Reconciliation completed: status={}, total={}, mismatched={}", record.getStatus(), total, mismatched);
        return savedRecord;
    }

    public List<ReconciliationRecord> getRecentReports() {
        return recordRepository.findTop10ByOrderByCreatedAtDesc();
    }

    public List<ReconciliationDetail> getDetailsForRecord(Long recordId) {
        return detailRepository.findByReconciliationRecordId(recordId);
    }
}