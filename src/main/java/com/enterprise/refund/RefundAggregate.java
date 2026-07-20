package com.enterprise.refund;

import com.enterprise.money.Money;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "refund")
public class RefundAggregate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transaction_id", nullable = false)
    private Long transactionId;

    @Column(name = "amount", nullable = false)
    @Convert(converter = com.enterprise.money.MoneyConverter.class)
    private Money amount;

    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;

    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    private RefundStatus status = RefundStatus.PENDING;

    @Column(name = "idempotency_key", nullable = false, unique = true)
    private String idempotencyKey;

    @Column(name = "processed_by")
    private Long processedBy;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    // ===== CONSTRUCTORS =====
    public RefundAggregate() {}

    public RefundAggregate(Long transactionId, Money amount, String reason, String idempotencyKey) {
        this.transactionId = transactionId;
        this.amount = amount;
        this.reason = reason;
        this.idempotencyKey = idempotencyKey;
        this.status = RefundStatus.PENDING;
        this.processedAt = null;
        this.processedBy = null;
        this.version = 0L;
    }

    // ===== BUSINESS METHODS =====
    /**
     * Process the refund – called when the refund is successfully completed.
     */
    public void process(Long processedBy) {
        if (!isProcessable()) {
            throw new IllegalStateException("Refund cannot be processed. Current status: " + status);
        }
        this.status = RefundStatus.PROCESSED;
        this.processedBy = processedBy;
        this.processedAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Approve the refund – usually for admin approval workflows.
     */
    public void approve() {
        if (this.status != RefundStatus.PENDING) {
            throw new IllegalStateException("Only pending refunds can be approved. Current status: " + status);
        }
        this.status = RefundStatus.APPROVED;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Reject the refund.
     */
    public void reject() {
        if (this.status != RefundStatus.PENDING && this.status != RefundStatus.APPROVED) {
            throw new IllegalStateException("Only pending or approved refunds can be rejected. Current status: " + status);
        }
        this.status = RefundStatus.REJECTED;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Mark the refund as failed.
     */
    public void fail() {
        if (this.status == RefundStatus.PROCESSED) {
            throw new IllegalStateException("Cannot fail a refund that is already processed.");
        }
        this.status = RefundStatus.FAILED;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Check if the refund can be processed.
     */
    public boolean isProcessable() {
        return this.status == RefundStatus.PENDING || this.status == RefundStatus.APPROVED;
    }

    /**
     * Check if the refund has been completed.
     */
    public boolean isCompleted() {
        return this.status == RefundStatus.PROCESSED || this.status == RefundStatus.REJECTED;
    }

    // ===== GETTERS AND SETTERS =====
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(Long transactionId) {
        this.transactionId = transactionId;
    }

    public Money getAmount() {
        return amount;
    }

    public void setAmount(Money amount) {
        this.amount = amount;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public RefundStatus getStatus() {
        return status;
    }

    public void setStatus(RefundStatus status) {
        this.status = status;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public Long getProcessedBy() {
        return processedBy;
    }

    public void setProcessedBy(Long processedBy) {
        this.processedBy = processedBy;
    }

    public LocalDateTime getProcessedAt() {
        return processedAt;
    }

    public void setProcessedAt(LocalDateTime processedAt) {
        this.processedAt = processedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    // ===== HELPER METHODS =====
    @Override
    public String toString() {
        return "RefundAggregate{" +
                "id=" + id +
                ", transactionId=" + transactionId +
                ", amount=" + amount +
                ", reason='" + reason + '\'' +
                ", status=" + status +
                ", idempotencyKey='" + idempotencyKey + '\'' +
                ", processedBy=" + processedBy +
                ", processedAt=" + processedAt +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                ", version=" + version +
                '}';
    }
}