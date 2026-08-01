package com.enterprise.reliability;

import com.enterprise.tenant.TenantContext;
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
        String message = ex.getMessage().toLowerCase();
        List<FailureClassificationRule> rules = ruleRepository.findByActiveTrueOrderByPriorityAsc();
        for (FailureClassificationRule rule : rules) {
            if (message.contains(rule.getConditionExpression().toLowerCase())) {
                return rule;
            }
        }
        FailureClassificationRule defaultRule = new FailureClassificationRule();
        defaultRule.setAction("RETRY");
        defaultRule.setCategory("TRANSIENT");
        defaultRule.setSeverity("MEDIUM");
        return defaultRule;
    }

    public CircuitBreakerState getCircuitBreakerState(String operationType) {
        Long tenantId = TenantContext.getRequiredTenantId();
        return cbStateRepository.findByOperationTypeAndTenantId(operationType, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("No circuit breaker state for " + operationType + " in tenant " + tenantId));
    }

    public CircuitBreakerPolicy getCircuitBreakerPolicy(String operationType) {
        Long tenantId = TenantContext.getRequiredTenantId();
        return cbPolicyRepository.findByOperationTypeAndTenantIdAndActiveTrue(operationType, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("No circuit breaker policy for " + operationType + " in tenant " + tenantId));
    }

    // Update methods (tenant-aware)
    public void updateCircuitBreakerState(String operationType, String state) {
        Long tenantId = TenantContext.getRequiredTenantId();
        cbStateRepository.updateState(operationType, tenantId, state);
    }

    public void incrementFailureCount(String operationType) {
        Long tenantId = TenantContext.getRequiredTenantId();
        cbStateRepository.incrementFailureCount(operationType, tenantId);
    }

    public void incrementSuccessCount(String operationType) {
        Long tenantId = TenantContext.getRequiredTenantId();
        cbStateRepository.incrementSuccessCount(operationType, tenantId);
    }

    public void resetCounts(String operationType) {
        Long tenantId = TenantContext.getRequiredTenantId();
        cbStateRepository.resetCounts(operationType, tenantId);
    }
}