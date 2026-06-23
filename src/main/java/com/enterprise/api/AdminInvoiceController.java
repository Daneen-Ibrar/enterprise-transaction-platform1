package com.enterprise.api;

import com.enterprise.invoice.Invoice;
import com.enterprise.invoice.InvoiceMessage;
import com.enterprise.invoice.InvoiceMessageService;
import com.enterprise.invoice.InvoiceRepository;
import com.enterprise.invoice.InvoiceService;
import com.enterprise.identity.AppUser;
import com.enterprise.identity.UserRepository;
import com.enterprise.notification.NotificationService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@Controller
@RequestMapping("/admin/invoices")
@PreAuthorize("hasRole('ADMIN')")
public class AdminInvoiceController {

    private final InvoiceService invoiceService;
    private final UserRepository userRepository;
    private final InvoiceMessageService messageService;
    private final InvoiceRepository invoiceRepository;
    private final NotificationService notificationService;

    public AdminInvoiceController(InvoiceService invoiceService,
                                  UserRepository userRepository,
                                  InvoiceMessageService messageService,
                                  InvoiceRepository invoiceRepository,
                                  NotificationService notificationService) {
        this.invoiceService = invoiceService;
        this.userRepository = userRepository;
        this.messageService = messageService;
        this.invoiceRepository = invoiceRepository;
        this.notificationService = notificationService;
    }

    @GetMapping("/pending")
    public String pendingInvoices(Model model) {
        List<Invoice> pending = invoiceService.getPendingApprovalInvoices();
        model.addAttribute("invoices", pending);
        return "admin/invoices/pending";
    }

    @PostMapping("/{id}/approve")
    public String approveInvoice(@PathVariable Long id, Authentication authentication) {
        AppUser admin = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
        invoiceService.approveInvoice(id, admin.getId());
        return "redirect:/admin/invoices/pending";
    }

    @PostMapping("/{id}/reject")
    public String rejectInvoice(@PathVariable Long id, Authentication authentication) {
        AppUser admin = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
        invoiceService.rejectInvoice(id, admin.getId());
        return "redirect:/admin/invoices/pending";
    }

    // --- New: Rejected invoices view ---
    @GetMapping("/rejected")
    public String rejectedInvoices(@RequestParam(required = false) String search,
                                   Model model) {
        List<Invoice> rejected;
        if (search != null && !search.isEmpty()) {
            rejected = invoiceRepository.findByStatusAndCustomerEmailContainingIgnoreCase("REJECTED", search);
        } else {
            rejected = invoiceRepository.findByStatus("REJECTED");
        }
        model.addAttribute("invoices", rejected);
        model.addAttribute("search", search);
        return "admin/invoices/rejected";
    }

    @PostMapping("/{id}/reapprove")
    public String reapproveInvoice(@PathVariable Long id, Authentication authentication) {
        AppUser admin = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new RuntimeException("Admin not found"));
        Invoice invoice = invoiceService.findById(id)
                .orElseThrow(() -> new RuntimeException("Invoice not found"));
        if (!"REJECTED".equals(invoice.getStatus())) {
            throw new IllegalStateException("Only rejected invoices can be reapproved");
        }
        invoice.setStatus("APPROVED");
        invoice.setUpdatedAt(LocalDateTime.now());
        invoiceRepository.save(invoice);

        notificationService.createNotification(
            invoice.getMerchantId(),
            "INVOICE_REAPPROVED",
            "Invoice Reapproved",
            "Invoice #" + id + " has been reapproved by Admin",
            "/invoices/" + id
        );
        return "redirect:/admin/invoices/rejected";
    }

    // --- Chat endpoints for admin ---
    @GetMapping("/{id}/messages")
    public String viewAdminChat(@PathVariable Long id, Model model) {
        Invoice invoice = invoiceService.findById(id)
                .orElseThrow(() -> new RuntimeException("Invoice not found"));
        List<InvoiceMessage> messages = messageService.getMessagesForInvoice(id);
        model.addAttribute("invoice", invoice);
        model.addAttribute("messages", messages);
        return "admin/invoices/chat";
    }

    @PostMapping("/{id}/messages")
    public String sendAdminReply(@PathVariable Long id,
                                 @RequestParam String message,
                                 Authentication authentication) {
        Invoice invoice = invoiceService.findById(id)
                .orElseThrow(() -> new RuntimeException("Invoice not found"));
        AppUser admin = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new RuntimeException("Admin not found"));
        messageService.sendMessage(invoice, admin, message);
        return "redirect:/admin/invoices/" + id + "/messages";
    }
}