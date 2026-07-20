package com.enterprise.api;

import com.enterprise.reliability.CircuitBreakerPolicy;
import com.enterprise.reliability.CircuitBreakerPolicyRepository;
import com.enterprise.reliability.DlqEntry;
import com.enterprise.reliability.DlqEntryRepository;
import com.enterprise.reliability.DlqProcessorService;
import com.enterprise.reliability.DlqScheduler;
import com.enterprise.reliability.RetryPolicy;
import com.enterprise.reliability.RetryPolicyRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequestMapping("/admin/reliability")
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

    // ----- Policy views -----
    @GetMapping("/policies/retry")
    @PreAuthorize("hasPermission(null, 'reliability:view')")
    @ResponseBody
    public List<RetryPolicy> getRetryPolicies() {
        return retryPolicyRepository.findAll();
    }

    @GetMapping("/policies/circuit-breaker")
    @PreAuthorize("hasPermission(null, 'reliability:view')")
    @ResponseBody
    public List<CircuitBreakerPolicy> getCircuitBreakerPolicies() {
        return circuitBreakerPolicyRepository.findAll();
    }

    @GetMapping("/policies")
    @PreAuthorize("hasPermission(null, 'reliability:view')")
    public String viewPolicies(Model model) {
        model.addAttribute("retryPolicies", retryPolicyRepository.findAll());
        model.addAttribute("circuitBreakerPolicies", circuitBreakerPolicyRepository.findAll());
        return "admin/reliability-policies";
    }
}