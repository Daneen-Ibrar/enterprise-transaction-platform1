package com.enterprise.invoice;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ApprovalRuleRepository extends JpaRepository<ApprovalRule, Long> {
    List<ApprovalRule> findByActiveTrueOrderByPriorityAsc();
}