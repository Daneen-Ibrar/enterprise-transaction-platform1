package com.enterprise.security;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface BackupCodeRepository extends JpaRepository<BackupCode, Long> {

    List<BackupCode> findByUserIdAndUsedFalse(Long userId);

    Optional<BackupCode> findByUserIdAndCodeHash(Long userId, String codeHash);

    @Modifying
    @Transactional
    @Query("DELETE FROM BackupCode bc WHERE bc.userId = :userId")
    void deleteByUserId(@Param("userId") Long userId);

    long countByUserIdAndUsedFalse(Long userId);

    @Modifying
    @Transactional
    @Query("UPDATE BackupCode bc SET bc.used = true WHERE bc.id = :id")
    void markAsUsed(@Param("id") Long id);
}