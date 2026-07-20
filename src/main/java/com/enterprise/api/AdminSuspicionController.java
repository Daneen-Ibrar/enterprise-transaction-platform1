package com.enterprise.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.enterprise.audit.RuleAuditService;
import com.enterprise.identity.AppUser;
import com.enterprise.identity.UserRepository;
import com.enterprise.invoice.SuspicionRule;
import com.enterprise.invoice.SuspicionRuleRepository;
import com.enterprise.invoice.SuspicionService;
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

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/admin/suspicion-rules")
@PreAuthorize("hasRole('ADMIN')")
public class AdminSuspicionController {

    private static final Logger log = LoggerFactory.getLogger(AdminSuspicionController.class);
    private final SuspicionRuleRepository ruleRepository;
    private final SuspicionService suspicionService;
    private final RuleAuditService ruleAuditService;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    public AdminSuspicionController(SuspicionRuleRepository ruleRepository,
                                    SuspicionService suspicionService,
                                    RuleAuditService ruleAuditService,
                                    UserRepository userRepository,
                                    ObjectMapper objectMapper) {
        this.ruleRepository = ruleRepository;
        this.suspicionService = suspicionService;
        this.ruleAuditService = ruleAuditService;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public String listRules(Model model) {
        List<SuspicionRule> rules = ruleRepository.findAll();
        model.addAttribute("rules", rules);
        return "admin/suspicion-rules/list";
    }

    @GetMapping("/create")
    public String showCreateForm(Model model) {
        model.addAttribute("rule", new SuspicionRule());
        return "admin/suspicion-rules/create";
    }

    @PostMapping
    public String createRule(@RequestParam String keyword,
                             @RequestParam String riskLevel,
                             @RequestParam(required = false) String description,
                             Authentication authentication,
                             RedirectAttributes redirectAttributes) {
        try {
            AppUser admin = userRepository.findByEmail(authentication.getName())
                    .orElseThrow(() -> new RuntimeException("Admin not found"));

            String condition = "#description.toLowerCase().contains(\"" + keyword.toLowerCase() + "\")";
            SuspicionRule rule = new SuspicionRule();
            rule.setPriority(100);
            rule.setConditionExpression(condition);
            rule.setRiskLevel(riskLevel);
            rule.setDescription(description != null ? description : "Keyword: " + keyword);
            rule.setActive(true);
            Long tenantId = TenantContext.getTenantId();
            rule.setTenantId(tenantId != null ? tenantId : 1L);
            SuspicionRule savedRule = ruleRepository.save(rule);
            ruleAuditService.logChange("SUSPICION", savedRule.getId(), "CREATE", null, savedRule, admin.getId());

            suspicionService.reEvaluateAllInvoices();
            redirectAttributes.addFlashAttribute("success", "Suspicion rule created and invoices re-evaluated.");
        } catch (Exception e) {
            log.error("Error creating suspicion rule", e);
            redirectAttributes.addFlashAttribute("error", "Failed to create rule: " + e.getMessage());
        }
        return "redirect:/admin/suspicion-rules";
    }

    @GetMapping("/{id}/edit")
    public String showEditForm(@PathVariable Long id, Model model) {
        SuspicionRule rule = ruleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Rule not found"));
        model.addAttribute("rule", rule);
        return "admin/suspicion-rules/edit";
    }

    @PostMapping("/{id}")
    public String updateRule(@PathVariable Long id,
                             @RequestParam(required = false) String keyword,
                             @RequestParam(required = false) String conditionExpression,
                             @RequestParam String riskLevel,
                             @RequestParam(required = false) String description,
                             @RequestParam(required = false) Boolean active,
                             Authentication authentication,
                             RedirectAttributes redirectAttributes) {
        try {
            AppUser admin = userRepository.findByEmail(authentication.getName())
                    .orElseThrow(() -> new RuntimeException("Admin not found"));

            SuspicionRule rule = ruleRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Rule not found"));

            SuspicionRule oldRule = new SuspicionRule();
            oldRule.setId(rule.getId());
            oldRule.setPriority(rule.getPriority());
            oldRule.setConditionExpression(rule.getConditionExpression());
            oldRule.setRiskLevel(rule.getRiskLevel());
            oldRule.setDescription(rule.getDescription());
            oldRule.setActive(rule.isActive());

            if (keyword != null && !keyword.trim().isEmpty()) {
                rule.setConditionExpression("#description.toLowerCase().contains(\"" + keyword.toLowerCase() + "\")");
            } else if (conditionExpression != null && !conditionExpression.trim().isEmpty()) {
                rule.setConditionExpression(conditionExpression);
            }

            rule.setRiskLevel(riskLevel);
            rule.setDescription(description);
            if (active != null) {
                rule.setActive(active);
            }
            SuspicionRule savedRule = ruleRepository.save(rule);
            ruleAuditService.logChange("SUSPICION", savedRule.getId(), "UPDATE", oldRule, savedRule, admin.getId());

            suspicionService.reEvaluateAllInvoices();
            redirectAttributes.addFlashAttribute("success", "Suspicion rule updated and invoices re-evaluated.");
        } catch (Exception e) {
            log.error("Error updating suspicion rule", e);
            redirectAttributes.addFlashAttribute("error", "Failed to update rule: " + e.getMessage());
        }
        return "redirect:/admin/suspicion-rules";
    }

    @PostMapping("/{id}/delete")
    public String deleteRule(@PathVariable Long id,
                             Authentication authentication,
                             RedirectAttributes redirectAttributes) {
        try {
            AppUser admin = userRepository.findByEmail(authentication.getName())
                    .orElseThrow(() -> new RuntimeException("Admin not found"));

            SuspicionRule rule = ruleRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Rule not found"));
            ruleAuditService.logChange("SUSPICION", id, "DELETE", rule, null, admin.getId());

            ruleRepository.deleteById(id);
            suspicionService.reEvaluateAllInvoices();
            redirectAttributes.addFlashAttribute("success", "Suspicion rule deleted and invoices re-evaluated.");
        } catch (Exception e) {
            log.error("Error deleting suspicion rule", e);
            redirectAttributes.addFlashAttribute("error", "Failed to delete rule: " + e.getMessage());
        }
        return "redirect:/admin/suspicion-rules";
    }

    @PostMapping("/{id}/toggle")
    public String toggleRule(@PathVariable Long id,
                             Authentication authentication,
                             RedirectAttributes redirectAttributes) {
        try {
            AppUser admin = userRepository.findByEmail(authentication.getName())
                    .orElseThrow(() -> new RuntimeException("Admin not found"));

            SuspicionRule rule = ruleRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Rule not found"));

            SuspicionRule oldRule = new SuspicionRule();
            oldRule.setId(rule.getId());
            oldRule.setPriority(rule.getPriority());
            oldRule.setConditionExpression(rule.getConditionExpression());
            oldRule.setRiskLevel(rule.getRiskLevel());
            oldRule.setDescription(rule.getDescription());
            oldRule.setActive(rule.isActive());

            rule.setActive(!rule.isActive());
            SuspicionRule savedRule = ruleRepository.save(rule);
            ruleAuditService.logChange("SUSPICION", savedRule.getId(), "TOGGLE", oldRule, savedRule, admin.getId());

            suspicionService.reEvaluateAllInvoices();
            redirectAttributes.addFlashAttribute("success",
                    rule.isActive() ? "Rule activated and invoices re-evaluated." : "Rule deactivated and invoices re-evaluated.");
        } catch (Exception e) {
            log.error("Error toggling suspicion rule", e);
            redirectAttributes.addFlashAttribute("error", "Failed to toggle rule: " + e.getMessage());
        }
        return "redirect:/admin/suspicion-rules";
    }

    // ===== EXPORT =====
    @GetMapping("/export")
    @ResponseBody
    public ResponseEntity<String> exportRules(Authentication authentication) {
        AppUser admin = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new RuntimeException("Admin not found"));
        Long tenantId = admin.getTenantId();
        List<SuspicionRule> rules = ruleRepository.findAll().stream()
                .filter(r -> r.getTenantId().equals(tenantId))
                .collect(Collectors.toList());
        try {
            String json = objectMapper.writeValueAsString(rules);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=suspicion-rules.json")
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
            List<SuspicionRule> importedRules = objectMapper.readValue(content,
                    new TypeReference<List<SuspicionRule>>() {});
            for (SuspicionRule rule : importedRules) {
                rule.setId(null);
                rule.setTenantId(tenantId);
                rule.setCreatedAt(LocalDateTime.now());
            }
            List<SuspicionRule> existing = ruleRepository.findAll().stream()
                    .filter(r -> r.getTenantId().equals(tenantId))
                    .collect(Collectors.toList());
            ruleRepository.deleteAll(existing);
            ruleRepository.saveAll(importedRules);
            suspicionService.reEvaluateAllInvoices();
            redirectAttributes.addFlashAttribute("success",
                    "Imported " + importedRules.size() + " suspicion rules.");
        } catch (Exception e) {
            log.error("Import failed", e);
            redirectAttributes.addFlashAttribute("error", "Import failed: " + e.getMessage());
        }
        return "redirect:/admin/suspicion-rules";
    }
}