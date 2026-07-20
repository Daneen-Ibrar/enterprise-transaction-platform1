package com.enterprise.reliability;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface RetryPolicyRepository extends JpaRepository<RetryPolicy, Long> {
    Optional<RetryPolicy> findByOperationTypeAndActiveTrue(String operationType);
}