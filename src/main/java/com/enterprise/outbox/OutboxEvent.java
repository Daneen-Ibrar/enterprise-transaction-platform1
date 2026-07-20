package com.enterprise.outbox;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "outbox_event")
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "aggregate_id", nullable = false)
    private Long aggregateId;

    @Column(name = "aggregate_type", nullable = false)
    private String aggregateType;

    @Column(columnDefinition = "JSONB", nullable = false)
    private String payload;

    @Column(columnDefinition = "JSONB")
    private String headers;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private OutboxStatus status = OutboxStatus.PENDING;

    @Column(name = "retry_count", nullable = false)
    private int retryCount = 0;

    @Column(name = "max_retries", nullable = false)
    private int maxRetries = 3;

    @Column(name = "next_attempt_at", nullable = false)
    private LocalDateTime nextAttemptAt = LocalDateTime.now();

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // ===== CONSTRUCTORS =====
    public OutboxEvent() {}

    public OutboxEvent(String eventType, Long aggregateId, String aggregateType,
                       String payload, String headers) {
        this.eventType = eventType;
        this.aggregateId = aggregateId;
        this.aggregateType = aggregateType;
        this.payload = payload;
        this.headers = headers;
        this.status = OutboxStatus.PENDING;
        this.nextAttemptAt = LocalDateTime.now();
        this.retryCount = 0;
        this.maxRetries = 3;
    }

    // ===== GETTERS AND SETTERS =====
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public Long getAggregateId() {
        return aggregateId;
    }

    public void setAggregateId(Long aggregateId) {
        this.aggregateId = aggregateId;
    }

    public String getAggregateType() {
        return aggregateType;
    }

    public void setAggregateType(String aggregateType) {
        this.aggregateType = aggregateType;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public String getHeaders() {
        return headers;
    }

    public void setHeaders(String headers) {
        this.headers = headers;
    }

    public OutboxStatus getStatus() {
        return status;
    }

    public void setStatus(OutboxStatus status) {
        this.status = status;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    public LocalDateTime getNextAttemptAt() {
        return nextAttemptAt;
    }

    public void setNextAttemptAt(LocalDateTime nextAttemptAt) {
        this.nextAttemptAt = nextAttemptAt;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
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

    // ===== HELPER METHODS =====
    public boolean isPermanentFailure() {
        return status == OutboxStatus.FAILED && retryCount >= maxRetries;
    }

    public boolean isRetryable() {
        return status == OutboxStatus.PENDING || status == OutboxStatus.PROCESSING;
    }

    public void incrementRetry() {
        this.retryCount++;
        this.updatedAt = LocalDateTime.now();
    }

    public void markDelivered() {
        this.status = OutboxStatus.DELIVERED;
        this.completedAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public void markFailed(String error) {
        this.status = OutboxStatus.FAILED;
        this.lastError = error;
        this.updatedAt = LocalDateTime.now();
    }

    public void markProcessing() {
        this.status = OutboxStatus.PROCESSING;
        this.updatedAt = LocalDateTime.now();
    }

    @Override
    public String toString() {
        return "OutboxEvent{" +
                "id=" + id +
                ", eventType='" + eventType + '\'' +
                ", aggregateId=" + aggregateId +
                ", status=" + status +
                ", retryCount=" + retryCount +
                ", createdAt=" + createdAt +
                '}';
    }
}