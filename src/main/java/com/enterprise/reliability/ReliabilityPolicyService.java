package com.enterprise.reliability;

import com.enterprise.tenant.TenantContext;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ReliabilityPolicyService {

    private final RetryPolicyRepository retryPolicyRepository;
    private final CircuitBreakerPolicyRepository cbPolicyRepository;
    private final CircuitBreakerStateRepository cbStateRepository;
    private final FailureClassificationRuleRepository ruleRepository;

    public ReliabilityPolicyService(RetryPolicyRepository retryPolicyRepository,
                                    CircuitBreakerPolicyRepository cbPolicyRepository,
                                    CircuitBreakerStateRepository cbStateRepository,
                                    FailureClassificationRuleRepository ruleRepository) {
        this.retryPolicyRepository = retryPolicyRepository;
        this.cbPolicyRepository = cbPolicyRepository;
        this.cbStateRepository = cbStateRepository;
        this.ruleRepository = ruleRepository;
    }

    public RetryPolicy getRetryPolicy(String operationType) {
        Long tenantId = TenantContext.getRequiredTenantId();
        return retryPolicyRepository.findByOperationTypeAndActiveTrue(operationType)
                .orElseGet(() -> {
                    // Create default in-memory policy
                    RetryPolicy defaultPolicy = new RetryPolicy();
                    defaultPolicy.setOperationType(operationType);
                    defaultPolicy.setMaxAttempts(3);
                    defaultPolicy.setBackoffStrategy("EXPONENTIAL");
                    defaultPolicy.setBaseDelayMs(1000);
                    defaultPolicy.setMaxDelayMs(30000);
                    defaultPolicy.setJitterEnabled(true);
                    defaultPolicy.setActive(true);
                    return defaultPolicy;
                });
    }

    public CircuitBreakerState getCircuitBreakerState(String operationType) {
        Long tenantId = TenantContext.getRequiredTenantId();
        return cbStateRepository.findByOperationTypeAndTenantId(operationType, tenantId)
                .orElseGet(() -> {
                    // Create default state
                    CircuitBreakerState defaultState = new CircuitBreakerState();
                    defaultState.setOperationType(operationType);
                    defaultState.setState("CLOSED");
                    defaultState.setFailureCount(0);
                    defaultState.setSuccessCount(0);
                    defaultState.setUpdatedAt(LocalDateTime.now());
                    defaultState.setTenantId(tenantId);
                    return cbStateRepository.save(defaultState);
                });
    }

    public CircuitBreakerPolicy getCircuitBreakerPolicy(String operationType) {
        Long tenantId = TenantContext.getRequiredTenantId();
        return cbPolicyRepository.findByOperationTypeAndTenantIdAndActiveTrue(operationType, tenantId)
                .orElseGet(() -> {
                    // Create default policy
                    CircuitBreakerPolicy defaultPolicy = new CircuitBreakerPolicy();
                    defaultPolicy.setOperationType(operationType);
                    defaultPolicy.setFailureThreshold(5);
                    defaultPolicy.setSuccessThreshold(3);
                    defaultPolicy.setTimeoutMs(30000);
                    defaultPolicy.setEvaluationWindowSec(30);
                    defaultPolicy.setActive(true);
                    defaultPolicy.setTenantId(tenantId);
                    return cbPolicyRepository.save(defaultPolicy);
                });
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