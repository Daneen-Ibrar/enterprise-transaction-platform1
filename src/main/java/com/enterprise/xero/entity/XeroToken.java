package com.enterprise.xero.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "xero_token")
public class XeroToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "access_token", columnDefinition = "TEXT", nullable = false)
    private String accessToken;

    @Column(name = "refresh_token", columnDefinition = "TEXT", nullable = false)
    private String refreshToken;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "xero_tenant_id", nullable = false)
    private String xeroTenantId;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    // ✅ NEW: Store the tenant's own client ID and secret
    @Column(name = "client_id", nullable = false)
    private String clientId;

    @Column(name = "client_secret", nullable = false)
    private String clientSecret;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    // ✅ NEW: Check if tenant has configured their own credentials
    @Transient
    private boolean usesCustomCredentials = false;

    // Getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getAccessToken() { return accessToken; }
    public void setAccessToken(String accessToken) { this.accessToken = accessToken; }

    public String getRefreshToken() { return refreshToken; }
    public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }

    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }

    public String getXeroTenantId() { return xeroTenantId; }
    public void setXeroTenantId(String xeroTenantId) { this.xeroTenantId = xeroTenantId; }

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }

    public String getClientSecret() { return clientSecret; }
    public void setClientSecret(String clientSecret) { this.clientSecret = clientSecret; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public boolean isUsesCustomCredentials() { return usesCustomCredentials; }
    public void setUsesCustomCredentials(boolean usesCustomCredentials) { this.usesCustomCredentials = usesCustomCredentials; }
}