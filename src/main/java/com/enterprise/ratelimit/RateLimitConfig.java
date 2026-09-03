package com.enterprise.ratelimit;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import org.hibernate.annotations.Filter;

@Entity
@Table(name = "rate_limit_config")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public class RateLimitConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "api_key_prefix", nullable = false)
    private String apiKeyPrefix;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "payment_limit", nullable = false)
    private Integer paymentLimit = 100;

    @Column(name = "payment_window_seconds", nullable = false)
    private Integer paymentWindowSeconds = 60;

    @Column(name = "read_limit", nullable = false)
    private Integer readLimit = 200;

    @Column(name = "read_window_seconds", nullable = false)
    private Integer readWindowSeconds = 60;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getApiKeyPrefix() { return apiKeyPrefix; }
    public void setApiKeyPrefix(String apiKeyPrefix) { this.apiKeyPrefix = apiKeyPrefix; }

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public Integer getPaymentLimit() { return paymentLimit; }
    public void setPaymentLimit(Integer paymentLimit) { this.paymentLimit = paymentLimit; }

    public Integer getPaymentWindowSeconds() { return paymentWindowSeconds; }
    public void setPaymentWindowSeconds(Integer paymentWindowSeconds) { this.paymentWindowSeconds = paymentWindowSeconds; }

    public Integer getReadLimit() { return readLimit; }
    public void setReadLimit(Integer readLimit) { this.readLimit = readLimit; }

    public Integer getReadWindowSeconds() { return readWindowSeconds; }
    public void setReadWindowSeconds(Integer readWindowSeconds) { this.readWindowSeconds = readWindowSeconds; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}