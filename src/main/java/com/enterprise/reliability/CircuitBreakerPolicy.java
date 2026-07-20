package com.enterprise.reliability;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "circuit_breaker_policy")
public class CircuitBreakerPolicy {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "operation_type", nullable = false, unique = true)
    private String operationType;

    @Column(name = "failure_threshold", nullable = false)
    private int failureThreshold;

    @Column(name = "success_threshold", nullable = false)
    private int successThreshold;

    @Column(name = "timeout_ms", nullable = false)
    private long timeoutMs;

    @Column(name = "evaluation_window_sec", nullable = false)
    private int evaluationWindowSec;

    private boolean active = true;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    // Getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getOperationType() { return operationType; }
    public void setOperationType(String operationType) { this.operationType = operationType; }
    public int getFailureThreshold() { return failureThreshold; }
    public void setFailureThreshold(int failureThreshold) { this.failureThreshold = failureThreshold; }
    public int getSuccessThreshold() { return successThreshold; }
    public void setSuccessThreshold(int successThreshold) { this.successThreshold = successThreshold; }
    public long getTimeoutMs() { return timeoutMs; }
    public void setTimeoutMs(long timeoutMs) { this.timeoutMs = timeoutMs; }
    public int getEvaluationWindowSec() { return evaluationWindowSec; }
    public void setEvaluationWindowSec(int evaluationWindowSec) { this.evaluationWindowSec = evaluationWindowSec; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}