package com.enterprise.api;

import com.enterprise.refund.RefundRule;
import com.enterprise.refund.RefundRuleRepository;
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
@RequestMapping("/admin/refund-rules")
@PreAuthorize("hasRole('ADMIN')")
public class AdminRefundRuleController {

    private static final Logger log = LoggerFactory.getLogger(AdminRefundRuleController.class);
    private final RefundRuleRepository ruleRepository;

    public AdminRefundRuleController(RefundRuleRepository ruleRepository) {
        this.ruleRepository = ruleRepository;
    }

    // ----- List all rules with threshold -----
    @GetMapping
    public String listRules(@RequestParam(required = false) String search, Model model) {
        List<RefundRule> rules = ruleRepository.findAll();
        if (search != null && !search.isEmpty()) {
            String lowerSearch = search.toLowerCase();
            rules = rules.stream()
                    .filter(r -> r.getConditionExpression() != null && r.getConditionExpression().toLowerCase().contains(lowerSearch))
                    .toList();
        }
        model.addAttribute("rules", rules);
        model.addAttribute("search", search);

        // Extract current threshold from the "ALLOW" rule (if any)
        BigDecimal threshold = null;
        for (RefundRule rule : rules) {
            if ("ALLOW".equals(rule.getAction()) && rule.getConditionExpression().contains("amount")) {
                String expr = rule.getConditionExpression();
                // Handle expressions like "amount <= 1000" or "amount < 1000"
                String[] parts = expr.replace("amount", "").trim().split("\\s+");
                if (parts.length >= 2) {
                    try {
                        threshold = new BigDecimal(parts[1]);
                        break;
                    } catch (NumberFormatException ignored) {}
                }
            }
        }
        model.addAttribute("threshold", threshold != null ? threshold : BigDecimal.valueOf(1000));
        return "admin/refund-rules/list";
    }

    // ----- Quick threshold settings -----
   @PostMapping("/settings")
public String saveSettings(@RequestParam BigDecimal threshold,
                           RedirectAttributes redirectAttributes) {
    try {
        ruleRepository.deleteAll();

        RefundRule allowRule = new RefundRule();
        allowRule.setRulePriority(1);
        allowRule.setConditionExpression("#amount <= " + threshold);   // ✅ fixed
        allowRule.setAction("ALLOW");
        allowRule.setActive(true);
        ruleRepository.save(allowRule);

        RefundRule denyRule = new RefundRule();
        denyRule.setRulePriority(2);
        denyRule.setConditionExpression("#amount > " + threshold);    // ✅ fixed
        denyRule.setAction("DENY");
        denyRule.setActive(true);
        ruleRepository.save(denyRule);

        redirectAttributes.addFlashAttribute("success",
                "Refund threshold updated. Amounts up to £" + threshold + " are refundable.");
    } catch (Exception e) {
        log.error("Error saving refund threshold", e);
        redirectAttributes.addFlashAttribute("error", "Failed to save threshold: " + e.getMessage());
    }
    return "redirect:/admin/refund-rules";
}
    // ----- Create rule (full form) -----
    @GetMapping("/create")
    public String showCreateForm(Model model) {
        model.addAttribute("rule", new RefundRule());
        return "admin/refund-rules/create";
    }

    @PostMapping
    public String createRule(@ModelAttribute RefundRule rule,
                             RedirectAttributes redirectAttributes) {
        try {
            ruleRepository.save(rule);
            redirectAttributes.addFlashAttribute("success", "Refund rule created.");
        } catch (Exception e) {
            log.error("Error creating refund rule", e);
            redirectAttributes.addFlashAttribute("error", "Failed to create rule: " + e.getMessage());
        }
        return "redirect:/admin/refund-rules";
    }

    // ----- Edit rule -----
    @GetMapping("/{id}/edit")
    public String showEditForm(@PathVariable Long id, Model model) {
        RefundRule rule = ruleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Rule not found"));
        model.addAttribute("rule", rule);
        return "admin/refund-rules/edit";
    }

    @PostMapping("/{id}")
    public String updateRule(@PathVariable Long id,
                             @ModelAttribute RefundRule rule,
                             RedirectAttributes redirectAttributes) {
        try {
            rule.setId(id);
            ruleRepository.save(rule);
            redirectAttributes.addFlashAttribute("success", "Refund rule updated.");
        } catch (Exception e) {
            log.error("Error updating refund rule", e);
            redirectAttributes.addFlashAttribute("error", "Failed to update rule: " + e.getMessage());
        }
        return "redirect:/admin/refund-rules";
    }

    // ----- Delete rule -----
    @PostMapping("/{id}/delete")
    public String deleteRule(@PathVariable Long id,
                             RedirectAttributes redirectAttributes) {
        try {
            ruleRepository.deleteById(id);
            redirectAttributes.addFlashAttribute("success", "Refund rule deleted.");
        } catch (Exception e) {
            log.error("Error deleting refund rule", e);
            redirectAttributes.addFlashAttribute("error", "Failed to delete rule: " + e.getMessage());
        }
        return "redirect:/admin/refund-rules";
    }

    // ----- Toggle active -----
    @PostMapping("/{id}/toggle")
    public String toggleRule(@PathVariable Long id,
                             RedirectAttributes redirectAttributes) {
        try {
            RefundRule rule = ruleRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Rule not found"));
            rule.setActive(!rule.isActive());
            ruleRepository.save(rule);
            redirectAttributes.addFlashAttribute("success",
                    rule.isActive() ? "Refund rule activated." : "Refund rule deactivated.");
        } catch (Exception e) {
            log.error("Error toggling refund rule", e);
            redirectAttributes.addFlashAttribute("error", "Failed to toggle rule: " + e.getMessage());
        }
        return "redirect:/admin/refund-rules";
    }
}