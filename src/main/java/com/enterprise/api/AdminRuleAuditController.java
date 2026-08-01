package com.enterprise.api;

import com.enterprise.audit.RuleAudit;
import com.enterprise.audit.RuleAuditService;
import com.enterprise.identity.AppUser;
import com.enterprise.identity.UserRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Controller
@RequestMapping("/admin/rule-audit")
@PreAuthorize("hasRole('ADMIN')")
public class AdminRuleAuditController {

    private final RuleAuditService ruleAuditService;
    private final UserRepository userRepository;

    public AdminRuleAuditController(RuleAuditService ruleAuditService, UserRepository userRepository) {
        this.ruleAuditService = ruleAuditService;
        this.userRepository = userRepository;
    }

    @GetMapping
    public String listAudits(@RequestParam(required = false) String ruleType,
                             Model model,
                             Authentication authentication) {
        List<RuleAudit> audits;

        if (ruleType != null && !ruleType.isEmpty()) {
            audits = ruleAuditService.getAuditsByRuleType(ruleType);
            model.addAttribute("selectedRuleType", ruleType);
        } else {
            audits = ruleAuditService.getAllAudits();
        }

        // Enrich with user emails
        for (RuleAudit audit : audits) {
            userRepository.findById(audit.getChangedBy())
                    .ifPresent(user -> audit.setChangedByEmail(user.getEmail()));
        }

        List<String> ruleTypes = List.of("APPROVAL", "REFUND", "SUSPICION");

        model.addAttribute("audits", audits);
        model.addAttribute("ruleTypes", ruleTypes);

        return "admin/rule-audit/list";
    }
}