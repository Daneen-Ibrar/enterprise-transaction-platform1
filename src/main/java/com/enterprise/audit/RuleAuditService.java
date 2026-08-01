package com.enterprise.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.enterprise.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class RuleAuditService {

    private static final Logger log = LoggerFactory.getLogger(RuleAuditService.class);

    private final RuleAuditRepository ruleAuditRepository;
    private final ObjectMapper objectMapper;

    public RuleAuditService(RuleAuditRepository ruleAuditRepository, ObjectMapper objectMapper) {
        this.ruleAuditRepository = ruleAuditRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void logChange(String ruleType, Long ruleId, String action,
                          Object oldValues, Object newValues, Long changedBy) {
        try {
            RuleAudit audit = new RuleAudit();
            audit.setRuleType(ruleType);
            audit.setRuleId(ruleId);
            audit.setAction(action);
            audit.setChangedBy(changedBy);

            if (oldValues != null) {
                audit.setOldValues(objectMapper.writeValueAsString(oldValues));
            }
            if (newValues != null) {
                audit.setNewValues(objectMapper.writeValueAsString(newValues));
            }

            ruleAuditRepository.save(audit);
            log.info("Rule audit logged: type={}, ruleId={}, action={}, user={}",
                    ruleType, ruleId, action, changedBy);
        } catch (Exception e) {
            log.error("Failed to log rule audit: {}", e.getMessage(), e);
            // Don't fail the main operation – just log the error
        }
    }

    public List<RuleAudit> getAllAudits() {
        return ruleAuditRepository.findAllByOrderByCreatedAtDesc();
    }

    public List<RuleAudit> getAuditsByRuleType(String ruleType) {
        return ruleAuditRepository.findByRuleTypeOrderByCreatedAtDesc(ruleType);
    }

    public List<RuleAudit> getAuditsByRule(String ruleType, Long ruleId) {
        return ruleAuditRepository.findByRuleTypeAndRuleIdOrderByCreatedAtDesc(ruleType, ruleId);
    }

    public List<RuleAudit> getAuditsByUser(Long userId) {
        return ruleAuditRepository.findByChangedByOrderByCreatedAtDesc(userId);
    }
}