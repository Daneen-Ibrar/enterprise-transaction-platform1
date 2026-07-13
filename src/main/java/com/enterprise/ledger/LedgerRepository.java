package com.enterprise.ledger;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface LedgerRepository extends JpaRepository<LedgerEntry, Long> {

    List<LedgerEntry> findByTransactionId(Long transactionId);

    List<LedgerEntry> findByAccountId(Long accountId);

    List<LedgerEntry> findByTransactionIdIn(List<Long> transactionIds);

    // For reconciliation: get all entries ordered by transaction (optional)
    List<LedgerEntry> findAllByOrderByCreatedAtAsc();
}