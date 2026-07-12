package com.enterprise.reliability;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import org.hibernate.annotations.Filter;

@Entity
@Table(name = "dlq_entry")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public class DlqEntry {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "operation_type", nullable = false)
    private String operationType;

    @Column(columnDefinition = "JSONB", nullable = false)
    private String payload;

    @Column(name = "failure_reason", nullable = false, columnDefinition = "TEXT")
    private String failureReason;

     @Column(name = "failure_count", nullable = false)
private int failureCount = 1;

    @Column(nullable = false)
    private String status = "PENDING"; // PENDING, RETRYING, RESOLVED, FAILED

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();



    // Getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getOperationType() { return operationType; }
    public void setOperationType(String operationType) { this.operationType = operationType; }
    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }
    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }
    public int getFailureCount() { return failureCount; }
    public void setFailureCount(int failureCount) { this.failureCount = failureCount; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}