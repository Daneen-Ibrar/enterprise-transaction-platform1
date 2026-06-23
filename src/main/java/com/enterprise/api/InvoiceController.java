package com.enterprise.api;

import com.enterprise.invoice.Invoice;
import com.enterprise.invoice.InvoiceMessage;
import com.enterprise.invoice.InvoiceMessageService;
import com.enterprise.invoice.InvoiceService;
import com.enterprise.identity.AppUser;
import com.enterprise.identity.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@Controller
@RequestMapping("/invoices")
public class InvoiceController {

    private static final Logger log = LoggerFactory.getLogger(InvoiceController.class);

    private final InvoiceService invoiceService;
    private final UserRepository userRepository;
    private final InvoiceMessageService messageService;

    public InvoiceController(InvoiceService invoiceService,
                             UserRepository userRepository,
                             InvoiceMessageService messageService) {
        this.invoiceService = invoiceService;
        this.userRepository = userRepository;
        this.messageService = messageService;
    }

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

        boolean requiresApproval = amount.compareTo(BigDecimal.valueOf(5000)) > 0;
        invoiceService.createInvoice(amount, description, customerEmail, merchantId, requiresApproval);
        return "redirect:/dashboard";
    }

    @GetMapping("/customer")
    public String customerInvoices(Authentication authentication, Model model) {
        String email = authentication.getName();
        List<Invoice> invoices = invoiceService.getInvoicesForCustomer(email);
        model.addAttribute("invoices", invoices);
        return "invoice/customer-list";
    }

    @GetMapping("/merchant")
    public String merchantInvoices(Authentication authentication, Model model) {
        AppUser merchant = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
        List<Invoice> invoices = invoiceService.getInvoicesForMerchant(merchant.getId());
        model.addAttribute("invoices", invoices);
        return "invoice/merchant-list";
    }

    @GetMapping("/{id}")
    public String viewInvoice(@PathVariable Long id, Model model) {
        try {
            Invoice invoice = invoiceService.findById(id)
                    .orElseThrow(() -> new RuntimeException("Invoice not found"));
            model.addAttribute("invoice", invoice);
            return "invoice/detail";
        } catch (Exception e) {
            log.error("Error loading invoice detail for id {}: {}", id, e.getMessage(), e);
            model.addAttribute("error", "Could not load invoice details.");
            return "error";
        }
    }

    // Redirect old pay form to public gateway
    @GetMapping("/{id}/pay")
    public String redirectPay(@PathVariable Long id) {
        return "redirect:/pay/" + id;
    }

    // === Chat endpoints ===

    @GetMapping("/{id}/messages")
    public String viewChat(@PathVariable Long id, Model model, Authentication authentication) {
        Invoice invoice = invoiceService.findById(id)
                .orElseThrow(() -> new RuntimeException("Invoice not found"));
        AppUser user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));

        boolean isMerchant = user.getId().equals(invoice.getMerchantId());
        boolean isCustomer = invoice.getCustomerEmail().equals(user.getEmail());
        boolean isAdmin = user.getRoles().stream().anyMatch(r -> r.getName().equals("ADMIN"));
        if (!(isMerchant || isCustomer || isAdmin)) {
            throw new RuntimeException("Unauthorized");
        }

        List<InvoiceMessage> messages = messageService.getMessagesForInvoice(id);
        model.addAttribute("invoice", invoice);
        model.addAttribute("messages", messages);
        model.addAttribute("userId", user.getId());
        return "invoice/chat";
    }

    @PostMapping("/{id}/messages")
    public String sendMessage(@PathVariable Long id,
                              @RequestParam String message,
                              Authentication authentication) {
        Invoice invoice = invoiceService.findById(id)
                .orElseThrow(() -> new RuntimeException("Invoice not found"));
        AppUser user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));

        boolean isMerchant = user.getId().equals(invoice.getMerchantId());
        boolean isCustomer = invoice.getCustomerEmail().equals(user.getEmail());
        boolean isAdmin = user.getRoles().stream().anyMatch(r -> r.getName().equals("ADMIN"));
        if (!(isMerchant || isCustomer || isAdmin)) {
            throw new RuntimeException("Unauthorized");
        }

        messageService.sendMessage(invoice, user, message);
        return "redirect:/invoices/" + id + "/messages";
    }
}