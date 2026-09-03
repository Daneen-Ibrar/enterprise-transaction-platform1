package com.enterprise.tax;

import jakarta.persistence.*;
import org.hibernate.annotations.Filter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "tax_rule")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public class TaxRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "country_code", nullable = false, length = 2)
    private String countryCode;

    @Column(name = "region_code", length = 10)
    private String regionCode;

    @Column(name = "tax_rate", nullable = false, precision = 10, scale = 4)
    private BigDecimal taxRate;

    @Column(name = "tax_name", nullable = false)
    private String taxName; // VAT, GST, Sales Tax

    @Column(name = "is_default", nullable = false)
    private boolean isDefault = false;

    @Column(name = "is_b2b_exempt", nullable = false)
    private boolean isB2BExempt = false;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCountryCode() { return countryCode; }
    public void setCountryCode(String countryCode) { this.countryCode = countryCode; }

    public String getRegionCode() { return regionCode; }
    public void setRegionCode(String regionCode) { this.regionCode = regionCode; }

    public BigDecimal getTaxRate() { return taxRate; }
    public void setTaxRate(BigDecimal taxRate) { this.taxRate = taxRate; }

    public String getTaxName() { return taxName; }
    public void setTaxName(String taxName) { this.taxName = taxName; }

    public boolean isDefault() { return isDefault; }
    public void setDefault(boolean aDefault) { isDefault = aDefault; }

    public boolean isB2BExempt() { return isB2BExempt; }
    public void setB2BExempt(boolean b2BExempt) { isB2BExempt = b2BExempt; }

    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { isActive = active; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}