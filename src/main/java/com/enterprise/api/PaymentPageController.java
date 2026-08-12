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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Controller
@RequestMapping("/pay")
public class PaymentPageController {

    private static final Logger log = LoggerFactory.getLogger(PaymentPageController.class);

    private final InvoiceService invoiceService;
    private final TransactionService transactionService;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final RestTemplate restTemplate;

    public PaymentPageController(InvoiceService invoiceService,
                                 TransactionService transactionService,
                                 UserRepository userRepository,
                                 RoleRepository roleRepository,
                                 PasswordEncoder passwordEncoder,
                                 RestTemplate restTemplate) {
        this.invoiceService = invoiceService;
        this.transactionService = transactionService;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.restTemplate = restTemplate;
    }

    @GetMapping("/{invoiceId}")
    public String paymentPage(@PathVariable Long invoiceId, Model model) {
        Invoice invoice = invoiceService.findById(invoiceId)
                .orElseThrow(() -> new RuntimeException("Invoice not found"));

        if (!"APPROVED".equals(invoice.getStatus())) {
            return "redirect:/dashboard?error=Invoice not approved for payment";
        }

        model.addAttribute("invoice", invoice);
        model.addAttribute("idempotencyKey", UUID.randomUUID().toString());
        return "payment/gateway";
    }

    @PostMapping("/{invoiceId}")
    public String processPayment(
            @PathVariable Long invoiceId,
            @RequestParam String idempotencyKey,
            @RequestParam(required = false) String cardNumber,
            @RequestParam(required = false) String expiry,
            @RequestParam(required = false) String cvv,
            Model model) {

        try {
            Invoice invoice = invoiceService.findById(invoiceId)
                    .orElseThrow(() -> new RuntimeException("Invoice not found"));

            if (!"APPROVED".equals(invoice.getStatus())) {
                model.addAttribute("error", "This invoice is not available for payment.");
                return "payment/error";
            }

            Long tenantId = invoice.getTenantId() != null ? invoice.getTenantId() : 1L;
            TenantContext.setTenantId(tenantId);

            // Create or find customer
            AppUser customer = userRepository.findByEmail(invoice.getCustomerEmail())
                    .orElseGet(() -> {
                        try {
                            AppUser newUser = new AppUser();
                            newUser.setEmail(invoice.getCustomerEmail());
                            newUser.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));
                            Role customerRole = roleRepository.findByName("CUSTOMER")
                                    .orElseThrow(() -> new RuntimeException("CUSTOMER role not found"));
                            newUser.setRoles(Set.of(customerRole));
                            newUser.setActive(true);
                            newUser.setTenantId(tenantId);
                            return userRepository.save(newUser);
                        } catch (DataIntegrityViolationException e) {
                            return userRepository.findByEmail(invoice.getCustomerEmail())
                                    .orElseThrow(() -> new RuntimeException("User creation race condition"));
                        }
                    });

            // Process payment
            PaymentRequest request = new PaymentRequest();
            request.setInvoiceId(invoiceId);
            request.setCustomerId(customer.getId());
            request.setMerchantId(invoice.getMerchantId());
            request.setAmount(invoice.getAmount());
            request.setCurrency(invoice.getCurrency());

            PaymentResponse response = transactionService.processPayment(request, idempotencyKey);

            if ("SETTLED".equals(response.getStatus())) {
                invoiceService.markAsPaid(invoiceId, response.getTransactionId());

                // ✅ FIX: Use HashMap instead of Map.of() to handle null values
                // And check if webhookUrl is a valid HTTP URL
                String webhookUrl = invoice.getWebhookUrl();
                boolean isValidWebhookUrl = webhookUrl != null &&
                        !webhookUrl.isEmpty() &&
                        !webhookUrl.startsWith("xero:") &&
                        !webhookUrl.startsWith("00000000-0000-0000-0000-000000000000") &&
                        (webhookUrl.startsWith("http://") || webhookUrl.startsWith("https://"));

                if (isValidWebhookUrl) {
                    log.info("📤 Sending webhook to {}", webhookUrl);

                    // ✅ Use HashMap to avoid null pointer issues
                    Map<String, Object> payload = new HashMap<>();
                    payload.put("orderId", invoice.getWooOrderId() != null ? invoice.getWooOrderId() : invoiceId);
                    payload.put("status", response.getStatus() != null ? response.getStatus() : "SETTLED");
                    payload.put("transactionId", response.getTransactionId() != null ? response.getTransactionId() : -1L);
                    payload.put("invoiceId", invoiceId);

                    HttpHeaders headers = new HttpHeaders();
                    headers.setContentType(MediaType.APPLICATION_JSON);
                    HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);
                    try {
                        restTemplate.postForEntity(webhookUrl, entity, String.class);
                        log.info("✅ Webhook sent successfully");
                    } catch (Exception e) {
                        log.error("❌ Webhook failed: {}", e.getMessage());
                    }
                } else {
                    log.info("⏭️ Skipping webhook - webhookUrl is not valid HTTP URL: {}", webhookUrl);
                }

                // Redirect to WooCommerce if configured
                if (invoice.getReturnUrl() != null && !invoice.getReturnUrl().isEmpty() && invoice.getWooOrderId() != null) {
                    String orderKey = invoice.getOrderKey() != null ? invoice.getOrderKey() : "wc_order_" + invoice.getWooOrderId();
                    String redirect = invoice.getReturnUrl() + "/" + invoice.getWooOrderId() + "/?key=" + orderKey;
                    log.info("🔀 Redirecting to: {}", redirect);
                    return "redirect:" + redirect;
                } else {
                    model.addAttribute("response", response);
                    return "payment/result";
                }
            }

            model.addAttribute("response", response);
            return "payment/result";

        } catch (Exception e) {
            log.error("Payment error", e);
            model.addAttribute("error", "Payment failed: " + e.getMessage());
            
            return "payment/error";
        } finally {
            TenantContext.clear();
        }
    }
}