package com.enterprise.reliability;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "circuit_breaker_state")
public class CircuitBreakerState {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "operation_type", nullable = false, unique = true)
    private String operationType;

    @Column(nullable = false)
    private String state = "CLOSED"; // CLOSED, OPEN, HALF_OPEN

    @Column(name = "failure_count", nullable = false)
    private int failureCount = 0;

    @Column(name = "success_count", nullable = false)
    private int successCount = 0;

    @Column(name = "last_failure_time")
    private LocalDateTime lastFailureTime;

    @Column(name = "last_success_time")
    private LocalDateTime lastSuccessTime;

    @Column(name = "opened_at")
    private LocalDateTime openedAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    // Getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getOperationType() { return operationType; }
    public void setOperationType(String operationType) { this.operationType = operationType; }
    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
    public int getFailureCount() { return failureCount; }
    public void setFailureCount(int failureCount) { this.failureCount = failureCount; }
    public int getSuccessCount() { return successCount; }
    public void setSuccessCount(int successCount) { this.successCount = successCount; }
    public LocalDateTime getLastFailureTime() { return lastFailureTime; }
    public void setLastFailureTime(LocalDateTime lastFailureTime) { this.lastFailureTime = lastFailureTime; }
    public LocalDateTime getLastSuccessTime() { return lastSuccessTime; }
    public void setLastSuccessTime(LocalDateTime lastSuccessTime) { this.lastSuccessTime = lastSuccessTime; }
    public LocalDateTime getOpenedAt() { return openedAt; }
    public void setOpenedAt(LocalDateTime openedAt) { this.openedAt = openedAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}