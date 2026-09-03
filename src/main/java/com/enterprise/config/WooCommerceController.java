package com.enterprise.api;

import com.enterprise.identity.AppUser;
import com.enterprise.identity.Role;
import com.enterprise.identity.RoleRepository;
import com.enterprise.identity.UserRepository;
import com.enterprise.invoice.Invoice;
import com.enterprise.invoice.InvoiceService;
import com.enterprise.tenant.TenantContext;
import com.enterprise.transaction.PaymentRequest;
import com.enterprise.transaction.PaymentResponse;
import com.enterprise.transaction.TransactionService;
import com.enterprise.webhook.WebhookService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;  // ✅ ADD THIS IMPORT
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/woocommerce")
public class WooCommerceController {

    private static final Logger log = LoggerFactory.getLogger(WooCommerceController.class);

    private final TransactionService transactionService;
    private final InvoiceService invoiceService;
    private final WebhookService webhookService;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public WooCommerceController(TransactionService transactionService,
                                 InvoiceService invoiceService,
                                 WebhookService webhookService,
                                 UserRepository userRepository,
                                 RoleRepository roleRepository,
                                 PasswordEncoder passwordEncoder) {
        this.transactionService = transactionService;
        this.invoiceService = invoiceService;
        this.webhookService = webhookService;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @PostMapping("/create-invoice")
    public ResponseEntity<Map<String, Object>> createInvoice(@RequestBody WooCommerceOrder order) {
        Long tenantId = order.getTenantId() != null ? order.getTenantId() : 1L;
        TenantContext.setTenantId(tenantId);
        log.info("🔵 Tenant set to: {}", tenantId);

        try {
            // ✅ Get or create customer
            AppUser customer = getOrCreateCustomer(order, tenantId);

            Invoice invoice = invoiceService.createInvoice(
                    order.getTotal(),
                    "WooCommerce Order #" + order.getOrderId(),
                    order.getBillingEmail(),
                    order.getMerchantId(),
                    false,
                    order.getCurrency(),
                    order.getOrderId(),
                    order.getWebhookUrl(),
                    order.getReturnUrl(),
                    order.getOrderKey()
            );

            Map<String, Object> response = new HashMap<>();
            response.put("invoiceId", invoice.getId());
            response.put("status", invoice.getStatus());
            response.put("customerId", customer.getId());
            response.put("customerEmail", customer.getEmail());
            return ResponseEntity.ok(response);
        } finally {
            TenantContext.clear();
        }
    }

    @PostMapping("/payment")
    public ResponseEntity<WooCommerceResponse> processWooOrder(@RequestBody WooCommerceOrder order) {
        Long tenantId = order.getTenantId() != null ? order.getTenantId() : 1L;
        TenantContext.setTenantId(tenantId);

        try {
            // ✅ Get or create customer - handles duplicate emails
            AppUser customer = getOrCreateCustomer(order, tenantId);

            Invoice invoice = invoiceService.createInvoice(
                    order.getTotal(),
                    "WooCommerce Order #" + order.getOrderId(),
                    order.getBillingEmail(),
                    order.getMerchantId(),
                    false,
                    order.getCurrency(),
                    null, null, null, null
            );

            PaymentRequest request = new PaymentRequest();
            request.setInvoiceId(invoice.getId());
            request.setCustomerId(customer.getId());
            request.setMerchantId(order.getMerchantId());
            request.setAmount(order.getTotal());
            request.setCurrency(order.getCurrency());
            request.setDescription("WooCommerce order " + order.getOrderId());

            String idempotencyKey = "woo-" + order.getOrderId() + "-" + order.getOrderKey();
            PaymentResponse response = transactionService.processPayment(request, idempotencyKey);

            webhookService.sendWebhooks("WOOCOMMERCE_ORDER_UPDATE", Map.of(
                    "orderId", order.getOrderId(),
                    "status", response.getStatus(),
                    "transactionId", response.getTransactionId(),
                    "customerId", customer.getId(),
                    "customerEmail", customer.getEmail()
            ));

            WooCommerceResponse wooResponse = new WooCommerceResponse(
                    order.getOrderId(),
                    response.getStatus(),
                    response.getTransactionId(),
                    "Payment " + response.getStatus().toLowerCase()
            );
            return ResponseEntity.ok(wooResponse);

        } finally {
            TenantContext.clear();
        }
    }

    // ============================================================
    // ✅ HELPER: Get or Create Customer (Handles Duplicate Emails)
    // ============================================================
    private AppUser getOrCreateCustomer(WooCommerceOrder order, Long tenantId) {
        String email = order.getBillingEmail();
        
        if (email == null || email.trim().isEmpty()) {
            log.error("❌ No billing email provided in WooCommerce order");
            throw new RuntimeException("Billing email is required");
        }

        // ✅ 1. Try to find existing user by email (case-insensitive)
        Optional<AppUser> existingUser = userRepository.findByEmailIgnoreCase(email);
        
        if (existingUser.isPresent()) {
            AppUser user = existingUser.get();
            log.info("✅ Found existing customer: {} (ID: {})", email, user.getId());
            
            // ✅ Check if user is active
            if (!user.isActive()) {
                log.warn("⚠️ User {} is inactive, reactivating", email);
                user.setActive(true);
                user = userRepository.save(user);
            }
            
            // ✅ Ensure user has CUSTOMER role
            boolean hasCustomerRole = user.getRoles().stream()
                    .anyMatch(r -> r.getName().equals("CUSTOMER"));
            if (!hasCustomerRole) {
                log.info("✅ Adding CUSTOMER role to user {}", email);
                Role customerRole = roleRepository.findByName("CUSTOMER")
                        .orElseThrow(() -> new RuntimeException("CUSTOMER role not found"));
                user.getRoles().add(customerRole);
                user = userRepository.save(user);
            }
            
            return user;
        }

        // ✅ 2. No user found - create new one
        log.info("📝 Creating new customer for email: {}", email);
        
        AppUser newUser = new AppUser();
        newUser.setEmail(email);
        
        // ✅ 3. Generate password if not provided
        String password = order.getCustomerPassword() != null && !order.getCustomerPassword().isEmpty()
                ? order.getCustomerPassword()
                : UUID.randomUUID().toString();
        newUser.setPasswordHash(passwordEncoder.encode(password));
        
        newUser.setActive(true);
        newUser.setTenantId(tenantId);
        
        // ✅ 4. Assign CUSTOMER role
        Role customerRole = roleRepository.findByName("CUSTOMER")
                .orElseThrow(() -> new RuntimeException("CUSTOMER role not found"));
        newUser.setRoles(Set.of(customerRole));
        
        try {
            AppUser savedUser = userRepository.save(newUser);
            log.info("✅ Created new customer: {} (ID: {})", email, savedUser.getId());
            return savedUser;
            
        } catch (DataIntegrityViolationException e) {
            // ✅ 5. Race condition - another thread created the user
            log.warn("⚠️ User creation race condition for email: {}, fetching existing", email);
            return userRepository.findByEmailIgnoreCase(email)
                    .orElseThrow(() -> new RuntimeException("Failed to create or find user: " + email));
        }
    }

    // ---------- DTOs ----------
    public static class WooCommerceOrder {
        private Long orderId;
        private String orderKey;
        private BigDecimal total;
        private String currency;
        private String billingEmail;
        private String customerPassword;
        private Long merchantId;
        private Long tenantId;
        private String webhookUrl;
        private String returnUrl;

        public Long getOrderId() { return orderId; }
        public void setOrderId(Long orderId) { this.orderId = orderId; }
        public String getOrderKey() { return orderKey; }
        public void setOrderKey(String orderKey) { this.orderKey = orderKey; }
        public BigDecimal getTotal() { return total; }
        public void setTotal(BigDecimal total) { this.total = total; }
        public String getCurrency() { return currency; }
        public void setCurrency(String currency) { this.currency = currency; }
        public String getBillingEmail() { return billingEmail; }
        public void setBillingEmail(String billingEmail) { this.billingEmail = billingEmail; }
        public String getCustomerPassword() { return customerPassword; }
        public void setCustomerPassword(String customerPassword) { this.customerPassword = customerPassword; }
        public Long getMerchantId() { return merchantId; }
        public void setMerchantId(Long merchantId) { this.merchantId = merchantId; }
        public Long getTenantId() { return tenantId; }
        public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
        public String getWebhookUrl() { return webhookUrl; }
        public void setWebhookUrl(String webhookUrl) { this.webhookUrl = webhookUrl; }
        public String getReturnUrl() { return returnUrl; }
        public void setReturnUrl(String returnUrl) { this.returnUrl = returnUrl; }
    }

    public static class WooCommerceResponse {
        private Long orderId;
        private String status;
        private Long transactionId;
        private String message;

        public WooCommerceResponse(Long orderId, String status, Long transactionId, String message) {
            this.orderId = orderId;
            this.status = status;
            this.transactionId = transactionId;
            this.message = message;
        }
        public Long getOrderId() { return orderId; }
        public String getStatus() { return status; }
        public Long getTransactionId() { return transactionId; }
        public String getMessage() { return message; }
    }
}