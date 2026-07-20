package com.enterprise.identity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.stream.Collectors;

@Service
public class DatabaseUserDetailsService implements UserDetailsService {

    private static final Logger log = LoggerFactory.getLogger(DatabaseUserDetailsService.class);

    private final UserRepository userRepository;

    public DatabaseUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        log.info("🔍 loadUserByUsername() called for: {}", email);

        AppUser appUser = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));

        log.info("   User found: {}, locked: {}, attempts: {}, superAdmin: {}",
                appUser.getEmail(), appUser.isAccountLocked(),
                appUser.getFailedLoginAttempts(), appUser.isSuperAdmin());

        // Check if account is disabled
        if (!appUser.isActive()) {
            log.warn("❌ Account disabled for user: {}", email);
            throw new DisabledException("Your account has been disabled by an administrator.");
        }

        // ----- LOCK CHECK (only for non‑super admins) -----
        if (!appUser.isSuperAdmin() && appUser.isAccountLocked() &&
                (appUser.getLockExpiry() == null || appUser.getLockExpiry().isAfter(LocalDateTime.now()))) {
            log.warn("🔒 Account locked for user: {}, expiry: {}", email, appUser.getLockExpiry());
            throw new LockedException("Your account has been locked due to too many failed login attempts.");
        }

        return User.builder()
                .username(appUser.getEmail())
                .password(appUser.getPasswordHash())
                .authorities(appUser.getRoles().stream()
                        .map(role -> new SimpleGrantedAuthority("ROLE_" + role.getName()))
                        .collect(Collectors.toList()))
                .build();
    }
}