package com.enterprise.events;

import com.enterprise.transaction.Transaction;

public class RefundProcessedEvent {
    private final Transaction refundTransaction;
    private final Transaction originalTransaction;

    public RefundProcessedEvent(Transaction refundTransaction, Transaction originalTransaction) {
        this.refundTransaction = refundTransaction;
        this.originalTransaction = originalTransaction;
    }

    public Transaction getRefundTransaction() { return refundTransaction; }
    public Transaction getOriginalTransaction() { return originalTransaction; }
}