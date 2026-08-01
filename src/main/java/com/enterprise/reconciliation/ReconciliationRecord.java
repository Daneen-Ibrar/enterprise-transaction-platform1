package com.enterprise.reconciliation;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import org.hibernate.annotations.Filter;

@Entity
@Table(name = "reconciliation_record")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public class ReconciliationRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "completed_at", nullable = false)
    private LocalDateTime completedAt;

    @Column(nullable = false)
    private String status; // PASS, FAIL

    @Column(name = "total_transactions", nullable = false)
    private int totalTransactions;

    @Column(name = "matched_transactions", nullable = false)
    private int matchedTransactions;

    @Column(name = "mismatched_transactions", nullable = false)
    private int mismatchedTransactions;

    @Column(name = "missing_ledger_count", nullable = false)
    private int missingLedgerCount;

    @Column(name = "amount_mismatch_count", nullable = false)
    private int amountMismatchCount;

    @Column(columnDefinition = "TEXT")
    private String details;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    // ----- TENANT ID -----
    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    // Getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public int getTotalTransactions() { return totalTransactions; }
    public void setTotalTransactions(int totalTransactions) { this.totalTransactions = totalTransactions; }
    public int getMatchedTransactions() { return matchedTransactions; }
    public void setMatchedTransactions(int matchedTransactions) { this.matchedTransactions = matchedTransactions; }
    public int getMismatchedTransactions() { return mismatchedTransactions; }
    public void setMismatchedTransactions(int mismatchedTransactions) { this.mismatchedTransactions = mismatchedTransactions; }
    public int getMissingLedgerCount() { return missingLedgerCount; }
    public void setMissingLedgerCount(int missingLedgerCount) { this.missingLedgerCount = missingLedgerCount; }
    public int getAmountMismatchCount() { return amountMismatchCount; }
    public void setAmountMismatchCount(int amountMismatchCount) { this.amountMismatchCount = amountMismatchCount; }
    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
}