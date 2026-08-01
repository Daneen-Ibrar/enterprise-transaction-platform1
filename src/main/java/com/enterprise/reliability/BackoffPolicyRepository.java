package com.enterprise.reliability;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface BackoffPolicyRepository extends JpaRepository<BackoffPolicy, Long> {
    List<BackoffPolicy> findByRetryPolicyId(Long retryPolicyId);
}