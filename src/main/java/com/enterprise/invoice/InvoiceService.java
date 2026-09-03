package com.enterprise.invoice;

import com.enterprise.audit.AuditService;
import com.enterprise.events.SuspicionEnabledEvent;
import com.enterprise.feature.FeatureFlagService;
import com.enterprise.identity.AppUser;
import com.enterprise.identity.UserRepository;
import com.enterprise.notification.NotificationService;
import com.enterprise.tax.TaxService;
import com.enterprise.tenant.TenantContext;
import com.enterprise.xero.service.XeroInvoiceService;
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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
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
    private final FeatureFlagService featureFlagService;
    private final XeroInvoiceService xeroInvoiceService;
    private final TaxService taxService;  // ✅ ADDED

    public InvoiceService(InvoiceRepository invoiceRepository,
                          NotificationService notificationService,
                          UserRepository userRepository,
                          SuspicionService suspicionService,
                          ApprovalService approvalService,
                          AuditService auditService,
                          ObjectMapper objectMapper,
                          RestTemplate restTemplate,
                          FeatureFlagService featureFlagService,
                          XeroInvoiceService xeroInvoiceService,
                          TaxService taxService) {  // ✅ ADDED
        this.invoiceRepository = invoiceRepository;
        this.notificationService = notificationService;
        this.userRepository = userRepository;
        this.suspicionService = suspicionService;
        this.approvalService = approvalService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.restTemplate = restTemplate;
        this.featureFlagService = featureFlagService;
        this.xeroInvoiceService = xeroInvoiceService;
        this.taxService = taxService;  // ✅ ADDED
    }

    // ===== PRIMARY CREATE INVOICE =====
    @Transactional(noRollbackFor = Exception.class)
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

        // ============================================================
        // ✅ TAX CALCULATION - USING VATLAYER API
        // ============================================================
        String customerCountry = getCustomerCountry(customerEmail);
        boolean isB2B = false; // Can be determined from customer profile

        if (customerCountry != null && !customerCountry.isEmpty()) {
            try {
                TaxService.TaxCalculation taxCalc = taxService.calculateTax(amount, customerCountry, isB2B);
                invoice.setTaxAmount(taxCalc.getTaxAmount());
                invoice.setTaxRate(taxCalc.getTaxRate());
                invoice.setTaxName(taxCalc.getTaxName());
                invoice.setTotalWithTax(taxCalc.getTotalWithTax());
                invoice.setCustomerCountry(customerCountry);
                invoice.setB2B(isB2B);
                log.info("✅ Tax calculated for invoice: {}% {} = {} (total: {})",
                        taxCalc.getTaxRate(), taxCalc.getTaxName(),
                        taxCalc.getTaxAmount(), taxCalc.getTotalWithTax());
            } catch (Exception e) {
                log.warn("⚠️ Tax calculation failed: {}", e.getMessage());
                invoice.setTotalWithTax(amount);
                invoice.setTaxAmount(BigDecimal.ZERO);
            }
        } else {
            invoice.setTotalWithTax(amount);
            invoice.setTaxAmount(BigDecimal.ZERO);
        }

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
                Map.of("invoiceId", invoice.getId(), "amount", amount, "customer", customerEmail, "currency", currency,
                       "taxAmount", invoice.getTaxAmount(), "totalWithTax", invoice.getTotalWithTax()),
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

        // Sync to Xero (only if no approval required)
        if (!needsApproval) {
            syncToXeroIfConnected(invoice);
        } else {
            log.info("⏳ Invoice {} requires approval - will sync when approved", invoice.getId());
        }

        log.info("✅ Invoice {} created successfully", invoice.getId());
        return invoice;
    }

    // ============================================================
    // ✅ HELPER: Detect Country from Email
    // ============================================================
    private String getCustomerCountry(String email) {
        if (email == null || !email.contains("@")) {
            return null;
        }
        String domain = email.substring(email.indexOf("@") + 1).toLowerCase();

        // Map common email domains/TLDs to countries
        if (domain.endsWith(".co.uk") || domain.endsWith(".ac.uk") || domain.endsWith(".gov.uk")) {
            return "GB";
        } else if (domain.endsWith(".de")) {
            return "DE";
        } else if (domain.endsWith(".fr")) {
            return "FR";
        } else if (domain.endsWith(".es")) {
            return "ES";
        } else if (domain.endsWith(".it")) {
            return "IT";
        } else if (domain.endsWith(".nl")) {
            return "NL";
        } else if (domain.endsWith(".be")) {
            return "BE";
        } else if (domain.endsWith(".pl")) {
            return "PL";
        } else if (domain.endsWith(".pt")) {
            return "PT";
        } else if (domain.endsWith(".ie")) {
            return "IE";
        } else if (domain.endsWith(".at")) {
            return "AT";
        } else if (domain.endsWith(".se")) {
            return "SE";
        } else if (domain.endsWith(".fi")) {
            return "FI";
        } else if (domain.endsWith(".dk")) {
            return "DK";
        } else if (domain.endsWith(".no")) {
            return "NO";
        } else if (domain.endsWith(".ch")) {
            return "CH";
        } else if (domain.endsWith(".com") || domain.endsWith(".org") || domain.endsWith(".net")) {
            return null; // Unknown - could be US or international
        } else {
            // Try to detect from TLD (if it's a 2-letter country code)
            String[] parts = domain.split("\\.");
            if (parts.length > 0) {
                String lastPart = parts[parts.length - 1];
                if (lastPart.length() == 2) {
                    return lastPart.toUpperCase();
                }
            }
        }
        return null;
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

    // ===== SYNC INVOICE TO XERO =====
    @Transactional(noRollbackFor = Exception.class)
    private void syncToXeroIfConnected(Invoice invoice) {
        try {
            Long tenantId = invoice.getTenantId();
            if (tenantId != null && xeroInvoiceService != null) {
                if (!"APPROVED".equals(invoice.getStatus())) {
                    log.debug("⏳ Invoice {} is not approved yet (status: {}), skipping Xero sync",
                            invoice.getId(), invoice.getStatus());
                    return;
                }

                if (invoice.getXeroInvoiceId() != null && !invoice.getXeroInvoiceId().isEmpty()) {
                    log.debug("⏭️ Invoice {} already synced to Xero with ID: {}",
                            invoice.getId(), invoice.getXeroInvoiceId());
                    return;
                }

                String xeroId = xeroInvoiceService.syncInvoiceToXero(invoice);
                if (xeroId != null) {
                    log.info("✅ Invoice {} synced to Xero with ID: {}", invoice.getId(), xeroId);
                }
            }
        } catch (Exception e) {
            log.warn("⚠️ Failed to sync invoice {} to Xero: {}", invoice.getId(), e.getMessage());
        }
    }

    // ===== APPROVE INVOICE =====
    @Retryable(
        value = {OptimisticLockingFailureException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 100)
    )
    @Transactional(noRollbackFor = Exception.class)
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
        invoice.setRequiresApproval(false);
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

        try {
            syncToXeroIfConnected(afterEntity);
        } catch (Exception e) {
            log.warn("⚠️ Xero sync failed for invoice {}: {}", invoiceId, e.getMessage());
        }

        try {
            sendApprovalWebhookAsync(afterEntity);
        } catch (Exception e) {
            log.warn("⚠️ Webhook failed for invoice {}: {}", invoiceId, e.getMessage());
        }

        log.info("✅ Invoice {} approved", invoiceId);
        return afterEntity;
    }

    // ===== REJECT INVOICE =====
    @Retryable(
        value = {OptimisticLockingFailureException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 100)
    )
    @Transactional(noRollbackFor = Exception.class)
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

        try {
            sendRejectionWebhookAsync(afterEntity, reason);
        } catch (Exception e) {
            log.warn("⚠️ Webhook failed for invoice {}: {}", invoiceId, e.getMessage());
        }

        log.info("✅ Invoice {} rejected successfully", invoiceId);
        return afterEntity;
    }

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
    @Transactional(noRollbackFor = Exception.class)
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

        try {
            String paymentId = xeroInvoiceService.syncPaymentToXero(invoice, transactionId);
            if (paymentId != null) {
                log.info("✅ Payment {} synced to Xero for invoice {}", paymentId, invoiceId);
            } else {
                log.warn("⚠️ Payment sync to Xero failed for invoice {}", invoiceId);
            }
        } catch (Exception e) {
            log.warn("⚠️ Failed to sync payment to Xero for invoice {}: {}", invoiceId, e.getMessage());
        }
    }

    public boolean isInvoicePaid(Long invoiceId) {
        return invoiceRepository.findById(invoiceId)
                .map(inv -> "PAID".equals(inv.getStatus()))
                .orElse(false);
    }

    // ===== RE-EVALUATION =====
    @Transactional
    public void reEvaluateAllInvoicesForApproval() {
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
    public void reEvaluateAllInvoices() {
        reEvaluateAllInvoicesForSuspicion();
    }

    @Transactional
    public void reEvaluateAllInvoicesForSuspicion() {
        if (!featureFlagService.isEnabled("SUSPICION_DETECTION")) {
            log.info("Suspicion detection is disabled – skipping re-evaluation");
            return;
        }

        log.info("🔄 Re-evaluating all invoices for suspicion");

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean isSuperAdmin = false;
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            isSuperAdmin = auth.getAuthorities().stream()
                    .anyMatch(grantedAuthority -> grantedAuthority.getAuthority().equals("ROLE_SUPER_ADMIN"));
        }

        List<Invoice> allInvoices;
        if (isSuperAdmin) {
            allInvoices = invoiceRepository.findAllInvoicesForReevaluation();
            log.info("Super Admin re-evaluating {} invoices across all tenants", allInvoices.size());
        } else {
            Long tenantId = TenantContext.getRequiredTenantId();
            allInvoices = invoiceRepository.findAllByTenantIdForReevaluation(tenantId);
            log.info("User re-evaluating {} invoices for tenant {}", allInvoices.size(), tenantId);
        }

        int updated = 0;
        int cleared = 0;
        int movedToSuspicious = 0;

        for (Invoice invoice : allInvoices) {
            if ("PAID".equals(invoice.getStatus()) || "REJECTED".equals(invoice.getStatus())) {
                continue;
            }

            SuspicionService.SuspicionResult result = suspicionService.evaluate(invoice);
            String newRisk = result.getRiskLevel();
            String newReason = result.getReason();

            String oldRisk = invoice.getRiskLevel();

            if (!newRisk.equals(oldRisk) || !newReason.equals(invoice.getSuspicionReason())) {
                invoiceRepository.updateRiskLevel(invoice.getId(), newRisk, newReason);

                if ("GREEN".equals(newRisk)) {
                    if (!approvalService.evaluate(invoice)) {
                        invoice.setRequiresApproval(false);
                        invoice.setStatus("APPROVED");
                        invoiceRepository.save(invoice);
                        cleared++;
                        log.info("✅ Cleared suspicion for invoice {}: {} -> {}", invoice.getId(), oldRisk, newRisk);
                    } else {
                        invoice.setStatus("PENDING_APPROVAL");
                        invoiceRepository.save(invoice);
                        log.info("✅ Cleared suspicion for invoice {}: {} -> {}, but needs approval", invoice.getId(), oldRisk, newRisk);
                    }
                } else {
                    invoice.setRequiresApproval(true);
                    invoice.setStatus("PENDING_APPROVAL");
                    invoiceRepository.save(invoice);
                    movedToSuspicious++;
                    log.info("🚨 Invoice {} marked as suspicious: {} -> {}", invoice.getId(), oldRisk, newRisk);
                }
                updated++;
            }
        }

        log.info("✅ Re-evaluation for suspicion completed: {} updated, {} cleared, {} moved to suspicious",
                updated, cleared, movedToSuspicious);
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