package com.enterprise.subscription;

import jakarta.persistence.*;
import org.hibernate.annotations.Filter;

import java.time.LocalDateTime;

@Entity
@Table(name = "subscription_request")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public class SubscriptionRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "customer_email", nullable = false)
    private String customerEmail;

    @Column(name = "plan_id", nullable = false)
    private Long planId;

    @Column(name = "plan_name", nullable = false)
    private String planName;

    @Column(name = "merchant_id", nullable = false)
    private Long merchantId;

    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    private RequestStatus status = RequestStatus.PENDING;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "assigned_by")
    private Long assignedBy;

    @Column(name = "assigned_at")
    private LocalDateTime assignedAt;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    public enum RequestStatus {
        PENDING, APPROVED, REJECTED, ASSIGNED
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }

    public String getCustomerEmail() { return customerEmail; }
    public void setCustomerEmail(String customerEmail) { this.customerEmail = customerEmail; }

    public Long getPlanId() { return planId; }
    public void setPlanId(Long planId) { this.planId = planId; }

    public String getPlanName() { return planName; }
    public void setPlanName(String planName) { this.planName = planName; }

    public Long getMerchantId() { return merchantId; }
    public void setMerchantId(Long merchantId) { this.merchantId = merchantId; }

    public RequestStatus getStatus() { return status; }
    public void setStatus(RequestStatus status) { this.status = status; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public Long getAssignedBy() { return assignedBy; }
    public void setAssignedBy(Long assignedBy) { this.assignedBy = assignedBy; }

    public LocalDateTime getAssignedAt() { return assignedAt; }
    public void setAssignedAt(LocalDateTime assignedAt) { this.assignedAt = assignedAt; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}