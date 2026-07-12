package com.enterprise.idempotency;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "idempotency_key")

public class IdempotencyKey {

    @Id
    @Column(name = "key", nullable = false, unique = true)
    private String key;

    @Column(columnDefinition = "JSONB", nullable = false)
    private String response;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    // Constructors
    public IdempotencyKey() {}

    public IdempotencyKey(String key, String response, LocalDateTime expiresAt) {
        this.key = key;
        this.response = response;
        this.expiresAt = expiresAt;
    }

    // Getters and setters
    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }
    public String getResponse() { return response; }
    public void setResponse(String response) { this.response = response; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
}