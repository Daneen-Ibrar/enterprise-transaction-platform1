package com.enterprise.apikey;

import com.enterprise.identity.AppUser;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "api_key")
public class ApiKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "key_value", nullable = false)
    private String keyValue; // Display version only (first 8 chars + suffix)

    @Column(name = "key_hash", nullable = false, unique = true)
    private String keyHash; // Hashed version for verification

    @Column(name = "key_prefix", nullable = false)
    private String keyPrefix; // First 8 chars for identification

    @Column(nullable = false)
    private String name;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    // ===== CONSTRUCTORS =====
    public ApiKey() {}

    public ApiKey(String keyValue, String keyHash, String keyPrefix, String name, AppUser user, Long tenantId) {
        this.keyValue = keyValue;
        this.keyHash = keyHash;
        this.keyPrefix = keyPrefix;
        this.name = name;
        this.user = user;
        this.tenantId = tenantId;
        this.active = true;
    }

    // ===== GETTERS AND SETTERS =====
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getKeyValue() {
        return keyValue;
    }

    public void setKeyValue(String keyValue) {
        this.keyValue = keyValue;
    }

    public String getKeyHash() {
        return keyHash;
    }

    public void setKeyHash(String keyHash) {
        this.keyHash = keyHash;
    }

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public void setKeyPrefix(String keyPrefix) {
        this.keyPrefix = keyPrefix;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public AppUser getUser() {
        return user;
    }

    public void setUser(AppUser user) {
        this.user = user;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public LocalDateTime getLastUsedAt() {
        return lastUsedAt;
    }

    public void setLastUsedAt(LocalDateTime lastUsedAt) {
        this.lastUsedAt = lastUsedAt;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }
}