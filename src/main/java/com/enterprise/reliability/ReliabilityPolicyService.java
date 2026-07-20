package com.enterprise.reliability;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ReliabilityPolicyService {

    private final RetryPolicyRepository retryPolicyRepository;
    private final FailureClassificationRuleRepository ruleRepository;
    private final CircuitBreakerPolicyRepository cbPolicyRepository;
    private final CircuitBreakerStateRepository cbStateRepository;

    public ReliabilityPolicyService(RetryPolicyRepository retryPolicyRepository,
                                    FailureClassificationRuleRepository ruleRepository,
                                    CircuitBreakerPolicyRepository cbPolicyRepository,
                                    CircuitBreakerStateRepository cbStateRepository) {
        this.retryPolicyRepository = retryPolicyRepository;
        this.ruleRepository = ruleRepository;
        this.cbPolicyRepository = cbPolicyRepository;
        this.cbStateRepository = cbStateRepository;
    }

    public RetryPolicy getRetryPolicy(String operationType) {
        return retryPolicyRepository.findByOperationTypeAndActiveTrue(operationType)
                .orElseThrow(() -> new IllegalArgumentException("No retry policy for " + operationType));
    }

    public FailureClassificationRule classifyFailure(String operationType, Exception ex) {
        // Simple matching – in reality, you'd use a pattern or SpEL
        String message = ex.getMessage().toLowerCase();
        List<FailureClassificationRule> rules = ruleRepository.findByActiveTrueOrderByPriorityAsc();
        for (FailureClassificationRule rule : rules) {
            // Very simple: check if message contains the condition string
            if (message.contains(rule.getConditionExpression().toLowerCase())) {
                return rule;
            }
        }
        // default: treat as transient
        FailureClassificationRule defaultRule = new FailureClassificationRule();
        defaultRule.setAction("RETRY");
        defaultRule.setCategory("TRANSIENT");
        defaultRule.setSeverity("MEDIUM");
        return defaultRule;
    }

    public CircuitBreakerState getCircuitBreakerState(String operationType) {
        return cbStateRepository.findByOperationType(operationType)
                .orElseThrow(() -> new IllegalArgumentException("No circuit breaker state for " + operationType));
    }

    public CircuitBreakerPolicy getCircuitBreakerPolicy(String operationType) {
        return cbPolicyRepository.findByOperationTypeAndActiveTrue(operationType)
                .orElseThrow(() -> new IllegalArgumentException("No circuit breaker policy for " + operationType));
    }
}