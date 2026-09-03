package com.enterprise.api;

import com.enterprise.tax.TaxRule;
import com.enterprise.tax.TaxRuleRepository;
import com.enterprise.tax.TaxService;
import com.enterprise.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/admin/tax")
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MERCHANT_ADMIN')")
public class AdminTaxController {

    private static final Logger log = LoggerFactory.getLogger(AdminTaxController.class);

    private final TaxRuleRepository taxRuleRepository;
    private final TaxService taxService;

    public AdminTaxController(TaxRuleRepository taxRuleRepository,
                              TaxService taxService) {
        this.taxRuleRepository = taxRuleRepository;
        this.taxService = taxService;
    }

    @GetMapping
    public String listRules(Model model) {
        Long tenantId = TenantContext.getRequiredTenantId();
        List<TaxRule> rules = taxRuleRepository.findByTenantIdAndActiveTrue(tenantId);
        model.addAttribute("rules", rules);
        return "admin/tax/list";
    }

    @GetMapping("/create")
    public String showCreateForm(Model model) {
        model.addAttribute("rule", new TaxRule());
        return "admin/tax/form";
    }

    @PostMapping
    public String createRule(@ModelAttribute TaxRule rule, RedirectAttributes redirectAttributes) {
        try {
            Long tenantId = TenantContext.getRequiredTenantId();
            rule.setTenantId(tenantId);
            taxRuleRepository.save(rule);
            redirectAttributes.addFlashAttribute("success", "Tax rule created successfully");
        } catch (Exception e) {
            log.error("Error creating tax rule", e);
            redirectAttributes.addFlashAttribute("error", "Failed to create tax rule: " + e.getMessage());
        }
        return "redirect:/admin/tax";
    }

    @PostMapping("/{id}/delete")
    public String deleteRule(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            taxRuleRepository.deleteById(id);
            redirectAttributes.addFlashAttribute("success", "Tax rule deleted successfully");
        } catch (Exception e) {
            log.error("Error deleting tax rule", e);
            redirectAttributes.addFlashAttribute("error", "Failed to delete tax rule: " + e.getMessage());
        }
        return "redirect:/admin/tax";
    }

    @PostMapping("/{id}/toggle")
    public String toggleRule(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            TaxRule rule = taxRuleRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Tax rule not found"));
            rule.setActive(!rule.isActive());
            rule.setUpdatedAt(java.time.LocalDateTime.now());
            taxRuleRepository.save(rule);
            redirectAttributes.addFlashAttribute("success", "Tax rule toggled successfully");
        } catch (Exception e) {
            log.error("Error toggling tax rule", e);
            redirectAttributes.addFlashAttribute("error", "Failed to toggle tax rule: " + e.getMessage());
        }
        return "redirect:/admin/tax";
    }
}