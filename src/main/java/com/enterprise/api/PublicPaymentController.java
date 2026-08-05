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
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Controller
@RequestMapping("/pay")
public class PublicPaymentController {

    private static final Logger log = LoggerFactory.getLogger(PublicPaymentController.class);

    private final InvoiceService invoiceService;
    private final TransactionService transactionService;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final RestTemplate restTemplate;

    public PublicPaymentController(InvoiceService invoiceService,
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
    public String showPaymentForm(@PathVariable Long invoiceId, Model model) {
        Invoice invoice = invoiceService.findById(invoiceId)
                .orElseThrow(() -> new RuntimeException("Invoice not found"));

        if (!"APPROVED".equals(invoice.getStatus())) {
            model.addAttribute("error", "This invoice is not available for payment.");
            return "payment/error";
        }

        model.addAttribute("invoice", invoice);
        model.addAttribute("idempotencyKey", UUID.randomUUID().toString());
        return "payment/gateway";
    }

    @PostMapping("/{invoiceId}")
    public String processPayment(@PathVariable Long invoiceId,
                                 @RequestParam String idempotencyKey,
                                 @RequestParam(required = false) String wooOrderId,
                                 Model model) {
        try {
            Invoice invoice = invoiceService.findById(invoiceId)
                    .orElseThrow(() -> new RuntimeException("Invoice not found"));

            Long tenantId = invoice.getTenantId() != null ? invoice.getTenantId() : 1L;
            TenantContext.setTenantId(tenantId);
            log.info("🔵 Tenant set to {} for invoice {}", tenantId, invoiceId);

            if (!"APPROVED".equals(invoice.getStatus())) {
                model.addAttribute("error", "This invoice is not available for payment.");
                return "payment/error";
            }

            AppUser customer = userRepository.findByEmail(invoice.getCustomerEmail())
                    .orElseGet(() -> {
                        AppUser newUser = new AppUser();
                        newUser.setEmail(invoice.getCustomerEmail());
                        newUser.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));
                        Role customerRole = roleRepository.findByName("CUSTOMER")
                                .orElseThrow(() -> new RuntimeException("CUSTOMER role not found"));
                        newUser.setRoles(Set.of(customerRole));
                        newUser.setActive(true);
                        newUser.setTenantId(tenantId);
                        return userRepository.save(newUser);
                    });

            if (!customer.isActive()) {
                model.addAttribute("error", "Your account is disabled.");
                return "payment/error";
            }

            PaymentRequest request = new PaymentRequest();
            request.setInvoiceId(invoiceId);
            request.setCustomerId(customer.getId());
            request.setMerchantId(invoice.getMerchantId());
            request.setAmount(invoice.getAmount());
            request.setCurrency(invoice.getCurrency());

            PaymentResponse response = transactionService.processPayment(request, idempotencyKey);

            if ("SETTLED".equals(response.getStatus())) {
                invoiceService.markAsPaid(invoiceId, response.getTransactionId());

                if (invoice.getWebhookUrl() != null && !invoice.getWebhookUrl().isEmpty()) {
                    log.info("📤 Sending webhook to {}", invoice.getWebhookUrl());

                    Map<String, Object> payload = Map.of(
                            "orderId", invoice.getWooOrderId() != null ? invoice.getWooOrderId() : 0,
                            "status", response.getStatus(),
                            "transactionId", response.getTransactionId(),
                            "invoiceId", invoiceId
                    );

                    HttpHeaders headers = new HttpHeaders();
                    headers.setContentType(MediaType.APPLICATION_JSON);
                    HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);

                    try {
                        restTemplate.postForEntity(invoice.getWebhookUrl(), entity, String.class);
                        log.info("✅ Webhook sent successfully");
                    } catch (Exception e) {
                        log.error("❌ Webhook failed: {}", e.getMessage());
                    }

                    // ===== REDIRECT – use stored orderKey =====
                    String orderIdForRedirect = wooOrderId != null ? wooOrderId :
                            (invoice.getWooOrderId() != null ? String.valueOf(invoice.getWooOrderId()) : null);

                    if (invoice.getReturnUrl() != null && !invoice.getReturnUrl().isEmpty() && orderIdForRedirect != null) {
                        String orderKey = invoice.getOrderKey() != null ? invoice.getOrderKey() : "wc_order_" + orderIdForRedirect;
                        String redirect = invoice.getReturnUrl() + "/" + orderIdForRedirect + "/?key=" + orderKey;
                        log.info("🔀 Redirecting to: " + redirect);
                        return "redirect:" + redirect;
                    } else {
                        log.warn("⚠️ Cannot redirect: returnUrl or orderId is missing");
                        model.addAttribute("response", response);
                        return "payment/result";
                    }
                }

                model.addAttribute("response", response);
                return "payment/result";
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