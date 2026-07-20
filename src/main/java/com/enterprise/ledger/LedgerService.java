package com.enterprise.ledger;

import com.enterprise.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class LedgerService {

    private static final Logger log = LoggerFactory.getLogger(LedgerService.class);

    private final LedgerRepository ledgerRepository;

    public LedgerService(LedgerRepository ledgerRepository) {
        this.ledgerRepository = ledgerRepository;
    }

    @Transactional
    public void recordDebit(Long accountId, BigDecimal amount, Long transactionId) {
        BigDecimal current = getBalance(accountId);
        BigDecimal newBalance = current.subtract(amount);
        LedgerEntry entry = new LedgerEntry(transactionId, EntryType.DEBIT, amount, newBalance);
        entry.setAccountId(accountId);
        entry.setTenantId(TenantContext.getTenantId() != null ? TenantContext.getTenantId() : 1L);
        ledgerRepository.save(entry);
        log.info("Debit recorded: account={}, amount={}, newBalance={}, txId={}",
                accountId, amount, newBalance, transactionId);
    }

    @Transactional
    public void recordCredit(Long accountId, BigDecimal amount, Long transactionId) {
        BigDecimal current = getBalance(accountId);
        BigDecimal newBalance = current.add(amount);
        LedgerEntry entry = new LedgerEntry(transactionId, EntryType.CREDIT, amount, newBalance);
        entry.setAccountId(accountId);
        entry.setTenantId(TenantContext.getTenantId() != null ? TenantContext.getTenantId() : 1L);
        ledgerRepository.save(entry);
        log.info("Credit recorded: account={}, amount={}, newBalance={}, txId={}",
                accountId, amount, newBalance, transactionId);
    }

    public BigDecimal getBalance(Long accountId) {
        List<LedgerEntry> entries = ledgerRepository.findByAccountId(accountId);
        BigDecimal balance = BigDecimal.ZERO;
        for (LedgerEntry entry : entries) {
            if (EntryType.CREDIT.equals(entry.getEntryType())) {
                balance = balance.add(entry.getAmount());
            } else {
                balance = balance.subtract(entry.getAmount());
            }
        }
        return balance;
    }

    // For reconciliation – fetch all entries with optional tenant filter (handled by Hibernate)
    public List<LedgerEntry> getAllEntries() {
        return ledgerRepository.findAll();
    }

    // Rollback / reversal: create compensating entries
    @Transactional
    public void reverseTransaction(Long transactionId, BigDecimal amount, Long accountId) {
        // Find existing entries for this transaction
        List<LedgerEntry> entries = ledgerRepository.findByTransactionId(transactionId);
        for (LedgerEntry entry : entries) {
            // Create opposite entry to reverse
            EntryType opposite = entry.getEntryType() == EntryType.DEBIT ? EntryType.CREDIT : EntryType.DEBIT;
            LedgerEntry reverseEntry = new LedgerEntry(transactionId, opposite, entry.getAmount(), getBalance(accountId));
            reverseEntry.setAccountId(accountId);
            reverseEntry.setTenantId(entry.getTenantId());
            ledgerRepository.save(reverseEntry);
        }
        log.info("Transaction {} reversed for account {}", transactionId, accountId);
    }
}