package com.enterprise.refund;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface RefundRuleRepository extends JpaRepository<RefundRule, Long> {
    List<RefundRule> findByActiveTrueOrderByRulePriorityAsc();
}