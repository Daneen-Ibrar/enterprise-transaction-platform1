package com.enterprise.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.enterprise.audit.RuleAuditService;
import com.enterprise.identity.AppUser;
import com.enterprise.identity.UserRepository;
import com.enterprise.refund.RefundRule;
import com.enterprise.refund.RefundRuleRepository;
import com.enterprise.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/admin/refund-rules")
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MERCHANT_ADMIN')")
public class AdminRefundRuleController {

    private static final Logger log = LoggerFactory.getLogger(AdminRefundRuleController.class);
    private final RefundRuleRepository ruleRepository;
    private final RuleAuditService ruleAuditService;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    public AdminRefundRuleController(RefundRuleRepository ruleRepository,
                                     RuleAuditService ruleAuditService,
                                     UserRepository userRepository,
                                     ObjectMapper objectMapper) {
        this.ruleRepository = ruleRepository;
        this.ruleAuditService = ruleAuditService;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    private AppUser getCurrentAdmin(Authentication authentication) {
        return userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new RuntimeException("Admin not found"));
    }

    @GetMapping
    public String listRules(@RequestParam(required = false) String search, Model model, Authentication authentication) {
        Long tenantId = getCurrentAdmin(authentication).getTenantId();
        List<RefundRule> rules = ruleRepository.findAllByTenantId(tenantId);
        if (search != null && !search.isEmpty()) {
            String lowerSearch = search.toLowerCase();
            rules = rules.stream()
                    .filter(r -> r.getConditionExpression() != null && r.getConditionExpression().toLowerCase().contains(lowerSearch))
                    .toList();
        }
        model.addAttribute("rules", rules);
        model.addAttribute("search", search);

        BigDecimal threshold = null;
        for (RefundRule rule : rules) {
            if ("ALLOW".equals(rule.getAction()) && rule.getConditionExpression().contains("amount")) {
                String expr = rule.getConditionExpression();
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

    @PostMapping("/settings")
    public String saveSettings(@RequestParam BigDecimal threshold,
                               Authentication authentication,
                               RedirectAttributes redirectAttributes) {
        try {
            AppUser admin = userRepository.findByEmail(authentication.getName())
                    .orElseThrow(() -> new RuntimeException("Admin not found"));
            Long tenantId = admin.getTenantId();

            // ✅ Only delete rules for THIS tenant
            List<RefundRule> existing = ruleRepository.findAllByTenantId(tenantId);
            ruleRepository.deleteAll(existing);

            RefundRule allowRule = new RefundRule();
            allowRule.setRulePriority(1);
            allowRule.setConditionExpression("#amount <= " + threshold);
            allowRule.setAction("ALLOW");
            allowRule.setActive(true);
            allowRule.setTenantId(tenantId);
            allowRule = ruleRepository.save(allowRule);
            ruleAuditService.logChange("REFUND", allowRule.getId(), "CREATE", null, allowRule, admin.getId());

            RefundRule denyRule = new RefundRule();
            denyRule.setRulePriority(2);
            denyRule.setConditionExpression("#amount > " + threshold);
            denyRule.setAction("DENY");
            denyRule.setActive(true);
            denyRule.setTenantId(tenantId);
            denyRule = ruleRepository.save(denyRule);
            ruleAuditService.logChange("REFUND", denyRule.getId(), "CREATE", null, denyRule, admin.getId());

            redirectAttributes.addFlashAttribute("success",
                    "Refund threshold updated. Amounts up to £" + threshold + " are refundable.");
        } catch (Exception e) {
            log.error("Error saving refund threshold", e);
            redirectAttributes.addFlashAttribute("error", "Failed to save threshold: " + e.getMessage());
        }
        return "redirect:/admin/refund-rules";
    }

    @GetMapping("/create")
    public String showCreateForm(Model model) {
        model.addAttribute("rule", new RefundRule());
        return "admin/refund-rules/create";
    }

    @PostMapping
    public String createRule(@ModelAttribute RefundRule rule,
                             Authentication authentication,
                             RedirectAttributes redirectAttributes) {
        try {
            AppUser admin = userRepository.findByEmail(authentication.getName())
                    .orElseThrow(() -> new RuntimeException("Admin not found"));

            // ✅ FIX: Use required tenant ID – fail closed
            rule.setTenantId(TenantContext.getRequiredTenantId());
            rule.setActive(true);
            RefundRule savedRule = ruleRepository.save(rule);
            ruleAuditService.logChange("REFUND", savedRule.getId(), "CREATE", null, savedRule, admin.getId());

            redirectAttributes.addFlashAttribute("success", "Refund rule created.");
        } catch (Exception e) {
            log.error("Error creating refund rule", e);
            redirectAttributes.addFlashAttribute("error", "Failed to create rule: " + e.getMessage());
        }
        return "redirect:/admin/refund-rules";
    }

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
                             Authentication authentication,
                             RedirectAttributes redirectAttributes) {
        try {
            AppUser admin = userRepository.findByEmail(authentication.getName())
                    .orElseThrow(() -> new RuntimeException("Admin not found"));

            RefundRule oldRule = ruleRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Rule not found"));

            rule.setId(id);
            rule.setCreatedAt(oldRule.getCreatedAt());
            // ✅ FIX: Use required tenant ID – fail closed
            rule.setTenantId(TenantContext.getRequiredTenantId());
            RefundRule savedRule = ruleRepository.save(rule);
            ruleAuditService.logChange("REFUND", savedRule.getId(), "UPDATE", oldRule, savedRule, admin.getId());

            redirectAttributes.addFlashAttribute("success", "Refund rule updated.");
        } catch (Exception e) {
            log.error("Error updating refund rule", e);
            redirectAttributes.addFlashAttribute("error", "Failed to update rule: " + e.getMessage());
        }
        return "redirect:/admin/refund-rules";
    }

    @PostMapping("/{id}/delete")
    public String deleteRule(@PathVariable Long id,
                             Authentication authentication,
                             RedirectAttributes redirectAttributes) {
        try {
            AppUser admin = userRepository.findByEmail(authentication.getName())
                    .orElseThrow(() -> new RuntimeException("Admin not found"));

            RefundRule rule = ruleRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Rule not found"));
            ruleAuditService.logChange("REFUND", id, "DELETE", rule, null, admin.getId());

            ruleRepository.deleteById(id);
            redirectAttributes.addFlashAttribute("success", "Refund rule deleted.");
        } catch (Exception e) {
            log.error("Error deleting refund rule", e);
            redirectAttributes.addFlashAttribute("error", "Failed to delete rule: " + e.getMessage());
        }
        return "redirect:/admin/refund-rules";
    }

    @PostMapping("/{id}/toggle")
    public String toggleRule(@PathVariable Long id,
                             Authentication authentication,
                             RedirectAttributes redirectAttributes) {
        try {
            AppUser admin = userRepository.findByEmail(authentication.getName())
                    .orElseThrow(() -> new RuntimeException("Admin not found"));

            RefundRule rule = ruleRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Rule not found"));

            RefundRule oldRule = new RefundRule();
            oldRule.setId(rule.getId());
            oldRule.setRulePriority(rule.getRulePriority());
            oldRule.setConditionExpression(rule.getConditionExpression());
            oldRule.setAction(rule.getAction());
            oldRule.setRequiredPermission(rule.getRequiredPermission());
            oldRule.setActive(rule.isActive());

            rule.setActive(!rule.isActive());
            // ✅ FIX: Use required tenant ID – fail closed (preserve tenant)
            rule.setTenantId(TenantContext.getRequiredTenantId());
            RefundRule savedRule = ruleRepository.save(rule);
            ruleAuditService.logChange("REFUND", savedRule.getId(), "TOGGLE", oldRule, savedRule, admin.getId());

            redirectAttributes.addFlashAttribute("success",
                    rule.isActive() ? "Refund rule activated." : "Refund rule deactivated.");
        } catch (Exception e) {
            log.error("Error toggling refund rule", e);
            redirectAttributes.addFlashAttribute("error", "Failed to toggle rule: " + e.getMessage());
        }
        return "redirect:/admin/refund-rules";
    }

    // ===== EXPORT =====
    @GetMapping("/export")
    @ResponseBody
    public ResponseEntity<String> exportRules(Authentication authentication) {
        AppUser admin = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new RuntimeException("Admin not found"));
        Long tenantId = admin.getTenantId();
        List<RefundRule> rules = ruleRepository.findAllByTenantId(tenantId);
        try {
            String json = objectMapper.writeValueAsString(rules);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=refund-rules.json")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(json);
        } catch (JsonProcessingException e) {
            log.error("Export failed", e);
            return ResponseEntity.internalServerError().body("Export failed");
        }
    }

    // ===== IMPORT =====
    @PostMapping("/import")
    public String importRules(@RequestParam("file") MultipartFile file,
                              Authentication authentication,
                              RedirectAttributes redirectAttributes) {
        AppUser admin = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new RuntimeException("Admin not found"));
        Long tenantId = admin.getTenantId();
        try {
            String content = new String(file.getBytes(), StandardCharsets.UTF_8);
            List<RefundRule> importedRules = objectMapper.readValue(content,
                    new TypeReference<List<RefundRule>>() {});
            for (RefundRule rule : importedRules) {
                rule.setId(null);
                rule.setTenantId(tenantId);
                rule.setCreatedAt(LocalDateTime.now());
            }
            List<RefundRule> existing = ruleRepository.findAllByTenantId(tenantId);
            ruleRepository.deleteAll(existing);
            ruleRepository.saveAll(importedRules);
            redirectAttributes.addFlashAttribute("success",
                    "Imported " + importedRules.size() + " refund rules.");
        } catch (Exception e) {
            log.error("Import failed", e);
            redirectAttributes.addFlashAttribute("error", "Import failed: " + e.getMessage());
        }
        return "redirect:/admin/refund-rules";
    }
}