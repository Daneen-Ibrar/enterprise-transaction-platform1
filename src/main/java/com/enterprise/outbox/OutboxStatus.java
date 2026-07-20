package com.enterprise.outbox;

public enum OutboxStatus {
    PENDING,
    PROCESSING,
    DELIVERED,
    FAILED,
    SKIPPED
}