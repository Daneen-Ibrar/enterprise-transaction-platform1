package com.enterprise.ledger;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Service
public class LedgerService {

    // Simple in-memory balance store (for demo – will be replaced with real persistence in later milestones)
    private final Map<Long, BigDecimal> balances = new HashMap<>();

    @Transactional
    public void recordDebit(Long accountId, BigDecimal amount, Long transactionId) {
        // For demo, we store balances in memory; in production, this would be a real account table.
        BigDecimal current = balances.getOrDefault(accountId, BigDecimal.ZERO);
        BigDecimal newBalance = current.subtract(amount);
        balances.put(accountId, newBalance);

        // Persist the ledger entry
        LedgerEntry entry = new LedgerEntry(transactionId, EntryType.DEBIT, amount, newBalance);
        // ledgerRepository.save(entry); // will be added when repository is injected
    }

    @Transactional
    public void recordCredit(Long accountId, BigDecimal amount, Long transactionId) {
        BigDecimal current = balances.getOrDefault(accountId, BigDecimal.ZERO);
        BigDecimal newBalance = current.add(amount);
        balances.put(accountId, newBalance);

        LedgerEntry entry = new LedgerEntry(transactionId, EntryType.CREDIT, amount, newBalance);
        // ledgerRepository.save(entry);
    }

    @Transactional
public void reverseTransaction(Long transactionId, BigDecimal amount) {
    // Fetch all ledger entries for the transaction and create opposite entries.
    // Or just record compensation entries.
    // This is a placeholder – implement based on your ledger model.
}


    // For testing
    public BigDecimal getBalance(Long accountId) {
        return balances.getOrDefault(accountId, BigDecimal.ZERO);
    }
}