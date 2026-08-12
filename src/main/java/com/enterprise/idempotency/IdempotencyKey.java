package com.enterprise.idempotency;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import org.hibernate.annotations.Filter;

@Entity
@Table(name = "idempotency_key")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public class IdempotencyKey {

    @Id
    @Column(name = "key", nullable = false, unique = true)
    private String key;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String response;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "request_fingerprint", length = 64)
    private String requestFingerprint;

    @Column(name = "tenant_id", nullable = false)   // <-- new field
    private Long tenantId;

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

    public String getRequestFingerprint() { return requestFingerprint; }
    public void setRequestFingerprint(String requestFingerprint) { this.requestFingerprint = requestFingerprint; }

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
}