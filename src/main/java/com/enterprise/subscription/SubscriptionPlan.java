package com.enterprise.subscription;

import jakarta.persistence.*;
import org.hibernate.annotations.Filter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "subscription_plan")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public class SubscriptionPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(name = "billing_period", nullable = false)
    @Enumerated(EnumType.STRING)
    private BillingPeriod billingPeriod;

    @Column(name = "price", nullable = false, precision = 19, scale = 2)
    private BigDecimal price;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "GBP";

    @Column(name = "is_active")
    private boolean isActive = true;

    @Column(name = "trial_days")
    private Integer trialDays = 0;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    public enum BillingPeriod {
        MONTHLY, QUARTERLY, YEARLY
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public BillingPeriod getBillingPeriod() { return billingPeriod; }
    public void setBillingPeriod(BillingPeriod billingPeriod) { this.billingPeriod = billingPeriod; }

    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { isActive = active; }

    public Integer getTrialDays() { return trialDays; }
    public void setTrialDays(Integer trialDays) { this.trialDays = trialDays; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }

    public int getMonthsInPeriod() {
        return switch (billingPeriod) {
            case MONTHLY -> 1;
            case QUARTERLY -> 3;
            case YEARLY -> 12;
        };
    }
}