package com.enterprise.events;

import com.enterprise.transaction.Transaction;

public class TransactionSettledEvent {
    private final Transaction transaction;

    public TransactionSettledEvent(Transaction transaction) {
        this.transaction = transaction;
    }

    public Transaction getTransaction() { return transaction; }
}