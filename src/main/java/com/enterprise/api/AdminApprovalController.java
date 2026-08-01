package com.enterprise.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.enterprise.audit.RuleAuditService;
import com.enterprise.identity.AppUser;
import com.enterprise.identity.UserRepository;
import com.enterprise.invoice.ApprovalRule;
import com.enterprise.invoice.ApprovalRuleRepository;
import com.enterprise.invoice.InvoiceService;
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
@RequestMapping("/admin/approval-rules")
@PreAuthorize("hasRole('ADMIN')")
public class AdminApprovalController {

    private static final Logger log = LoggerFactory.getLogger(AdminApprovalController.class);
    private final ApprovalRuleRepository ruleRepository;
    private final InvoiceService invoiceService;
    private final RuleAuditService ruleAuditService;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    public AdminApprovalController(ApprovalRuleRepository ruleRepository,
                                   InvoiceService invoiceService,
                                   RuleAuditService ruleAuditService,
                                   UserRepository userRepository,
                                   ObjectMapper objectMapper) {
        this.ruleRepository = ruleRepository;
        this.invoiceService = invoiceService;
        this.ruleAuditService = ruleAuditService;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public String index(@RequestParam(required = false) String search, Model model) {
        List<ApprovalRule> rules = ruleRepository.findAll();
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
                               Authentication authentication,
                               RedirectAttributes redirectAttributes) {
        try {
            AppUser admin = userRepository.findByEmail(authentication.getName())
                    .orElseThrow(() -> new RuntimeException("Admin not found"));

            ruleRepository.deleteAll();

            ApprovalRule autoRule = new ApprovalRule();
            autoRule.setPriority(1);
            autoRule.setConditionExpression("#amount <= " + threshold);
            autoRule.setRequiresApproval(false);
            autoRule.setDescription("Amount £" + threshold + " or less – auto-approved");
            autoRule.setActive(true);
            // ✅ FIX: Set tenant ID – fail closed
            autoRule.setTenantId(TenantContext.getRequiredTenantId());
            autoRule = ruleRepository.save(autoRule);
            ruleAuditService.logChange("APPROVAL", autoRule.getId(), "CREATE", null, autoRule, admin.getId());

            ApprovalRule requireRule = new ApprovalRule();
            requireRule.setPriority(2);
            requireRule.setConditionExpression("#amount > " + threshold);
            requireRule.setRequiresApproval(true);
            requireRule.setDescription("Amount over £" + threshold + " – requires approval");
            requireRule.setActive(true);
            requireRule.setTenantId(TenantContext.getRequiredTenantId());
            requireRule = ruleRepository.save(requireRule);
            ruleAuditService.logChange("APPROVAL", requireRule.getId(), "CREATE", null, requireRule, admin.getId());

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
                             Authentication authentication,
                             RedirectAttributes redirectAttributes) {
        try {
            AppUser admin = userRepository.findByEmail(authentication.getName())
                    .orElseThrow(() -> new RuntimeException("Admin not found"));

            // ✅ FIX: Set tenant ID – fail closed
            rule.setTenantId(TenantContext.getRequiredTenantId());
            rule.setActive(true);
            ApprovalRule savedRule = ruleRepository.save(rule);
            ruleAuditService.logChange("APPROVAL", savedRule.getId(), "CREATE", null, savedRule, admin.getId());

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
                             Authentication authentication,
                             RedirectAttributes redirectAttributes) {
        try {
            AppUser admin = userRepository.findByEmail(authentication.getName())
                    .orElseThrow(() -> new RuntimeException("Admin not found"));

            ApprovalRule oldRule = ruleRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Rule not found"));

            rule.setId(id);
            rule.setCreatedAt(oldRule.getCreatedAt());
            // ✅ FIX: Set tenant ID – fail closed
            rule.setTenantId(TenantContext.getRequiredTenantId());
            ApprovalRule savedRule = ruleRepository.save(rule);
            ruleAuditService.logChange("APPROVAL", savedRule.getId(), "UPDATE", oldRule, savedRule, admin.getId());

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
                             Authentication authentication,
                             RedirectAttributes redirectAttributes) {
        try {
            AppUser admin = userRepository.findByEmail(authentication.getName())
                    .orElseThrow(() -> new RuntimeException("Admin not found"));

            ApprovalRule rule = ruleRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Rule not found"));
            ruleAuditService.logChange("APPROVAL", id, "DELETE", rule, null, admin.getId());

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
                             Authentication authentication,
                             RedirectAttributes redirectAttributes) {
        try {
            AppUser admin = userRepository.findByEmail(authentication.getName())
                    .orElseThrow(() -> new RuntimeException("Admin not found"));

            ApprovalRule rule = ruleRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Rule not found"));

            ApprovalRule oldRule = new ApprovalRule();
            oldRule.setId(rule.getId());
            oldRule.setPriority(rule.getPriority());
            oldRule.setConditionExpression(rule.getConditionExpression());
            oldRule.setRequiresApproval(rule.isRequiresApproval());
            oldRule.setDescription(rule.getDescription());
            oldRule.setActive(rule.isActive());

            rule.setActive(!rule.isActive());
            // ✅ FIX: Set tenant ID – fail closed
            rule.setTenantId(TenantContext.getRequiredTenantId());
            ApprovalRule savedRule = ruleRepository.save(rule);
            ruleAuditService.logChange("APPROVAL", savedRule.getId(), "TOGGLE", oldRule, savedRule, admin.getId());

            invoiceService.reEvaluateAllInvoices();
            redirectAttributes.addFlashAttribute("success",
                    rule.isActive() ? "Rule activated and all invoices re-evaluated." : "Rule deactivated and all invoices re-evaluated.");
        } catch (Exception e) {
            log.error("Error toggling rule", e);
            redirectAttributes.addFlashAttribute("error", "Failed to toggle rule: " + e.getMessage());
        }
        return "redirect:/admin/approval-rules";
    }

    // ===== EXPORT =====
    @GetMapping("/export")
    @ResponseBody
    public ResponseEntity<String> exportRules(Authentication authentication) {
        AppUser admin = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new RuntimeException("Admin not found"));
        Long tenantId = admin.getTenantId();
        List<ApprovalRule> rules = ruleRepository.findAll().stream()
                .filter(r -> r.getTenantId().equals(tenantId))
                .collect(Collectors.toList());
        try {
            String json = objectMapper.writeValueAsString(rules);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=approval-rules.json")
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
            List<ApprovalRule> importedRules = objectMapper.readValue(content,
                    new TypeReference<List<ApprovalRule>>() {});
            // Validate and set tenant
            for (ApprovalRule rule : importedRules) {
                rule.setId(null); // force new IDs
                rule.setTenantId(tenantId);
                rule.setCreatedAt(LocalDateTime.now());
            }
            // Delete existing rules for this tenant
            List<ApprovalRule> existing = ruleRepository.findAll().stream()
                    .filter(r -> r.getTenantId().equals(tenantId))
                    .collect(Collectors.toList());
            ruleRepository.deleteAll(existing);
            ruleRepository.saveAll(importedRules);
            invoiceService.reEvaluateAllInvoices();
            redirectAttributes.addFlashAttribute("success",
                    "Imported " + importedRules.size() + " approval rules.");
        } catch (Exception e) {
            log.error("Import failed", e);
            redirectAttributes.addFlashAttribute("error", "Import failed: " + e.getMessage());
        }
        return "redirect:/admin/approval-rules";
    }
}