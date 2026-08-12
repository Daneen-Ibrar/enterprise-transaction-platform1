package com.enterprise.audit;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import org.hibernate.annotations.Filter;

@Entity
@Table(name = "audit_event")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String details;

    @Column(name = "previous_hash", nullable = false, length = 64)
    private String previousHash;

    @Column(name = "current_hash", nullable = false, length = 64)
    private String currentHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "entity_type", length = 50)
    private String entityType;

    @Column(name = "entity_id")
    private Long entityId;

    @Column(name = "previous_state", columnDefinition = "TEXT")
    private String previousState;

    @Column(name = "current_state", columnDefinition = "TEXT")
    private String currentState;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    // Constructors
    public AuditEvent() {}

    public AuditEvent(String eventType, Long userId, String details, String previousHash, String currentHash) {
        this.eventType = eventType;
        this.userId = userId;
        this.details = details;
        this.previousHash = previousHash;
        this.currentHash = currentHash;
    }

    // Getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }

    public String getPreviousHash() { return previousHash; }
    public void setPreviousHash(String previousHash) { this.previousHash = previousHash; }

    public String getCurrentHash() { return currentHash; }
    public void setCurrentHash(String currentHash) { this.currentHash = currentHash; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public String getEntityType() { return entityType; }
    public void setEntityType(String entityType) { this.entityType = entityType; }

    public Long getEntityId() { return entityId; }
    public void setEntityId(Long entityId) { this.entityId = entityId; }

    public String getPreviousState() { return previousState; }
    public void setPreviousState(String previousState) { this.previousState = previousState; }

    public String getCurrentState() { return currentState; }
    public void setCurrentState(String currentState) { this.currentState = currentState; }

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}