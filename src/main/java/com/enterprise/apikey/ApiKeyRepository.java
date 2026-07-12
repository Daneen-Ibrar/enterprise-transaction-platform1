package com.enterprise.apikey;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface ApiKeyRepository extends JpaRepository<ApiKey, Long> {

    // Explicit JPQL query using field names
    @Query("SELECT k FROM ApiKey k WHERE k.keyValue = :keyValue AND k.active = true")
    Optional<ApiKey> findActiveByKeyValue(@Param("keyValue") String keyValue);

    // Alternative: native SQL (if JPQL fails)
    @Query(value = "SELECT * FROM api_key WHERE key_value = :keyValue AND active = true", nativeQuery = true)
    Optional<ApiKey> findActiveByKeyValueNative(@Param("keyValue") String keyValue);

    List<ApiKey> findByUserId(Long userId);
}