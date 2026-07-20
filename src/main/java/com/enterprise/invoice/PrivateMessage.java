package com.enterprise.invoice;

import com.enterprise.identity.AppUser;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import org.hibernate.annotations.Filter;

@Entity
@Table(name = "private_message")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public class PrivateMessage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "invoice_id", nullable = false)
    private Invoice invoice;

    @ManyToOne
    @JoinColumn(name = "sender_id", nullable = false)
    private AppUser sender;

    @ManyToOne
    @JoinColumn(name = "recipient_id", nullable = false)
    private AppUser recipient;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    // ----- TENANT ID -----
    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    public PrivateMessage() {}

    public PrivateMessage(Invoice invoice, AppUser sender, AppUser recipient, String message) {
        this.invoice = invoice;
        this.sender = sender;
        this.recipient = recipient;
        this.message = message;
    }

    // Getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Invoice getInvoice() { return invoice; }
    public void setInvoice(Invoice invoice) { this.invoice = invoice; }
    public AppUser getSender() { return sender; }
    public void setSender(AppUser sender) { this.sender = sender; }
    public AppUser getRecipient() { return recipient; }
    public void setRecipient(AppUser recipient) { this.recipient = recipient; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
}