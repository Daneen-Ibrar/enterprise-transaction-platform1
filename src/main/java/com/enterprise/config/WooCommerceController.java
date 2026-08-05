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
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
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
            AppUser customer = userRepository.findByEmail(order.getBillingEmail())
                    .orElseGet(() -> {
                        AppUser newUser = new AppUser();
                        newUser.setEmail(order.getBillingEmail());
                        String password = order.getCustomerPassword() != null ?
                                order.getCustomerPassword() :
                                UUID.randomUUID().toString();
                        newUser.setPasswordHash(passwordEncoder.encode(password));
                        newUser.setActive(true);
                        newUser.setTenantId(tenantId);
                        Role customerRole = roleRepository.findByName("CUSTOMER")
                                .orElseThrow(() -> new RuntimeException("CUSTOMER role not found"));
                        newUser.setRoles(Set.of(customerRole));
                        return userRepository.save(newUser);
                    });

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
                    "transactionId", response.getTransactionId()
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