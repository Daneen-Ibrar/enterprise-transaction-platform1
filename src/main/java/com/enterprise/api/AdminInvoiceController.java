package com.enterprise.api;

import com.enterprise.invoice.Invoice;
import com.enterprise.invoice.InvoiceService;
import com.enterprise.identity.AppUser;
import com.enterprise.identity.UserRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequestMapping("/admin/invoices")
@PreAuthorize("hasRole('ADMIN')")
public class AdminInvoiceController {

    private final InvoiceService invoiceService;
    private final UserRepository userRepository;

    public AdminInvoiceController(InvoiceService invoiceService, UserRepository userRepository) {
        this.invoiceService = invoiceService;
        this.userRepository = userRepository;
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
}