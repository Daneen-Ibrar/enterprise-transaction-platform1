package com.enterprise.reconciliation;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "reconciliation_detail")
public class ReconciliationDetail {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "reconciliation_record_id", nullable = false)
    private ReconciliationRecord reconciliationRecord;

    @Column(name = "transaction_id", nullable = false)
    private Long transactionId;

    @Column(name = "discrepancy_type", nullable = false)
    private String discrepancyType; // MISSING_LEDGER, AMOUNT_MISMATCH, DUPLICATE

    @Column(name = "expected_amount", precision = 19, scale = 2)
    private BigDecimal expectedAmount;

    @Column(name = "actual_amount", precision = 19, scale = 2)
    private BigDecimal actualAmount;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    // Getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public ReconciliationRecord getReconciliationRecord() { return reconciliationRecord; }
    public void setReconciliationRecord(ReconciliationRecord reconciliationRecord) { this.reconciliationRecord = reconciliationRecord; }
    public Long getTransactionId() { return transactionId; }
    public void setTransactionId(Long transactionId) { this.transactionId = transactionId; }
    public String getDiscrepancyType() { return discrepancyType; }
    public void setDiscrepancyType(String discrepancyType) { this.discrepancyType = discrepancyType; }
    public BigDecimal getExpectedAmount() { return expectedAmount; }
    public void setExpectedAmount(BigDecimal expectedAmount) { this.expectedAmount = expectedAmount; }
    public BigDecimal getActualAmount() { return actualAmount; }
    public void setActualAmount(BigDecimal actualAmount) { this.actualAmount = actualAmount; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}