package com.enterprise.reliability;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface CircuitBreakerStateRepository extends JpaRepository<CircuitBreakerState, Long> {
    Optional<CircuitBreakerState> findByOperationType(String operationType);
}