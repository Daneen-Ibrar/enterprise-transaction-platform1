package com.enterprise.audit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface RuleAuditRepository extends JpaRepository<RuleAudit, Long> {

    List<RuleAudit> findByRuleTypeAndRuleIdOrderByCreatedAtDesc(String ruleType, Long ruleId);

    List<RuleAudit> findAllByOrderByCreatedAtDesc();

    @Query("SELECT a FROM RuleAudit a WHERE a.ruleType = :ruleType ORDER BY a.createdAt DESC")
    List<RuleAudit> findByRuleTypeOrderByCreatedAtDesc(@Param("ruleType") String ruleType);

    @Query("SELECT a FROM RuleAudit a WHERE a.changedBy = :userId ORDER BY a.createdAt DESC")
    List<RuleAudit> findByChangedByOrderByCreatedAtDesc(@Param("userId") Long userId);
}