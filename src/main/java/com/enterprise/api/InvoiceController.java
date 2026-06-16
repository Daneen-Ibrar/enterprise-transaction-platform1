package com.enterprise.api;

import com.enterprise.invoice.Invoice;
import com.enterprise.invoice.InvoiceService;
import com.enterprise.identity.AppUser;
import com.enterprise.identity.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@Controller
@RequestMapping("/invoices")
public class InvoiceController {

    private final InvoiceService invoiceService;
    private final UserRepository userRepository;

    public InvoiceController(InvoiceService invoiceService, UserRepository userRepository) {
        this.invoiceService = invoiceService;
        this.userRepository = userRepository;
    }

    // Merchant: create invoice form
    @GetMapping("/create")
    public String showCreateForm(Model model) {
        model.addAttribute("invoice", new Invoice());
        return "invoice/create";
    }

    @PostMapping("/create")
    public String createInvoice(@RequestParam BigDecimal amount,
                                @RequestParam String description,
                                @RequestParam String customerEmail,
                                Authentication authentication) {
        AppUser merchant = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
        Long merchantId = merchant.getId();

        // Determine if approval needed (e.g., amount > 5000)
        boolean requiresApproval = amount.compareTo(BigDecimal.valueOf(5000)) > 0;

        invoiceService.createInvoice(amount, description, customerEmail, merchantId, requiresApproval);
        return "redirect:/dashboard";
    }

    // Customer view: list their invoices
    @GetMapping("/customer")
    public String customerInvoices(Authentication authentication, Model model) {
        String email = authentication.getName();
        List<Invoice> invoices = invoiceService.getInvoicesForCustomer(email);
        model.addAttribute("invoices", invoices);
        return "invoice/customer-list";
    }

    // Merchant view: list their invoices
    @GetMapping("/merchant")
    public String merchantInvoices(Authentication authentication, Model model) {
        AppUser merchant = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
        List<Invoice> invoices = invoiceService.getInvoicesForMerchant(merchant.getId());
        model.addAttribute("invoices", invoices);
        return "invoice/merchant-list";
    }

    // View invoice detail
    @GetMapping("/{id}")
    public String viewInvoice(@PathVariable Long id, Model model) {
        Invoice invoice = invoiceService.findById(id)
                .orElseThrow(() -> new RuntimeException("Invoice not found"));
        model.addAttribute("invoice", invoice);
        return "invoice/detail";
    }

    // Customer: pay invoice (GET form, POST will be handled by PaymentController)
    @GetMapping("/{id}/pay")
    public String showPayForm(@PathVariable Long id, Model model) {
        Invoice invoice = invoiceService.findById(id)
                .orElseThrow(() -> new RuntimeException("Invoice not found"));
        if (!"APPROVED".equals(invoice.getStatus())) {
            throw new IllegalStateException("Invoice is not approved for payment");
        }
        model.addAttribute("invoice", invoice);
        return "invoice/pay";
    }
}