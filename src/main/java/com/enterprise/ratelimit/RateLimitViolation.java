package com.enterprise.ratelimit;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import org.hibernate.annotations.Filter;

@Entity
@Table(name = "rate_limit_violation")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public class RateLimitViolation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "api_key_prefix", nullable = false)
    private String apiKeyPrefix;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "limit_type", nullable = false)
    private String limitType; // PAYMENT, READ

    @Column(name = "limit_value", nullable = false)
    private Integer limitValue;

    @Column(name = "actual_usage", nullable = false)
    private Integer actualUsage;

    @Column(name = "request_path")
    private String requestPath;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getApiKeyPrefix() { return apiKeyPrefix; }
    public void setApiKeyPrefix(String apiKeyPrefix) { this.apiKeyPrefix = apiKeyPrefix; }

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public String getLimitType() { return limitType; }
    public void setLimitType(String limitType) { this.limitType = limitType; }

    public Integer getLimitValue() { return limitValue; }
    public void setLimitValue(Integer limitValue) { this.limitValue = limitValue; }

    public Integer getActualUsage() { return actualUsage; }
    public void setActualUsage(Integer actualUsage) { this.actualUsage = actualUsage; }

    public String getRequestPath() { return requestPath; }
    public void setRequestPath(String requestPath) { this.requestPath = requestPath; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}