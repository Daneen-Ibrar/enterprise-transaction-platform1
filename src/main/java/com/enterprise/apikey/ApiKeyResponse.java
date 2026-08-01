package com.enterprise.apikey;

import java.time.LocalDateTime;

public class ApiKeyResponse {
    private Long id;
    private String keyValue;       // full key (used only during generation popup)
    private String maskedKey;      // masked version for table display
    private String name;
    private Long userId;
    private String userEmail;
    private boolean active;
    private LocalDateTime lastUsedAt;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;   // <-- NEW

    public ApiKeyResponse(ApiKey apiKey) {
        this.id = apiKey.getId();
        this.keyValue = apiKey.getKeyValue();
        this.maskedKey = maskKey(apiKey.getKeyValue());
        this.name = apiKey.getName();
        this.userId = apiKey.getUser().getId();
        this.userEmail = apiKey.getUser().getEmail();
        this.active = apiKey.isActive();
        this.lastUsedAt = apiKey.getLastUsedAt();
        this.createdAt = apiKey.getCreatedAt();
        this.expiresAt = apiKey.getExpiresAt();   // <-- NEW
    }

    // Mask key: show first 4 and last 4 characters
    private String maskKey(String key) {
        if (key == null || key.length() < 8) return key;
        return key.substring(0, 4) + "-****-****-" + key.substring(key.length() - 4);
    }

    // Getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getKeyValue() { return keyValue; }
    public void setKeyValue(String keyValue) { this.keyValue = keyValue; }
    public String getMaskedKey() { return maskedKey; }
    public void setMaskedKey(String maskedKey) { this.maskedKey = maskedKey; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getUserEmail() { return userEmail; }
    public void setUserEmail(String userEmail) { this.userEmail = userEmail; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public LocalDateTime getLastUsedAt() { return lastUsedAt; }
    public void setLastUsedAt(LocalDateTime lastUsedAt) { this.lastUsedAt = lastUsedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getExpiresAt() { return expiresAt; }   // <-- NEW
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
}