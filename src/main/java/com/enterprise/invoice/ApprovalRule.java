package com.enterprise.invoice;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "approval_rule")
public class ApprovalRule {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "rule_priority", nullable = false)
    private int priority;

    @Column(name = "condition_expression", nullable = false, columnDefinition = "TEXT")
    private String conditionExpression;

    @Column(name = "requires_approval", nullable = false)
    private boolean requiresApproval = true;

    private String description;

    private boolean active = true;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    // ----- TENANT ID -----
    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public int getPriority() { return priority; }
    public void setPriority(int priority) { this.priority = priority; }
    public String getConditionExpression() { return conditionExpression; }
    public void setConditionExpression(String conditionExpression) { this.conditionExpression = conditionExpression; }
    public boolean isRequiresApproval() { return requiresApproval; }
    public void setRequiresApproval(boolean requiresApproval) { this.requiresApproval = requiresApproval; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
}