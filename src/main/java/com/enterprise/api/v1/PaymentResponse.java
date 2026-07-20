package com.enterprise.api.v1;

import com.enterprise.money.Money;
import com.enterprise.transaction.Transaction;
import java.time.LocalDateTime;

public class PaymentResponse {

    private final Long transactionId;
    private final String status;
    private final Money amount;
    private final Long invoiceId;
    private final LocalDateTime createdAt;
    private final String idempotencyKey;

    public PaymentResponse(Long transactionId, String status, Money amount,
                           Long invoiceId, LocalDateTime createdAt, String idempotencyKey) {
        this.transactionId = transactionId;
        this.status = status;
        this.amount = amount;
        this.invoiceId = invoiceId;
        this.createdAt = createdAt;
        this.idempotencyKey = idempotencyKey;
    }

    public Long getTransactionId() { return transactionId; }
    public String getStatus() { return status; }
    public Money getAmount() { return amount; }
    public Long getInvoiceId() { return invoiceId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public String getIdempotencyKey() { return idempotencyKey; }

    public static PaymentResponse from(Transaction tx, Money money) {
        return new PaymentResponse(
            tx.getId(),
            tx.getStatus().name(),
            money,
            tx.getInvoiceId(),
            tx.getCreatedAt(),
            tx.getIdempotencyKey()
        );
    }
}
