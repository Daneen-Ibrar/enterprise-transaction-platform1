package com.enterprise.invoice;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.hibernate.annotations.Filter;

@Entity
@Table(name = "invoice")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public class Invoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(length = 3)
    private String currency = "GBP";

    @Column(nullable = false)
    private String description;

    @Column(name = "customer_email", nullable = false)
    private String customerEmail;

    @Column(name = "merchant_id", nullable = false)
    private Long merchantId;

    @Column(nullable = false)
    private String status;

    @Column(name = "requires_approval")
    private boolean requiresApproval = false;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "risk_level")
    private String riskLevel = "GREEN";

    @Column(name = "suspicion_reason", columnDefinition = "TEXT")
    private String suspicionReason;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    // ===== WOOCOMMERCE FIELDS =====
    @Column(name = "woo_order_id")
    private Long wooOrderId;

    @Column(name = "webhook_url", columnDefinition = "TEXT")
    private String webhookUrl;

    @Column(name = "return_url", columnDefinition = "TEXT")
    private String returnUrl;

    @Column(name = "order_key")
    private String orderKey;

    // ===== OPTIMISTIC LOCKING =====
    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    // Constructors
    public Invoice() {}

    public Invoice(BigDecimal amount, String description, String customerEmail, Long merchantId) {
        this.amount = amount;
        this.description = description;
        this.customerEmail = customerEmail;
        this.merchantId = merchantId;
        this.status = "DRAFT";
    }

    // Getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getCustomerEmail() { return customerEmail; }
    public void setCustomerEmail(String customerEmail) { this.customerEmail = customerEmail; }

    public Long getMerchantId() { return merchantId; }
    public void setMerchantId(Long merchantId) { this.merchantId = merchantId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public boolean isRequiresApproval() { return requiresApproval; }
    public void setRequiresApproval(boolean requiresApproval) { this.requiresApproval = requiresApproval; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public String getRiskLevel() { return riskLevel; }
    public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }

    public String getSuspicionReason() { return suspicionReason; }
    public void setSuspicionReason(String suspicionReason) { this.suspicionReason = suspicionReason; }

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }

    // WooCommerce getters/setters
    public Long getWooOrderId() { return wooOrderId; }
    public void setWooOrderId(Long wooOrderId) { this.wooOrderId = wooOrderId; }

    public String getWebhookUrl() { return webhookUrl; }
    public void setWebhookUrl(String webhookUrl) { this.webhookUrl = webhookUrl; }

    public String getReturnUrl() { return returnUrl; }
    public void setReturnUrl(String returnUrl) { this.returnUrl = returnUrl; }

    public String getOrderKey() { return orderKey; }
    public void setOrderKey(String orderKey) { this.orderKey = orderKey; }
}