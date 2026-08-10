package com.enterprise.api;

import com.enterprise.reliability.CircuitBreakerState;
import com.enterprise.reliability.ReliabilityDashboardService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/admin/reliability-dashboard")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminReliabilityDashboardController {

    private final ReliabilityDashboardService dashboardService;

    public AdminReliabilityDashboardController(ReliabilityDashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping
    public String dashboard(Model model) {
        model.addAttribute("dlqSummary", dashboardService.getDlqSummary());
        model.addAttribute("circuitBreakers", dashboardService.getCircuitBreakerStates());
        model.addAttribute("lastReconciliation", dashboardService.getLastReconciliation());
        model.addAttribute("auditChainValid", dashboardService.isAuditChainValid());
        model.addAttribute("openCircuits", dashboardService.getTotalOpenCircuits());
        return "admin/reliability-dashboard";
    }

    // ===== HTMX endpoints returning HTML fragments =====

    @GetMapping("/dlq-widget")
    public String dlqWidget(Model model) {
        model.addAttribute("dlqSummary", dashboardService.getDlqSummary());
        return "admin/fragments/reliability-dashboard :: dlq-widget";
    }

    @GetMapping("/cb-widget")
    public String cbWidget(Model model) {
        model.addAttribute("circuitBreakers", dashboardService.getCircuitBreakerStates());
        model.addAttribute("openCircuits", dashboardService.getTotalOpenCircuits());
        return "admin/fragments/reliability-dashboard :: cb-widget";
    }

    @GetMapping("/reconciliation-widget")
    public String reconciliationWidget(Model model) {
        model.addAttribute("lastReconciliation", dashboardService.getLastReconciliation());
        return "admin/fragments/reliability-dashboard :: reconciliation-widget";
    }

    @GetMapping("/audit-widget")
    public String auditWidget(Model model) {
        model.addAttribute("auditChainValid", dashboardService.isAuditChainValid());
        return "admin/fragments/reliability-dashboard :: audit-widget";
    }

    // ===== "Refresh All" endpoint: updates all widgets and returns a combined fragment (or simply redirects) =====
    @GetMapping("/refresh-all")
    public String refreshAll(Model model) {
        // Re-fetch data
        model.addAttribute("dlqSummary", dashboardService.getDlqSummary());
        model.addAttribute("circuitBreakers", dashboardService.getCircuitBreakerStates());
        model.addAttribute("lastReconciliation", dashboardService.getLastReconciliation());
        model.addAttribute("auditChainValid", dashboardService.isAuditChainValid());
        model.addAttribute("openCircuits", dashboardService.getTotalOpenCircuits());
        return "admin/reliability-dashboard :: dashboard-grid";
    }

    // ===== Re-verify audit chain =====
    @GetMapping("/reverify-audit")
    public String reverifyAudit(Model model, RedirectAttributes redirectAttributes) {
        boolean valid = dashboardService.isAuditChainValid();
        model.addAttribute("auditChainValid", valid);
        // Return the audit widget fragment
        return "admin/fragments/reliability-dashboard :: audit-widget";
    }
}