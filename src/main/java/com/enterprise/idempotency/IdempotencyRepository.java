package com.enterprise.idempotency;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface IdempotencyRepository extends JpaRepository<IdempotencyKey, String> {
    Optional<IdempotencyKey> findById(String key);
}