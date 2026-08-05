package com.enterprise.invoice;

import com.enterprise.audit.AuditService;
import com.enterprise.events.SuspicionEnabledEvent;
import com.enterprise.identity.AppUser;
import com.enterprise.identity.UserRepository;
import com.enterprise.notification.NotificationService;
import com.enterprise.tenant.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Service
public class InvoiceService {

    private static final Logger log = LoggerFactory.getLogger(InvoiceService.class);

    private final InvoiceRepository invoiceRepository;
    private final NotificationService notificationService;
    private final UserRepository userRepository;
    private final SuspicionService suspicionService;
    private final ApprovalService approvalService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    public InvoiceService(InvoiceRepository invoiceRepository,
                          NotificationService notificationService,
                          UserRepository userRepository,
                          SuspicionService suspicionService,
                          ApprovalService approvalService,
                          AuditService auditService,
                          ObjectMapper objectMapper,
                          RestTemplate restTemplate) {
        this.invoiceRepository = invoiceRepository;
        this.notificationService = notificationService;
        this.userRepository = userRepository;
        this.suspicionService = suspicionService;
        this.approvalService = approvalService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.restTemplate = restTemplate;
    }

    // ===== PRIMARY CREATE INVOICE =====
    @Transactional
    public Invoice createInvoice(BigDecimal amount, String description, String customerEmail,
                                 Long merchantId, boolean requiresApproval, String currency,
                                 Long wooOrderId, String webhookUrl, String returnUrl,
                                 String orderKey) {
        log.info("📝 Creating invoice for merchant {}: amount {}, description '{}', customer {}, currency {}",
                merchantId, amount, description, customerEmail, currency);

        AppUser merchant = userRepository.findById(merchantId)
                .orElseThrow(() -> {
                    log.error("❌ Merchant not found with id: {}", merchantId);
                    return new RuntimeException("Merchant not found");
                });
        if (!merchant.isActive()) {
            log.error("❌ Merchant account is disabled: {}", merchantId);
            throw new RuntimeException("Merchant account is disabled");
        }
        Long tenantId = TenantContext.getRequiredTenantId();

        Invoice invoice = new Invoice(amount, description, customerEmail, merchantId);
        invoice.setCurrency(currency != null && !currency.isEmpty() ? currency : "GBP");
        invoice.setTenantId(tenantId);

        invoice.setWooOrderId(wooOrderId);
        invoice.setWebhookUrl(webhookUrl);
        invoice.setReturnUrl(returnUrl);
        invoice.setOrderKey(orderKey);

        SuspicionService.SuspicionResult result = suspicionService.evaluate(invoice);
        invoice.setRiskLevel(result.getRiskLevel());
        invoice.setSuspicionReason(result.getReason());

        boolean approvalTrigger = approvalService.evaluate(invoice);
        boolean riskTrigger = !"GREEN".equals(result.getRiskLevel());
        boolean needsApproval = approvalTrigger || riskTrigger;

        invoice.setRequiresApproval(needsApproval);
        invoice.setStatus(needsApproval ? "PENDING_APPROVAL" : "APPROVED");

        invoice = invoiceRepository.save(invoice);

        Map<String, Object> afterMap = objectMapper.convertValue(invoice, Map.class);
        Map<String, Object> beforeMap = new HashMap<>();
        for (String key : afterMap.keySet()) {
            beforeMap.put(key, null);
        }

        auditService.recordEvent(
                "INVOICE_CREATED",
                merchantId,
                Map.of("invoiceId", invoice.getId(), "amount", amount, "customer", customerEmail, "currency", currency),
                "Invoice",
                invoice.getId(),
                beforeMap,
                afterMap
        );

        notificationService.createNotification(
                merchantId,
                "INVOICE_CREATED",
                "Invoice Created",
                String.format("Invoice #%d created for %.2f %s to %s", invoice.getId(), amount, invoice.getCurrency(), customerEmail),
                "/invoices/" + invoice.getId()
        );

        if (needsApproval) {
            List<AppUser> admins = userRepository.findByRolesName("ADMIN");
            String riskIcon = switch (result.getRiskLevel()) {
                case "RED" -> "🔴";
                case "YELLOW" -> "🟡";
                default -> "🟢";
            };
            for (AppUser admin : admins) {
                notificationService.createNotification(
                        admin.getId(),
                        "INVOICE_PENDING_APPROVAL",
                        "Invoice Requires Approval",
                        String.format("%s Invoice #%d for %.2f %s is pending approval (Risk: %s, Reason: %s)",
                                riskIcon, invoice.getId(), amount, invoice.getCurrency(),
                                result.getRiskLevel(), result.getReason()),
                        "/admin/invoices/pending"
                );
            }
        } else {
            Long customerId = getUserIdByEmail(customerEmail);
            if (customerId != null) {
                notificationService.createNotification(
                        customerId,
                        "INVOICE_READY",
                        "Invoice Ready for Payment",
                        String.format("Invoice #%d for %.2f %s is ready to pay", invoice.getId(), amount, invoice.getCurrency()),
                        "/invoices/" + invoice.getId()
                );
            }
        }

        log.info("✅ Invoice {} created successfully", invoice.getId());
        return invoice;
    }

    // ===== OVERLOADS =====
    @Transactional
    public Invoice createInvoice(BigDecimal amount, String description, String customerEmail,
                                 Long merchantId, boolean requiresApproval, String currency) {
        return createInvoice(amount, description, customerEmail, merchantId,
                requiresApproval, currency, null, null, null, null);
    }

    @Transactional
    public Invoice createInvoice(BigDecimal amount, String description, String customerEmail,
                                 Long merchantId, boolean requiresApproval, String currency,
                                 Long wooOrderId, String webhookUrl, String returnUrl) {
        return createInvoice(amount, description, customerEmail, merchantId,
                requiresApproval, currency, wooOrderId, webhookUrl, returnUrl, null);
    }

    // ===== ASYNC WEBHOOK METHODS =====
    @Async
    public void sendApprovalWebhookAsync(Invoice invoice) {
        sendApprovalWebhook(invoice);
    }

    @Async
    public void sendRejectionWebhookAsync(Invoice invoice, String reason) {
        sendRejectionWebhook(invoice, reason);
    }

    // ===== APPROVE INVOICE =====
    @Retryable(
        value = {OptimisticLockingFailureException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 100)
    )
    @Transactional
    public Invoice approveInvoice(Long invoiceId, Long adminId) {
        log.info("🔵 InvoiceService.approveInvoice() called - invoice: {}, admin: {}", invoiceId, adminId);

        Invoice invoice = invoiceRepository.findByIdWithLock(invoiceId)
                .orElseThrow(() -> {
                    log.error("❌ Invoice not found: {}", invoiceId);
                    return new RuntimeException("Invoice not found");
                });

        Map<String, Object> before = objectMapper.convertValue(invoice, Map.class);

        if (!"PENDING_APPROVAL".equals(invoice.getStatus())) {
            log.warn("⚠️ Invoice {} is not pending approval (status: {})", invoiceId, invoice.getStatus());
            throw new IllegalStateException("Invoice is not pending approval");
        }

        log.info("   Approving invoice {}", invoiceId);
        invoice.setStatus("APPROVED");
        invoice.setUpdatedAt(LocalDateTime.now());
        Invoice afterEntity = invoiceRepository.save(invoice);
        Map<String, Object> after = objectMapper.convertValue(afterEntity, Map.class);

        auditService.recordEvent(
                "INVOICE_APPROVED",
                adminId,
                Map.of("invoiceId", invoiceId),
                "Invoice",
                invoiceId,
                before,
                after
        );

        notificationService.createNotification(
                invoice.getMerchantId(),
                "INVOICE_APPROVED",
                "Invoice Approved",
                String.format("Invoice #%d approved by Admin", invoiceId),
                "/invoices/" + invoiceId
        );
        Long customerId = getUserIdByEmail(invoice.getCustomerEmail());
        if (customerId != null) {
            notificationService.createNotification(
                    customerId,
                    "INVOICE_APPROVED",
                    "Invoice Approved",
                    String.format("Invoice #%d has been approved and is ready for payment", invoiceId),
                    "/invoices/" + invoiceId
            );
        }

        // ===== SEND WEBHOOK ASYNCHRONOUSLY =====
        sendApprovalWebhookAsync(afterEntity);

        log.info("✅ Invoice {} approved successfully", invoiceId);
        return afterEntity;
    }

    // ===== REJECT INVOICE =====
    @Retryable(
        value = {OptimisticLockingFailureException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 100)
    )
    @Transactional
    public Invoice rejectInvoice(Long invoiceId, Long adminId, String reason) {
        log.info("🔴 InvoiceService.rejectInvoice() called - invoice: {}, admin: {}, reason: {}", invoiceId, adminId, reason);

        Invoice invoice = invoiceRepository.findByIdWithLock(invoiceId)
                .orElseThrow(() -> {
                    log.error("❌ Invoice not found: {}", invoiceId);
                    return new RuntimeException("Invoice not found");
                });

        Map<String, Object> before = objectMapper.convertValue(invoice, Map.class);

        if (!"PENDING_APPROVAL".equals(invoice.getStatus())) {
            log.warn("⚠️ Invoice {} is not pending approval (status: {})", invoiceId, invoice.getStatus());
            throw new IllegalStateException("Invoice is not pending approval");
        }

        log.info("   Rejecting invoice {}", invoiceId);
        invoice.setStatus("REJECTED");
        invoice.setUpdatedAt(LocalDateTime.now());
        Invoice afterEntity = invoiceRepository.save(invoice);
        Map<String, Object> after = objectMapper.convertValue(afterEntity, Map.class);

        auditService.recordEvent(
                "INVOICE_REJECTED",
                adminId,
                Map.of("invoiceId", invoiceId, "reason", reason),
                "Invoice",
                invoiceId,
                before,
                after
        );

        notificationService.createNotification(
                invoice.getMerchantId(),
                "INVOICE_REJECTED",
                "Invoice Rejected",
                String.format("Invoice #%d was rejected by Admin. Reason: %s", invoiceId, reason != null ? reason : "No reason provided"),
                "/invoices/" + invoiceId
        );
        Long customerId = getUserIdByEmail(invoice.getCustomerEmail());
        if (customerId != null) {
            notificationService.createNotification(
                    customerId,
                    "INVOICE_REJECTED",
                    "Invoice Rejected",
                    String.format("Invoice #%d was rejected. Reason: %s", invoiceId, reason != null ? reason : "No reason provided"),
                    "/invoices/" + invoiceId
            );
        }

        // ===== SEND WEBHOOK ASYNCHRONOUSLY =====
        sendRejectionWebhookAsync(afterEntity, reason);

        log.info("✅ Invoice {} rejected successfully", invoiceId);
        return afterEntity;
    }

    // ===== OVERLOAD for backward compatibility =====
    @Transactional
    public Invoice rejectInvoice(Long invoiceId, Long adminId) {
        return rejectInvoice(invoiceId, adminId, null);
    }

    // ===== WEBHOOK SENDING METHODS =====
    private void sendApprovalWebhook(Invoice invoice) {
        if (invoice.getWebhookUrl() == null || invoice.getWebhookUrl().isEmpty()) {
            log.debug("No webhook URL for invoice {}", invoice.getId());
            return;
        }

        String paymentUrl = null;
        if (invoice.getReturnUrl() != null && invoice.getWooOrderId() != null) {
            String orderKey = invoice.getOrderKey() != null ? invoice.getOrderKey() : "wc_order_" + invoice.getWooOrderId();
            paymentUrl = invoice.getReturnUrl() + "/" + invoice.getWooOrderId() + "/?key=" + orderKey;
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("event", "INVOICE_APPROVED");
        payload.put("orderId", invoice.getWooOrderId());
        payload.put("invoiceId", invoice.getId());
        payload.put("paymentUrl", paymentUrl);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);

        try {
            restTemplate.postForEntity(invoice.getWebhookUrl(), entity, String.class);
            log.info("✅ Approval webhook sent for invoice {}", invoice.getId());
        } catch (Exception e) {
            log.error("❌ Failed to send approval webhook for invoice {}: {}", invoice.getId(), e.getMessage());
        }
    }

    private void sendRejectionWebhook(Invoice invoice, String reason) {
        if (invoice.getWebhookUrl() == null || invoice.getWebhookUrl().isEmpty()) {
            log.debug("No webhook URL for invoice {}", invoice.getId());
            return;
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("event", "INVOICE_REJECTED");
        payload.put("orderId", invoice.getWooOrderId());
        payload.put("invoiceId", invoice.getId());
        payload.put("reason", reason != null ? reason : "Order rejected by merchant");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);

        try {
            restTemplate.postForEntity(invoice.getWebhookUrl(), entity, String.class);
            log.info("✅ Rejection webhook sent for invoice {}", invoice.getId());
        } catch (Exception e) {
            log.error("❌ Failed to send rejection webhook for invoice {}: {}", invoice.getId(), e.getMessage());
        }
    }

    // ===== BULK OPERATIONS =====
    public int bulkApproveInvoices(List<Long> invoiceIds, Long adminId) {
        log.info("🟢 bulkApproveInvoices() called with {} invoices by admin {}", invoiceIds.size(), adminId);
        int approvedCount = 0;
        for (Long id : invoiceIds) {
            try {
                log.info("   Approving invoice {} via bulk", id);
                approveInvoice(id, adminId);
                approvedCount++;
            } catch (Exception e) {
                log.error("❌ Failed to approve invoice {}: {}", id, e.getMessage());
            }
        }
        log.info("✅ Bulk approve completed: {} out of {} approved", approvedCount, invoiceIds.size());
        return approvedCount;
    }

    public int bulkRejectInvoices(List<Long> invoiceIds, Long adminId) {
        log.info("🔴 bulkRejectInvoices() called with {} invoices by admin {}", invoiceIds.size(), adminId);
        int rejectedCount = 0;
        for (Long id : invoiceIds) {
            try {
                log.info("   Rejecting invoice {} via bulk", id);
                rejectInvoice(id, adminId);
                rejectedCount++;
            } catch (Exception e) {
                log.error("❌ Failed to reject invoice {}: {}", id, e.getMessage());
            }
        }
        log.info("✅ Bulk reject completed: {} out of {} rejected", rejectedCount, invoiceIds.size());
        return rejectedCount;
    }

    // ===== MARK AS PAID =====
    @Transactional
    public void markAsPaid(Long invoiceId, Long transactionId) {
        log.info("💳 Marking invoice {} as paid with transaction {}", invoiceId, transactionId);
        Invoice beforeEntity = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new RuntimeException("Invoice not found"));
        Map<String, Object> before = objectMapper.convertValue(beforeEntity, Map.class);

        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new RuntimeException("Invoice not found"));
        if (!"APPROVED".equals(invoice.getStatus())) {
            log.warn("⚠️ Invoice {} is not approved (status: {})", invoiceId, invoice.getStatus());
            throw new IllegalStateException("Invoice is not approved");
        }
        invoice.setStatus("PAID");
        invoice.setUpdatedAt(LocalDateTime.now());
        Invoice afterEntity = invoiceRepository.save(invoice);
        Map<String, Object> after = objectMapper.convertValue(afterEntity, Map.class);

        auditService.recordEvent(
                "INVOICE_PAID",
                invoice.getMerchantId(),
                Map.of("invoiceId", invoiceId, "transactionId", transactionId),
                "Invoice",
                invoiceId,
                before,
                after
        );

        notificationService.createNotification(
                invoice.getMerchantId(),
                "INVOICE_PAID",
                "Invoice Paid",
                String.format("Invoice #%d paid (Transaction #%d)", invoiceId, transactionId),
                "/transactions/" + transactionId
        );
        Long customerId = getUserIdByEmail(invoice.getCustomerEmail());
        if (customerId != null) {
            notificationService.createNotification(
                    customerId,
                    "INVOICE_PAID",
                    "Invoice Paid",
                    String.format("Invoice #%d was paid (Transaction #%d)", invoiceId, transactionId),
                    "/transactions/" + transactionId
            );
        }
    }

    public boolean isInvoicePaid(Long invoiceId) {
        return invoiceRepository.findById(invoiceId)
                .map(inv -> "PAID".equals(inv.getStatus()))
                .orElse(false);
    }

    // ===== RE-EVALUATION =====
    @Transactional
    public void reEvaluateAllInvoices() {
        log.info("🔄 Re-evaluating all non-paid invoices for approval rules");
        List<Invoice> invoices = invoiceRepository.findByStatusIn(List.of("APPROVED", "PENDING_APPROVAL"));
        int updated = 0;
        for (Invoice invoice : invoices) {
            if ("PAID".equals(invoice.getStatus()) || "REJECTED".equals(invoice.getStatus())) {
                continue;
            }
            String risk = Objects.requireNonNullElse(invoice.getRiskLevel(), "GREEN");
            boolean isSuspicious = !"GREEN".equals(risk);

            boolean needsApproval;
            if (isSuspicious) {
                needsApproval = true;
            } else {
                needsApproval = approvalService.evaluate(invoice);
            }

            invoice.setRequiresApproval(needsApproval);
            String newStatus = needsApproval ? "PENDING_APPROVAL" : "APPROVED";
            if (!newStatus.equals(invoice.getStatus())) {
                invoice.setStatus(newStatus);
                invoice.setUpdatedAt(LocalDateTime.now());
                updated++;
                try {
                    if (needsApproval) {
                        notificationService.createNotification(
                                invoice.getMerchantId(),
                                "INVOICE_MOVED_TO_PENDING",
                                "Invoice Moved to Pending",
                                "Invoice #" + invoice.getId() + " now requires approval after rule update.",
                                "/invoices/" + invoice.getId()
                        );
                    } else {
                        notificationService.createNotification(
                                invoice.getMerchantId(),
                                "INVOICE_AUTO_APPROVED",
                                "Invoice Auto-Approved",
                                "Invoice #" + invoice.getId() + " was automatically approved after rule update.",
                                "/invoices/" + invoice.getId()
                        );
                    }
                } catch (Exception e) {
                    log.warn("⚠️ Notification failed during re-evaluation for invoice {}: {}", invoice.getId(), e.getMessage());
                }
            }
        }
        if (updated > 0) {
            invoiceRepository.saveAll(invoices);
        }
        log.info("✅ Re-evaluation completed: {} invoices updated", updated);
    }

    @Transactional
    public void reEvaluateAllInvoicesForSuspicion() {
        log.info("🔄 Re-evaluating all invoices for suspicion");
        List<Invoice> allInvoices = invoiceRepository.findAll();
        int updated = 0;
        for (Invoice invoice : allInvoices) {
            if ("PAID".equals(invoice.getStatus()) || "REJECTED".equals(invoice.getStatus())) {
                continue;
            }
            SuspicionService.SuspicionResult result = suspicionService.evaluate(invoice);
            String newRisk = result.getRiskLevel();
            if (!newRisk.equals(invoice.getRiskLevel())) {
                invoice.setRiskLevel(newRisk);
                invoice.setSuspicionReason(result.getReason());
                invoice.setUpdatedAt(LocalDateTime.now());
                invoiceRepository.save(invoice);
                updated++;
            }
        }
        log.info("✅ Re-evaluation for suspicion completed: {} invoices risk levels changed", updated);
    }

    @EventListener
    @Transactional
    public void onSuspicionEnabled(SuspicionEnabledEvent event) {
        log.info("📢 Received SuspicionEnabledEvent – re-evaluating all invoices for suspicion");
        reEvaluateAllInvoicesForSuspicion();
    }

    // ===== UPDATE =====
    @Transactional
    public void updateInvoice(Invoice invoice) {
        Invoice existing = invoiceRepository.findById(invoice.getId())
                .orElseThrow(() -> new RuntimeException("Invoice not found"));
        existing.setDescription(invoice.getDescription());
        existing.setCurrency(invoice.getCurrency());
        existing.setUpdatedAt(LocalDateTime.now());
        invoiceRepository.save(existing);
    }

    // ===== HELPERS =====
    private Long getUserIdByEmail(String email) {
        return userRepository.findByEmail(email)
                .map(AppUser::getId)
                .orElse(null);
    }

    // ===== FINDERS =====
    public Optional<Invoice> findById(Long id) {
        return invoiceRepository.findById(id);
    }

    public List<Invoice> findAll() {
        return invoiceRepository.findAll();
    }

    public List<Invoice> getInvoicesForMerchant(Long merchantId) {
        return invoiceRepository.findByMerchantIdOrderByCreatedAtDesc(merchantId);
    }

    public List<Invoice> getInvoicesForCustomer(String email) {
        return invoiceRepository.findByCustomerEmailOrderByCreatedAtDesc(email);
    }

    public List<Invoice> getPendingApprovalInvoices() {
        return invoiceRepository.findByStatusAndRequiresApproval("PENDING_APPROVAL", true);
    }
}