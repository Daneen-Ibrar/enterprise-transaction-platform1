package com.enterprise.api;

import com.enterprise.reliability.*;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminReliabilityController {

    private final DlqEntryRepository dlqEntryRepository;
    private final RetryPolicyRepository retryPolicyRepository;
    private final CircuitBreakerPolicyRepository circuitBreakerPolicyRepository;
    private final DlqProcessorService dlqProcessorService;
    private final DlqScheduler dlqScheduler;

    public AdminReliabilityController(DlqEntryRepository dlqEntryRepository,
                                      RetryPolicyRepository retryPolicyRepository,
                                      CircuitBreakerPolicyRepository circuitBreakerPolicyRepository,
                                      DlqProcessorService dlqProcessorService,
                                      DlqScheduler dlqScheduler) {
        this.dlqEntryRepository = dlqEntryRepository;
        this.retryPolicyRepository = retryPolicyRepository;
        this.circuitBreakerPolicyRepository = circuitBreakerPolicyRepository;
        this.dlqProcessorService = dlqProcessorService;
        this.dlqScheduler = dlqScheduler;
    }

    // ----- DLQ list UI -----
    @GetMapping("/dlq")
    @PreAuthorize("hasPermission(null, 'reliability:view')")
    public String getDlqEntries(Model model) {
        List<DlqEntry> entries = dlqEntryRepository.findAll();
        model.addAttribute("entries", entries);
        return "admin/dlq";
    }

    // ----- Manual retry of a single DLQ entry (uses processor) -----
    @PostMapping("/dlq/{id}/retry")
    @PreAuthorize("hasPermission(null, 'reliability:retry')")
    public String retryDlq(@PathVariable Long id) {
        try {
            dlqProcessorService.retryDlq(id);
            return "DLQ entry " + id + " retried successfully.";
        } catch (Exception e) {
            return "Failed to retry DLQ entry " + id + ": " + e.getMessage();
        }
    }

    // ----- Manual trigger for scheduled processing -----
    @PostMapping("/dlq/process")
    @PreAuthorize("hasPermission(null, 'reliability:retry')")
    @ResponseBody
    public String triggerDlqProcessing() {
        dlqScheduler.processDlq();
        return "Scheduled DLQ processing triggered manually.";
    }

    // ----- Combined policy view (shows both retry and circuit breaker policies) -----
    @GetMapping("/policies")
    @PreAuthorize("hasPermission(null, 'reliability:view')")
    public String viewPolicies(Model model) {
        model.addAttribute("retryPolicies", retryPolicyRepository.findAll());
        model.addAttribute("circuitBreakerPolicies", circuitBreakerPolicyRepository.findAll());
        return "admin/reliability-policies";
    }

    // ===== SpEL Test Endpoint (with auto‑fix) =====
    @PostMapping("/test-expression")
    @ResponseBody
    public Map<String, Object> testExpression(@RequestParam String expression,
                                              @RequestParam String policyType) {
        Map<String, Object> result = new HashMap<>();
        try {
            // Auto‑fix common mistake: if expression doesn't start with '#', add it
            String fixedExpression = expression.trim();
            if (!fixedExpression.startsWith("#")) {
                fixedExpression = "#" + fixedExpression;
            }

            // Use StandardEvaluationContext (allows method calls)
            EvaluationContext context = new StandardEvaluationContext();
            switch (policyType) {
                case "RETRY":
                case "CIRCUIT_BREAKER":
                    context.setVariable("message", "Connection timed out");
                    break;
                case "APPROVAL":
                    context.setVariable("amount", 1000);
                    context.setVariable("description", "Urgent invoice");
                    context.setVariable("customerEmail", "test@example.com");
                    context.setVariable("merchantId", 1L);
                    break;
                case "REFUND":
                    context.setVariable("amount", 500);
                    context.setVariable("customerId", 1L);
                    context.setVariable("merchantId", 1L);
                    break;
                default:
                    context.setVariable("amount", 1000);
                    context.setVariable("description", "Test");
            }
            ExpressionParser parser = new SpelExpressionParser();
            Boolean value = parser.parseExpression(fixedExpression).getValue(context, Boolean.class);
            result.put("valid", true);
            result.put("result", value != null && value);
            result.put("message", "✅ Expression evaluated to " + (value != null && value) + " (auto‑fixed: " + fixedExpression + ")");
        } catch (Exception e) {
            result.put("valid", false);
            result.put("message", "❌ Error: " + e.getMessage());
        }
        return result;
    }
}