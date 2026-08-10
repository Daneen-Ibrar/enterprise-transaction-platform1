package com.enterprise.invoice;

import com.enterprise.tenant.TenantContext; // 👈 ADD THIS IMPORT
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.SimpleEvaluationContext;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ApprovalService {

    private static final Logger log = LoggerFactory.getLogger(ApprovalService.class);

    private final ApprovalRuleRepository ruleRepository;
    private final ExpressionParser parser = new SpelExpressionParser();

    public ApprovalService(ApprovalRuleRepository ruleRepository) {
        this.ruleRepository = ruleRepository;
    }

    public boolean evaluate(Invoice invoice) {
        Long tenantId = TenantContext.getRequiredTenantId(); // ✅ now works
        List<ApprovalRule> rules = ruleRepository.findByTenantIdAndActiveTrue(tenantId);

        // ✅ Use SimpleEvaluationContext – safe, read-only, no method calls
        EvaluationContext context = SimpleEvaluationContext.forReadOnlyDataBinding().build();
        context.setVariable("amount", invoice.getAmount());
        context.setVariable("description", invoice.getDescription());
        context.setVariable("customerEmail", invoice.getCustomerEmail());
        context.setVariable("merchantId", invoice.getMerchantId());

        for (ApprovalRule rule : rules) {
            try {
                Boolean matches = parser.parseExpression(rule.getConditionExpression())
                        .getValue(context, Boolean.class);
                if (Boolean.TRUE.equals(matches)) {
                    return rule.isRequiresApproval();
                }
            } catch (Exception e) {
                log.warn("Failed to evaluate approval rule {} (expr: {}): {}",
                        rule.getId(), rule.getConditionExpression(), e.getMessage());
                // Fail secure: if rule evaluation fails, treat as requiring approval
                return true;
            }
        }
        // Default: require approval if no rule matches (fail secure)
        return true;
    }
}