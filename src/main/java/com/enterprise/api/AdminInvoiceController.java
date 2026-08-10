package com.enterprise.api;

import com.enterprise.feature.FeatureFlagService;
import com.enterprise.invoice.Invoice;
import com.enterprise.invoice.InvoiceMessage;
import com.enterprise.invoice.InvoiceMessageService;
import com.enterprise.invoice.InvoiceRepository;
import com.enterprise.invoice.InvoiceService;
import com.enterprise.invoice.SuspicionService;
import com.enterprise.identity.AppUser;
import com.enterprise.identity.UserRepository;
import com.enterprise.notification.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/admin/invoices")
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MERCHANT_ADMIN')")
public class AdminInvoiceController {

    private static final Logger log = LoggerFactory.getLogger(AdminInvoiceController.class);

    private final InvoiceService invoiceService;
    private final UserRepository userRepository;
    private final InvoiceMessageService messageService;
    private final InvoiceRepository invoiceRepository;
    private final NotificationService notificationService;
    private final FeatureFlagService featureFlagService;
    private final SuspicionService suspicionService;

    public AdminInvoiceController(InvoiceService invoiceService,
                                  UserRepository userRepository,
                                  InvoiceMessageService messageService,
                                  InvoiceRepository invoiceRepository,
                                  NotificationService notificationService,
                                  FeatureFlagService featureFlagService,
                                  SuspicionService suspicionService) {
        this.invoiceService = invoiceService;
        this.userRepository = userRepository;
        this.messageService = messageService;
        this.invoiceRepository = invoiceRepository;
        this.notificationService = notificationService;
        this.featureFlagService = featureFlagService;
        this.suspicionService = suspicionService;
    }

    // ===== PENDING INVOICES (only GREEN risk) =====
    @GetMapping("/pending")
    public String pendingInvoices(Model model) {
        try {
            log.info("Loading pending invoices page");
            List<Invoice> allPending = invoiceService.getPendingApprovalInvoices();
            log.info("Found {} pending invoices", allPending.size());

            List<Invoice> pending = allPending.stream()
                    .filter(inv -> {
                        String risk = inv.getRiskLevel();
                        return risk != null && "GREEN".equals(risk);
                    })
                    .collect(Collectors.toList());

            log.info("Filtered to {} non-suspicious pending invoices", pending.size());
            model.addAttribute("invoices", pending);
            return "admin/invoices/pending";
        } catch (Exception e) {
            log.error("Error loading pending invoices", e);
            model.addAttribute("error", "Failed to load pending invoices: " + e.getMessage());
            model.addAttribute("invoices", Collections.emptyList());
            return "admin/invoices/pending";
        }
    }

    // ===== SINGLE APPROVE =====
    @PostMapping("/{id}/approve")
    public String approveInvoice(@PathVariable Long id, 
                                 Authentication authentication,
                                 RedirectAttributes redirectAttributes) {
        log.info("🔵 ===== SINGLE APPROVE REQUEST for invoice ID: {} =====", id);
        log.info("🔵 User: {}", authentication.getName());
        
        try {
            AppUser admin = userRepository.findByEmail(authentication.getName())
                    .orElseThrow(() -> new RuntimeException("Admin not found"));
            log.info("🔵 Admin found: {} (ID: {})", admin.getEmail(), admin.getId());

            var invoiceOpt = invoiceService.findById(id);
            if (invoiceOpt.isEmpty()) {
                log.error("🔵 Invoice {} not found", id);
                redirectAttributes.addFlashAttribute("error", "Invoice not found");
                return "redirect:/admin/invoices/pending";
            }
            
            Invoice invoice = invoiceOpt.get();
            log.info("🔵 Invoice found: ID={}, status={}, risk={}", invoice.getId(), invoice.getStatus(), invoice.getRiskLevel());
            
            if (!"PENDING_APPROVAL".equals(invoice.getStatus())) {
                log.warn("🔵 Invoice {} is not pending approval (status: {})", id, invoice.getStatus());
                redirectAttributes.addFlashAttribute("error", "Invoice is not pending approval");
                return "redirect:/admin/invoices/pending";
            }

            log.info("🔵 Calling invoiceService.approveInvoice({}, {})", id, admin.getId());
            Invoice approvedInvoice = invoiceService.approveInvoice(id, admin.getId());
            log.info("✅ Invoice {} approved successfully. New status: {}", id, approvedInvoice.getStatus());
            
            redirectAttributes.addFlashAttribute("success", "Invoice #" + id + " approved successfully.");
            return "redirect:/admin/invoices/pending";
            
        } catch (Exception e) {
            log.error("❌ Error approving invoice {}: {}", id, e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error", "Failed to approve invoice: " + e.getMessage());
            return "redirect:/admin/invoices/pending";
        }
    }

    // ===== SINGLE REJECT (with default reason) =====
    @PostMapping("/{id}/reject")
    public String rejectInvoice(@PathVariable Long id, 
                                @RequestParam(required = false) String reason,
                                Authentication authentication,
                                RedirectAttributes redirectAttributes) {
        log.info("🔴 ===== SINGLE REJECT REQUEST for invoice ID: {} =====", id);
        log.info("🔴 User: {}, Reason: {}", authentication.getName(), reason);
        
        try {
            AppUser admin = userRepository.findByEmail(authentication.getName())
                    .orElseThrow(() -> new RuntimeException("Admin not found"));
            log.info("🔴 Admin found: {} (ID: {})", admin.getEmail(), admin.getId());

            // If no reason provided, use a default
            if (reason == null || reason.trim().isEmpty()) {
                reason = "Rejected by admin";
            }

            Invoice rejectedInvoice = invoiceService.rejectInvoice(id, admin.getId(), reason);
            log.info("✅ Invoice {} rejected successfully. New status: {}", id, rejectedInvoice.getStatus());
            
            redirectAttributes.addFlashAttribute("success", "Invoice #" + id + " rejected.");
            return "redirect:/admin/invoices/pending";
            
        } catch (Exception e) {
            log.error("❌ Error rejecting invoice {}: {}", id, e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error", "Failed to reject invoice: " + e.getMessage());
            return "redirect:/admin/invoices/pending";
        }
    }

    // ===== BULK APPROVE =====
    @PostMapping("/bulk/approve")
    public String bulkApprove(@RequestParam List<Long> ids,
                              Authentication authentication,
                              RedirectAttributes redirectAttributes) {
        log.info("📦 BULK APPROVE for {} invoices", ids.size());
        try {
            AppUser admin = userRepository.findByEmail(authentication.getName())
                    .orElseThrow(() -> new RuntimeException("Admin not found"));
            int count = invoiceService.bulkApproveInvoices(ids, admin.getId());
            redirectAttributes.addFlashAttribute("success", "Bulk approved " + count + " invoices.");
        } catch (Exception e) {
            log.error("Error in bulk approve", e);
            redirectAttributes.addFlashAttribute("error", "Bulk approve failed: " + e.getMessage());
        }
        return "redirect:/admin/invoices/pending";
    }

    // ===== BULK REJECT =====
    @PostMapping("/bulk/reject")
    public String bulkReject(@RequestParam List<Long> ids,
                             @RequestParam(required = false) String reason,
                             Authentication authentication,
                             RedirectAttributes redirectAttributes) {
        log.info("📦 BULK REJECT for {} invoices", ids.size());
        try {
            AppUser admin = userRepository.findByEmail(authentication.getName())
                    .orElseThrow(() -> new RuntimeException("Admin not found"));
            int count = invoiceService.bulkRejectInvoices(ids, admin.getId());
            redirectAttributes.addFlashAttribute("success", "Bulk rejected " + count + " invoices.");
        } catch (Exception e) {
            log.error("Error in bulk reject", e);
            redirectAttributes.addFlashAttribute("error", "Bulk reject failed: " + e.getMessage());
        }
        return "redirect:/admin/invoices/pending";
    }

    // ===== SUSPICIOUS INVOICES =====
    @GetMapping("/suspicious")
    public String suspiciousInvoices(Model model) {
        try {
            boolean suspicionEnabled = featureFlagService.isEnabled("SUSPICION_DETECTION");
            model.addAttribute("suspicionEnabled", suspicionEnabled);

            if (suspicionEnabled) {
                List<Invoice> allPending = invoiceService.getPendingApprovalInvoices();
                List<Invoice> suspicious = allPending.stream()
                        .filter(inv -> {
                            String risk = inv.getRiskLevel();
                            return risk != null && !"GREEN".equals(risk);
                        })
                        .collect(Collectors.toList());
                model.addAttribute("invoices", suspicious);
            } else {
                model.addAttribute("invoices", Collections.emptyList());
            }
            return "admin/invoices/suspicious";
        } catch (Exception e) {
            log.error("Error loading suspicious invoices", e);
            model.addAttribute("error", "Failed to load suspicious invoices: " + e.getMessage());
            model.addAttribute("invoices", Collections.emptyList());
            model.addAttribute("suspicionEnabled", false);
            return "admin/invoices/suspicious";
        }
    }

    // ===== NOT FRAUDULENT =====
    @PostMapping("/{id}/not-fraudulent")
    public String notFraudulent(@PathVariable Long id, Authentication authentication) {
        try {
            AppUser admin = userRepository.findByEmail(authentication.getName())
                    .orElseThrow(() -> new RuntimeException("Admin not found"));
            Invoice invoice = invoiceService.findById(id)
                    .orElseThrow(() -> new RuntimeException("Invoice not found"));
            if (!"PENDING_APPROVAL".equals(invoice.getStatus())) {
                throw new IllegalStateException("Only pending invoices can be marked as not fraudulent");
            }
            invoice.setRiskLevel("GREEN");
            invoice.setSuspicionReason("Cleared by admin (not fraudulent)");
            invoice.setUpdatedAt(LocalDateTime.now());
            invoiceRepository.save(invoice);

            notificationService.createNotification(
                    invoice.getMerchantId(),
                    "INVOICE_CLEARED",
                    "Invoice Cleared",
                    "Invoice #" + id + " was marked as not fraudulent and moved to pending approval.",
                    "/admin/invoices/pending"
            );
            return "redirect:/admin/invoices/suspicious";
        } catch (Exception e) {
            log.error("Error marking not fraudulent for invoice {}", id, e);
            return "redirect:/admin/invoices/suspicious?error=" + e.getMessage();
        }
    }

    // ===== FRAUDULENT =====
    @PostMapping("/{id}/fraudulent")
    public String fraudulent(@PathVariable Long id, Authentication authentication) {
        try {
            AppUser admin = userRepository.findByEmail(authentication.getName())
                    .orElseThrow(() -> new RuntimeException("Admin not found"));
            Invoice invoice = invoiceService.findById(id)
                    .orElseThrow(() -> new RuntimeException("Invoice not found"));
            if (!"PENDING_APPROVAL".equals(invoice.getStatus())) {
                throw new IllegalStateException("Only pending invoices can be marked as fraudulent");
            }
            invoice.setStatus("REJECTED");
            invoice.setRiskLevel("RED");
            invoice.setSuspicionReason("Marked as fraudulent by admin");
            invoice.setUpdatedAt(LocalDateTime.now());
            invoiceRepository.save(invoice);

            notificationService.createNotification(
                    invoice.getMerchantId(),
                    "INVOICE_REJECTED_FRAUD",
                    "Invoice Rejected as Fraudulent",
                    "Invoice #" + id + " was rejected and blocked as fraudulent.",
                    "/admin/invoices/rejected"
            );
            return "redirect:/admin/invoices/suspicious";
        } catch (Exception e) {
            log.error("Error marking fraudulent for invoice {}", id, e);
            return "redirect:/admin/invoices/suspicious?error=" + e.getMessage();
        }
    }

    // ===== REJECTED INVOICES VIEW =====
    @GetMapping("/rejected")
    public String rejectedInvoices(@RequestParam(required = false) String search,
                                   Model model) {
        try {
            List<Invoice> rejected;
            if (search != null && !search.isEmpty()) {
                rejected = invoiceRepository.findByStatusAndCustomerEmailContainingIgnoreCase("REJECTED", search);
            } else {
                rejected = invoiceRepository.findByStatus("REJECTED");
            }
            model.addAttribute("invoices", rejected);
            model.addAttribute("search", search);
            return "admin/invoices/rejected";
        } catch (Exception e) {
            log.error("Error loading rejected invoices", e);
            model.addAttribute("error", "Failed to load rejected invoices: " + e.getMessage());
            model.addAttribute("invoices", Collections.emptyList());
            return "admin/invoices/rejected";
        }
    }

    // ===== REAPPROVE =====
    @PostMapping("/{id}/reapprove")
    public String reapproveInvoice(@PathVariable Long id, Authentication authentication) {
        try {
            AppUser admin = userRepository.findByEmail(authentication.getName())
                    .orElseThrow(() -> new RuntimeException("Admin not found"));
            Invoice invoice = invoiceService.findById(id)
                    .orElseThrow(() -> new RuntimeException("Invoice not found"));
            if (!"REJECTED".equals(invoice.getStatus())) {
                throw new IllegalStateException("Only rejected invoices can be reapproved");
            }

            SuspicionService.SuspicionResult result = suspicionService.evaluate(invoice);
            invoice.setRiskLevel(result.getRiskLevel());
            invoice.setSuspicionReason(result.getReason());

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
        } catch (Exception e) {
            log.error("Error reapproving invoice {}", id, e);
            return "redirect:/admin/invoices/rejected?error=" + e.getMessage();
        }
    }

    // ===== CHAT ENDPOINTS =====
    @GetMapping("/{id}/messages")
    public String viewAdminChat(@PathVariable Long id, Model model) {
        try {
            Invoice invoice = invoiceService.findById(id)
                    .orElseThrow(() -> new RuntimeException("Invoice not found"));
            List<InvoiceMessage> messages = messageService.getMessagesForInvoice(id);
            model.addAttribute("invoice", invoice);
            model.addAttribute("messages", messages);
            return "admin/invoices/chat";
        } catch (Exception e) {
            log.error("Error loading chat for invoice {}", id, e);
            model.addAttribute("error", "Failed to load chat: " + e.getMessage());
            return "admin/invoices/chat";
        }
    }

    @PostMapping("/{id}/messages")
    public String sendAdminReply(@PathVariable Long id,
                                 @RequestParam String message,
                                 Authentication authentication) {
        try {
            Invoice invoice = invoiceService.findById(id)
                    .orElseThrow(() -> new RuntimeException("Invoice not found"));
            AppUser admin = userRepository.findByEmail(authentication.getName())
                    .orElseThrow(() -> new RuntimeException("Admin not found"));
            messageService.sendMessage(invoice, admin, message);
            return "redirect:/admin/invoices/" + id + "/messages";
        } catch (Exception e) {
            log.error("Error sending admin reply for invoice {}", id, e);
            return "redirect:/admin/invoices/" + id + "/messages?error=" + e.getMessage();
        }
    }
}