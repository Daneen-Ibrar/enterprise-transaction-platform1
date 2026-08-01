package com.enterprise.audit;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "rule_audit")
public class RuleAudit {
@Transient
private String changedByEmail;

public String getChangedByEmail() { return changedByEmail; }
public void setChangedByEmail(String changedByEmail) { this.changedByEmail = changedByEmail; }
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "rule_type", nullable = false)
    private String ruleType; // APPROVAL, REFUND, SUSPICION

    @Column(name = "rule_id", nullable = false)
    private Long ruleId;

    @Column(name = "action", nullable = false)
    private String action; // CREATE, UPDATE, DELETE, TOGGLE

    @Column(name = "old_values", columnDefinition = "TEXT")
    private String oldValues;

    @Column(name = "new_values", columnDefinition = "TEXT")
    private String newValues;

    @Column(name = "changed_by", nullable = false)
    private Long changedBy;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    // Getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getRuleType() { return ruleType; }
    public void setRuleType(String ruleType) { this.ruleType = ruleType; }

    public Long getRuleId() { return ruleId; }
    public void setRuleId(Long ruleId) { this.ruleId = ruleId; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getOldValues() { return oldValues; }
    public void setOldValues(String oldValues) { this.oldValues = oldValues; }

    public String getNewValues() { return newValues; }
    public void setNewValues(String newValues) { this.newValues = newValues; }

    public Long getChangedBy() { return changedBy; }
    public void setChangedBy(Long changedBy) { this.changedBy = changedBy; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}