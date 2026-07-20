package com.enterprise.apikey;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ApiKeyRepository extends JpaRepository<ApiKey, Long> {

    @Query("SELECT k FROM ApiKey k WHERE k.active = true AND (k.expiresAt IS NULL OR k.expiresAt > CURRENT_TIMESTAMP)")
    List<ApiKey> findActiveKeys();

    List<ApiKey> findByUserId(Long userId);

    Optional<ApiKey> findByKeyHash(String keyHash);

    Optional<ApiKey> findByKeyPrefix(String keyPrefix);
}