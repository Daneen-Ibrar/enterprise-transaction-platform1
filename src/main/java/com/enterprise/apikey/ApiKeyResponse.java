package com.enterprise.apikey;

import java.time.LocalDateTime;

public class ApiKeyResponse {
    private Long id;
    private String keyValue;
    private String name;
    private Long userId;
    private String userEmail;
    private boolean active;
    private LocalDateTime lastUsedAt;
    private LocalDateTime createdAt;

    public ApiKeyResponse(ApiKey apiKey) {
        this.id = apiKey.getId();
        // keyValue should be the FULL key when generated
        // For existing keys, it's the prefix
        this.keyValue = apiKey.getKeyValue();
        this.name = apiKey.getName();
        this.userId = apiKey.getUser().getId();
        this.userEmail = apiKey.getUser().getEmail();
        this.active = apiKey.isActive();
        this.lastUsedAt = apiKey.getLastUsedAt();
        this.createdAt = apiKey.getCreatedAt();
    }

    // Getters and setters...
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getKeyValue() { return keyValue; }
    public void setKeyValue(String keyValue) { this.keyValue = keyValue; }
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
}