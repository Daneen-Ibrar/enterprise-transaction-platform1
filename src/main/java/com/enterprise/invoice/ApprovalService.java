package com.enterprise.invoice;

import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ApprovalService {

    private final ApprovalRuleRepository ruleRepository;
    private final ExpressionParser parser = new SpelExpressionParser();

    public ApprovalService(ApprovalRuleRepository ruleRepository) {
        this.ruleRepository = ruleRepository;
    }

    public boolean evaluate(Invoice invoice) {
        List<ApprovalRule> rules = ruleRepository.findByActiveTrueOrderByPriorityAsc();
        StandardEvaluationContext context = new StandardEvaluationContext();
        context.setVariable("amount", invoice.getAmount());
        context.setVariable("description", invoice.getDescription());
        context.setVariable("customerEmail", invoice.getCustomerEmail());
        context.setVariable("merchantId", invoice.getMerchantId());
        // Add more variables as needed (e.g., merchant risk level)

        for (ApprovalRule rule : rules) {
            try {
                Boolean matches = parser.parseExpression(rule.getConditionExpression())
                        .getValue(context, Boolean.class);
                if (Boolean.TRUE.equals(matches)) {
                    return rule.isRequiresApproval();
                }
            } catch (Exception ignored) {
                // Log if needed – fail safe: default to requiring approval
            }
        }
        // Default: require approval if no rule matches (fail secure)
        return true;
    }
}