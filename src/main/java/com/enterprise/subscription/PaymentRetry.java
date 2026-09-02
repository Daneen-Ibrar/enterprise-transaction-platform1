package com.enterprise.subscription;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "payment_retry")
public class PaymentRetry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "subscription_id", nullable = false)
    private Long subscriptionId;

    @Column(name = "attempt_number", nullable = false)
    private int attemptNumber = 1;

    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts = 3;

    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    private RetryStatus status = RetryStatus.PENDING;

    @Column(name = "last_attempt_at")
    private LocalDateTime lastAttemptAt;

    @Column(name = "next_attempt_at")
    private LocalDateTime nextAttemptAt;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public enum RetryStatus {
        PENDING, SUCCESS, FAILED, CANCELLED
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getSubscriptionId() { return subscriptionId; }
    public void setSubscriptionId(Long subscriptionId) { this.subscriptionId = subscriptionId; }

    public int getAttemptNumber() { return attemptNumber; }
    public void setAttemptNumber(int attemptNumber) { this.attemptNumber = attemptNumber; }

    public int getMaxAttempts() { return maxAttempts; }
    public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }

    public RetryStatus getStatus() { return status; }
    public void setStatus(RetryStatus status) { this.status = status; }

    public LocalDateTime getLastAttemptAt() { return lastAttemptAt; }
    public void setLastAttemptAt(LocalDateTime lastAttemptAt) { this.lastAttemptAt = lastAttemptAt; }

    public LocalDateTime getNextAttemptAt() { return nextAttemptAt; }
    public void setNextAttemptAt(LocalDateTime nextAttemptAt) { this.nextAttemptAt = nextAttemptAt; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public boolean canRetry() {
        return status == RetryStatus.PENDING && attemptNumber < maxAttempts;
    }

    public void incrementAttempt() {
        this.attemptNumber++;
        this.lastAttemptAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public void markSuccess() {
        this.status = RetryStatus.SUCCESS;
        this.updatedAt = LocalDateTime.now();
    }

    public void markFailed(String error) {
        this.status = RetryStatus.FAILED;
        this.errorMessage = error;
        this.updatedAt = LocalDateTime.now();
    }
}