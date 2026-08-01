package com.enterprise.invoice;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SuspicionRuleRepository extends JpaRepository<SuspicionRule, Long> {
    List<SuspicionRule> findByActiveTrueOrderByPriorityAsc();
}