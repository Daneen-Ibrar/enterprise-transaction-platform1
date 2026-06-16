package com.enterprise.reliability;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface CircuitBreakerPolicyRepository extends JpaRepository<CircuitBreakerPolicy, Long> {
    Optional<CircuitBreakerPolicy> findByOperationTypeAndActiveTrue(String operationType);
}