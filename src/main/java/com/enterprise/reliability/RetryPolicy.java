package com.enterprise.reliability;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "retry_policy")
public class RetryPolicy {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "operation_type", nullable = false, unique = true)
    private String operationType;

    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts;

    @Column(name = "backoff_strategy", nullable = false)
    private String backoffStrategy; // FIXED, LINEAR, EXPONENTIAL

    @Column(name = "base_delay_ms", nullable = false)
    private long baseDelayMs;

    @Column(name = "max_delay_ms", nullable = false)
    private long maxDelayMs;

    @Column(name = "jitter_enabled")
    private boolean jitterEnabled = true;

    @Column(nullable = false)
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
    public int getMaxAttempts() { return maxAttempts; }
    public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }
    public String getBackoffStrategy() { return backoffStrategy; }
    public void setBackoffStrategy(String backoffStrategy) { this.backoffStrategy = backoffStrategy; }
    public long getBaseDelayMs() { return baseDelayMs; }
    public void setBaseDelayMs(long baseDelayMs) { this.baseDelayMs = baseDelayMs; }
    public long getMaxDelayMs() { return maxDelayMs; }
    public void setMaxDelayMs(long maxDelayMs) { this.maxDelayMs = maxDelayMs; }
    public boolean isJitterEnabled() { return jitterEnabled; }
    public void setJitterEnabled(boolean jitterEnabled) { this.jitterEnabled = jitterEnabled; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}