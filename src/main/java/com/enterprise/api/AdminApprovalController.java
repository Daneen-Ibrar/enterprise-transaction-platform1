package com.enterprise.api;

import com.enterprise.invoice.ApprovalRule;
import com.enterprise.invoice.ApprovalRuleRepository;
import com.enterprise.invoice.InvoiceService;
import com.enterprise.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.List;

@Controller
@RequestMapping("/admin/approval-rules")
@PreAuthorize("hasRole('ADMIN')")
public class AdminApprovalController {

    private static final Logger log = LoggerFactory.getLogger(AdminApprovalController.class);
    private final ApprovalRuleRepository ruleRepository;
    private final InvoiceService invoiceService;

    public AdminApprovalController(ApprovalRuleRepository ruleRepository,
                                   InvoiceService invoiceService) {
        this.ruleRepository = ruleRepository;
        this.invoiceService = invoiceService;
    }

    @GetMapping
    public String index(@RequestParam(required = false) String search, Model model) {
        log.info("=== Loading approval rules page ===");
        List<ApprovalRule> rules = ruleRepository.findAll();
        log.info("Found {} rules", rules.size());

        if (search != null && !search.isEmpty()) {
            String lowerSearch = search.toLowerCase();
            rules = rules.stream()
                    .filter(r -> r.getDescription() != null && r.getDescription().toLowerCase().contains(lowerSearch))
                    .toList();
        }
        model.addAttribute("rules", rules);
        model.addAttribute("search", search);

        BigDecimal threshold = null;
        for (ApprovalRule rule : rules) {
            if (!rule.isRequiresApproval() && rule.getConditionExpression().contains("#amount")) {
                String expr = rule.getConditionExpression();
                String[] parts = expr.replace("#amount", "").trim().split("\\s+");
                if (parts.length >= 2) {
                    try {
                        threshold = new BigDecimal(parts[1]);
                        break;
                    } catch (NumberFormatException ignored) {}
                }
            }
        }
        model.addAttribute("threshold", threshold != null ? threshold : BigDecimal.valueOf(10000));
        return "admin/approval-rules/index";
    }

    @PostMapping("/settings")
    public String saveSettings(@RequestParam BigDecimal threshold,
                               RedirectAttributes redirectAttributes) {
        try {
            ruleRepository.deleteAll();

            ApprovalRule autoRule = new ApprovalRule();
            autoRule.setPriority(1);
            autoRule.setConditionExpression("#amount <= " + threshold);
            autoRule.setRequiresApproval(false);
            autoRule.setDescription("Amount £" + threshold + " or less – auto-approved");
            autoRule.setActive(true);
            // ----- FIX: Set tenant ID -----
            Long tenantId = TenantContext.getTenantId();
            autoRule.setTenantId(tenantId != null ? tenantId : 1L);
            ruleRepository.save(autoRule);

            ApprovalRule requireRule = new ApprovalRule();
            requireRule.setPriority(2);
            requireRule.setConditionExpression("#amount > " + threshold);
            requireRule.setRequiresApproval(true);
            requireRule.setDescription("Amount over £" + threshold + " – requires approval");
            requireRule.setActive(true);
            // ----- FIX: Set tenant ID -----
            requireRule.setTenantId(tenantId != null ? tenantId : 1L);
            ruleRepository.save(requireRule);

            invoiceService.reEvaluateAllInvoices();
            redirectAttributes.addFlashAttribute("success", "Threshold updated and all invoices re-evaluated.");
        } catch (Exception e) {
            log.error("Error saving threshold", e);
            redirectAttributes.addFlashAttribute("error", "Failed to save threshold: " + e.getMessage());
        }
        return "redirect:/admin/approval-rules";
    }

    @GetMapping("/create")
    public String showCreateForm(Model model) {
        model.addAttribute("rule", new ApprovalRule());
        return "admin/approval-rules/create";
    }

    @PostMapping
    public String createRule(@ModelAttribute ApprovalRule rule,
                             RedirectAttributes redirectAttributes) {
        try {
            // ----- FIX: Set tenant ID -----
            Long tenantId = TenantContext.getTenantId();
            rule.setTenantId(tenantId != null ? tenantId : 1L);
            ruleRepository.save(rule);
            invoiceService.reEvaluateAllInvoices();
            redirectAttributes.addFlashAttribute("success", "Rule created and all invoices re-evaluated.");
        } catch (Exception e) {
            log.error("Error creating rule", e);
            redirectAttributes.addFlashAttribute("error", "Failed to create rule: " + e.getMessage());
        }
        return "redirect:/admin/approval-rules";
    }

    @GetMapping("/{id}/edit")
    public String showEditForm(@PathVariable Long id, Model model) {
        ApprovalRule rule = ruleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Rule not found"));
        model.addAttribute("rule", rule);
        return "admin/approval-rules/edit";
    }

    @PostMapping("/{id}")
    public String updateRule(@PathVariable Long id,
                             @ModelAttribute ApprovalRule rule,
                             RedirectAttributes redirectAttributes) {
        try {
            rule.setId(id);
            ruleRepository.save(rule);
            invoiceService.reEvaluateAllInvoices();
            redirectAttributes.addFlashAttribute("success", "Rule updated and all invoices re-evaluated.");
        } catch (Exception e) {
            log.error("Error updating rule", e);
            redirectAttributes.addFlashAttribute("error", "Failed to update rule: " + e.getMessage());
        }
        return "redirect:/admin/approval-rules";
    }

    @PostMapping("/{id}/delete")
    public String deleteRule(@PathVariable Long id,
                             RedirectAttributes redirectAttributes) {
        try {
            ruleRepository.deleteById(id);
            invoiceService.reEvaluateAllInvoices();
            redirectAttributes.addFlashAttribute("success", "Rule deleted and all invoices re-evaluated.");
        } catch (Exception e) {
            log.error("Error deleting rule", e);
            redirectAttributes.addFlashAttribute("error", "Failed to delete rule: " + e.getMessage());
        }
        return "redirect:/admin/approval-rules";
    }

    @PostMapping("/{id}/toggle")
    public String toggleRule(@PathVariable Long id,
                             RedirectAttributes redirectAttributes) {
        try {
            ApprovalRule rule = ruleRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Rule not found"));
            rule.setActive(!rule.isActive());
            ruleRepository.save(rule);
            invoiceService.reEvaluateAllInvoices();
            redirectAttributes.addFlashAttribute("success",
                    rule.isActive() ? "Rule activated and all invoices re-evaluated." : "Rule deactivated and all invoices re-evaluated.");
        } catch (Exception e) {
            log.error("Error toggling rule", e);
            redirectAttributes.addFlashAttribute("error", "Failed to toggle rule: " + e.getMessage());
        }
        return "redirect:/admin/approval-rules";
    }
}