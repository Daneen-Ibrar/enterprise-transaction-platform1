package com.enterprise.api;

import com.enterprise.currency.ExchangeRateService;
import com.enterprise.invoice.Invoice;
import com.enterprise.invoice.InvoiceMessage;
import com.enterprise.invoice.InvoiceMessageService;
import com.enterprise.invoice.InvoicePdfService;
import com.enterprise.invoice.InvoiceService;
import com.enterprise.identity.AppUser;
import com.enterprise.identity.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
    private final ExchangeRateService exchangeRateService;
    private final InvoicePdfService invoicePdfService;

    public InvoiceController(InvoiceService invoiceService,
                             UserRepository userRepository,
                             InvoiceMessageService messageService,
                             ExchangeRateService exchangeRateService,
                             InvoicePdfService invoicePdfService) {
        this.invoiceService = invoiceService;
        this.userRepository = userRepository;
        this.messageService = messageService;
        this.exchangeRateService = exchangeRateService;
        this.invoicePdfService = invoicePdfService;
    }

    @GetMapping("/create")
    public String showCreateForm(Model model) {
        model.addAttribute("invoice", new Invoice());
        model.addAttribute("currencies", com.enterprise.currency.CurrencyCodes.getAllCurrencies());
        return "invoice/create";
    }

    @PostMapping("/create")
    public String createInvoice(@RequestParam BigDecimal amount,
                                @RequestParam String description,
                                @RequestParam String customerEmail,
                                @RequestParam(defaultValue = "GBP") String currency,
                                Authentication authentication) {
        AppUser merchant = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
        Long merchantId = merchant.getId();

        boolean requiresApproval = amount.compareTo(BigDecimal.valueOf(5000)) > 0;

        invoiceService.createInvoice(amount, description, customerEmail, merchantId, requiresApproval, currency);
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
            String symbol = exchangeRateService.getSymbol(invoice.getCurrency());
            model.addAttribute("invoice", invoice);
            model.addAttribute("symbol", symbol);
            return "invoice/detail";
        } catch (Exception e) {
            log.error("Error loading invoice detail for id {}: {}", id, e.getMessage(), e);
            model.addAttribute("error", "Could not load invoice details.");
            return "error";
        }
    }

    @GetMapping("/{id}/edit")
    public String showEditForm(@PathVariable Long id, Model model) {
        Invoice invoice = invoiceService.findById(id)
                .orElseThrow(() -> new RuntimeException("Invoice not found"));
        model.addAttribute("invoice", invoice);
        model.addAttribute("currencies", com.enterprise.currency.CurrencyCodes.getAllCurrencies());
        return "invoice/edit";
    }

    @PostMapping("/{id}")
    public String updateInvoice(@PathVariable Long id,
                                @RequestParam String description,
                                @RequestParam(required = false) String currency,
                                Authentication authentication) {
        Invoice invoice = invoiceService.findById(id)
                .orElseThrow(() -> new RuntimeException("Invoice not found"));
        AppUser user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
        if (!invoice.getMerchantId().equals(user.getId())) {
            throw new RuntimeException("You do not own this invoice");
        }
        invoice.setDescription(description);
        if (currency != null && !currency.isEmpty()) {
            invoice.setCurrency(currency);
        }
        invoice.setUpdatedAt(java.time.LocalDateTime.now());
        invoiceService.updateInvoice(invoice);
        return "redirect:/invoices/" + id;
    }

    // ============================================================
    // ✅ PDF EXPORT ENDPOINT
    // ============================================================
    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> exportPdf(@PathVariable Long id) {
        try {
            log.info("📄 Generating PDF for invoice {}", id);

            Invoice invoice = invoiceService.findById(id)
                    .orElseThrow(() -> new RuntimeException("Invoice not found"));

            if (!"APPROVED".equals(invoice.getStatus()) && 
                !"PAID".equals(invoice.getStatus()) &&
                !"PENDING_APPROVAL".equals(invoice.getStatus())) {
                log.warn("Invoice {} has status {} - PDF not available", id, invoice.getStatus());
                return ResponseEntity.badRequest().build();
            }

            byte[] pdfBytes = invoicePdfService.generateInvoicePdf(invoice);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("attachment", "invoice_" + id + ".pdf");
            headers.setContentLength(pdfBytes.length);

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(pdfBytes);

        } catch (Exception e) {
            log.error("Error generating PDF for invoice {}: {}", id, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/{id}/pay")
    public String redirectPay(@PathVariable Long id) {
        return "redirect:/pay/" + id;
    }

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