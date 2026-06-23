package com.enterprise.api;

import com.enterprise.identity.AppUser;
import com.enterprise.identity.Role;
import com.enterprise.identity.RoleRepository;
import com.enterprise.identity.UserRepository;
import com.enterprise.invoice.Invoice;
import com.enterprise.invoice.InvoiceService;
import com.enterprise.transaction.PaymentRequest;
import com.enterprise.transaction.PaymentResponse;
import com.enterprise.transaction.TransactionService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Set;
import java.util.UUID;

@Controller
@RequestMapping("/pay")
public class PublicPaymentController {

    private final InvoiceService invoiceService;
    private final TransactionService transactionService;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public PublicPaymentController(InvoiceService invoiceService,
                                   TransactionService transactionService,
                                   UserRepository userRepository,
                                   RoleRepository roleRepository,
                                   PasswordEncoder passwordEncoder) {
        this.invoiceService = invoiceService;
        this.transactionService = transactionService;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
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
        String idempotencyKey = UUID.randomUUID().toString();
        model.addAttribute("idempotencyKey", idempotencyKey);
        return "payment/gateway";
    }

    @PostMapping("/{invoiceId}")
    public String processPayment(@PathVariable Long invoiceId,
                                 @RequestParam String idempotencyKey,
                                 Model model) {
        try {
            Invoice invoice = invoiceService.findById(invoiceId)
                    .orElseThrow(() -> new RuntimeException("Invoice not found"));

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
                        return userRepository.save(newUser);
                    });

            PaymentRequest request = new PaymentRequest();
            request.setInvoiceId(invoiceId);
            request.setCustomerId(customer.getId());
            request.setMerchantId(invoice.getMerchantId());
            request.setAmount(invoice.getAmount());

            PaymentResponse response = transactionService.processPayment(request, idempotencyKey);

            // ✅ Mark invoice as PAID after successful settlement
            if ("SETTLED".equals(response.getStatus())) {
                invoiceService.markAsPaid(invoiceId, response.getTransactionId());
            }

            model.addAttribute("response", response);
            return "payment/result";

        } catch (Exception e) {
            model.addAttribute("error", "Payment failed: " + e.getMessage());
            return "payment/error";
        }
    }
}