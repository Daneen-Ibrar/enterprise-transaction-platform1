package com.enterprise.reliability;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface FailureClassificationRuleRepository extends JpaRepository<FailureClassificationRule, Long> {
    List<FailureClassificationRule> findByActiveTrueOrderByPriorityAsc();
}