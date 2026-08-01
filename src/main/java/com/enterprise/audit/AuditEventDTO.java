package com.enterprise.audit;

import java.time.LocalDateTime;

public class AuditEventDTO {
    private Long id;
    private String eventType;
    private Long userId;
    private String details;
    private String previousHash;
    private String currentHash;
    private LocalDateTime createdAt;

    public AuditEventDTO(AuditEvent event) {
        this.id = event.getId();
        this.eventType = event.getEventType();
        this.userId = event.getUserId();
        this.details = event.getDetails();
        this.previousHash = event.getPreviousHash();
        this.currentHash = event.getCurrentHash();
        this.createdAt = event.getCreatedAt();
    }

    // Getters
    public Long getId() { return id; }
    public String getEventType() { return eventType; }
    public Long getUserId() { return userId; }
    public String getDetails() { return details; }
    public String getPreviousHash() { return previousHash; }
    public String getCurrentHash() { return currentHash; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}