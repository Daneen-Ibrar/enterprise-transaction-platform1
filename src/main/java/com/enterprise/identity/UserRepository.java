package com.enterprise.identity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByEmail(String email);

    List<AppUser> findByRolesName(String roleName);

    @Query(value = "SELECT * FROM app_user", nativeQuery = true)
    List<AppUser> findAllWithoutTenantFilter();

    Optional<AppUser> findByEmailAndSuperAdminTrue(String email);

    // ----- LOGIN LOCK METHODS -----
    List<AppUser> findByAccountLockedTrueAndLockExpiryBefore(LocalDateTime now);

    @Modifying
    @Transactional
    @Query("UPDATE AppUser u SET u.failedLoginAttempts = 0, u.accountLocked = false, u.lockExpiry = null WHERE u.id = :userId")
    void resetLockAndAttempts(@Param("userId") Long userId);

    @Modifying
    @Transactional
    @Query("UPDATE AppUser u SET u.failedLoginAttempts = :count WHERE u.id = :userId")
    void updateFailedAttempts(@Param("userId") Long userId, @Param("count") int count);
}