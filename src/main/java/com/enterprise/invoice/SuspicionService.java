package com.enterprise.invoice;

import com.enterprise.feature.FeatureFlagService;
import com.enterprise.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.SimpleEvaluationContext;
import org.springframework.stereotype.Service;
import com.enterprise.events.SuspicionEnabledEvent;
import org.springframework.context.event.EventListener;

import java.util.List;

@Service
public class SuspicionService {

    private static final Logger log = LoggerFactory.getLogger(SuspicionService.class);

    private final SuspicionRuleRepository ruleRepository;
    private final FeatureFlagService featureFlagService;
    private final InvoiceService invoiceService;
    private final ExpressionParser parser = new SpelExpressionParser();

    public SuspicionService(SuspicionRuleRepository ruleRepository,
                            FeatureFlagService featureFlagService,
                            @Lazy InvoiceService invoiceService) {
        this.ruleRepository = ruleRepository;
        this.featureFlagService = featureFlagService;
        this.invoiceService = invoiceService;
    }

    @EventListener
public void onSuspicionEnabled(SuspicionEnabledEvent event) {
    log.info("📢 Received SuspicionEnabledEvent – re-evaluating all invoices");
    reEvaluateAllInvoices();
}

    public SuspicionResult evaluate(Invoice invoice) {
        if (!featureFlagService.isEnabled("SUSPICION_DETECTION")) {
            log.debug("Suspicion detection is disabled – returning GREEN");
            return new SuspicionResult("GREEN", "Suspicion detection disabled");
        }

        log.info("=== Suspicion Evaluation ===");
        log.info("Invoice ID: {}", invoice.getId());
        log.info("Description: '{}'", invoice.getDescription());
        log.info("Amount: {}", invoice.getAmount());

        Long tenantId = TenantContext.getRequiredTenantId();
        List<SuspicionRule> rules = ruleRepository.findByTenantIdAndActiveTrue(tenantId);
        log.info("Rules count: {}", rules.size());

        EvaluationContext context = SimpleEvaluationContext.forReadOnlyDataBinding().build();
        context.setVariable("amount", invoice.getAmount());
        context.setVariable("description", invoice.getDescription());
        context.setVariable("customerEmail", invoice.getCustomerEmail());
        context.setVariable("merchantId", invoice.getMerchantId());

        for (SuspicionRule rule : rules) {
            log.info("  Evaluating rule: {}", rule.getConditionExpression());
            try {
                Boolean matches = parser.parseExpression(rule.getConditionExpression())
                        .getValue(context, Boolean.class);
                log.info("    Matches: {}", matches);
                if (Boolean.TRUE.equals(matches)) {
                    log.info("    -> Returning {}", rule.getRiskLevel());
                    return new SuspicionResult(rule.getRiskLevel(), rule.getDescription());
                }
            } catch (Exception e) {
                log.error("    ERROR: {}", e.getMessage());
                return new SuspicionResult("GREEN", "Rule evaluation error – defaulting to GREEN");
            }
        }
        log.info("No matches – returning GREEN");
        return new SuspicionResult("GREEN", "Normal invoice");
    }

    public void reEvaluateAllInvoices() {
        // 👈 ADD THIS GUARD
        if (!featureFlagService.isEnabled("SUSPICION_DETECTION")) {
            log.info("Suspicion detection is disabled – skipping re-evaluation");
            return;
        }
        invoiceService.reEvaluateAllInvoicesForSuspicion();
    }

    public static class SuspicionResult {
        private final String riskLevel;
        private final String reason;

        public SuspicionResult(String riskLevel, String reason) {
            this.riskLevel = riskLevel;
            this.reason = reason;
        }
        public String getRiskLevel() { return riskLevel; }
        public String getReason() { return reason; }
    }
}