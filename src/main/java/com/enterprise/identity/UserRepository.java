package com.enterprise.identity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByEmail(String email);

    @Query("SELECT u FROM AppUser u JOIN u.roles r WHERE r.name = :roleName")
    List<AppUser> findByRolesName(@Param("roleName") String roleName);
}