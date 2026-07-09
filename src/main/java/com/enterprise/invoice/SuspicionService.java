package com.enterprise.invoice;

import com.enterprise.feature.FeatureFlagService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SuspicionService {

    private static final Logger log = LoggerFactory.getLogger(SuspicionService.class);

    private final SuspicionRuleRepository ruleRepository;
    private final FeatureFlagService featureFlagService;   // <-- ADDED
    private final ExpressionParser parser = new SpelExpressionParser();

    public SuspicionService(SuspicionRuleRepository ruleRepository,
                            FeatureFlagService featureFlagService) {   // <-- ADDED
        this.ruleRepository = ruleRepository;
        this.featureFlagService = featureFlagService;
    }

    public SuspicionResult evaluate(Invoice invoice) {
        // ----- FEATURE FLAG CHECK -----
        if (!featureFlagService.isEnabled("SUSPICION_DETECTION")) {
            log.debug("Suspicion detection is disabled – returning GREEN");
            return new SuspicionResult("GREEN", "Suspicion detection disabled");
        }

        log.info("=== Suspicion Evaluation ===");
        log.info("Invoice ID: {}", invoice.getId());
        log.info("Description: '{}'", invoice.getDescription());
        log.info("Amount: {}", invoice.getAmount());

        List<SuspicionRule> rules = ruleRepository.findByActiveTrueOrderByPriorityAsc();
        log.info("Rules count: {}", rules.size());

        StandardEvaluationContext context = new StandardEvaluationContext();
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
            }
        }
        log.info("No matches – returning GREEN");
        return new SuspicionResult("GREEN", "Normal invoice");
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