package com.enterprise.identity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByEmail(String email);

    // Find users by role name (e.g., "ADMIN")
    List<AppUser> findByRolesName(String roleName);

    // Bypass tenant filter – admin sees all users
    @Query(value = "SELECT * FROM app_user", nativeQuery = true)
    List<AppUser> findAllWithoutTenantFilter();
}