package com.enterprise.refund;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "refund_rule")
public class RefundRule {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private int rulePriority;
    private String conditionExpression;
    private String action;
    private String requiredPermission;
    private boolean active = true;
    @Column(updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    // Getters & Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public int getRulePriority() { return rulePriority; }
    public void setRulePriority(int rulePriority) { this.rulePriority = rulePriority; }
    public String getConditionExpression() { return conditionExpression; }
    public void setConditionExpression(String conditionExpression) { this.conditionExpression = conditionExpression; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getRequiredPermission() { return requiredPermission; }
    public void setRequiredPermission(String requiredPermission) { this.requiredPermission = requiredPermission; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}