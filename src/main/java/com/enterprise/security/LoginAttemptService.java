package com.enterprise.security;

import com.enterprise.audit.UserActivityLogService;
import com.enterprise.identity.AppUser;
import com.enterprise.identity.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class LoginAttemptService {

    private static final Logger log = LoggerFactory.getLogger(LoginAttemptService.class);

    private final UserRepository userRepository;
    private final UserActivityLogService activityLogService;

    @Value("${app.security.max-failed-attempts:5}")
    private int maxFailedAttempts;

    @Value("${app.security.lock-duration-minutes:30}")
    private int lockDurationMinutes;

    public LoginAttemptService(UserRepository userRepository,
                               UserActivityLogService activityLogService) {
        this.userRepository = userRepository;
        this.activityLogService = activityLogService;
    }

    @Transactional
    public void loginFailed(String email) {
        log.info("🔐 loginFailed() called for: {}", email);
        userRepository.findByEmail(email).ifPresent(user -> {
            log.info("👤 User found: {}, current attempts: {}, superAdmin: {}",
                    user.getEmail(), user.getFailedLoginAttempts(), user.isSuperAdmin());

            // Super admin is immune to lockout
            if (user.isSuperAdmin()) {
                log.info("🛡️ Super admin {} attempted failed login – not locking", user.getEmail());
                return;
            }

            // If already locked and lock hasn't expired, don't increment further
            if (user.isAccountLocked() && (user.getLockExpiry() == null ||
                    user.getLockExpiry().isAfter(LocalDateTime.now()))) {
                log.info("🔒 Account {} is already locked, not incrementing attempts", user.getEmail());
                return;
            }

            int attempts = user.getFailedLoginAttempts() + 1;
            user.setFailedLoginAttempts(attempts);
            log.info("📊 Updated attempts for {}: {}", user.getEmail(), attempts);

            if (attempts >= maxFailedAttempts) {
                user.setAccountLocked(true);
                user.setLockExpiry(LocalDateTime.now().plusMinutes(lockDurationMinutes));
                activityLogService.logActivity(
                        user.getId(),
                        "ACCOUNT_LOCKED",
                        "Account locked due to " + attempts + " failed login attempts. Expires at " + user.getLockExpiry(),
                        null
                );
                log.info("🔒 Account locked for user {} after {} failed attempts", user.getEmail(), attempts);
            }
            userRepository.save(user);
        });
    }

    @Transactional
    public void loginSucceeded(String email) {
        log.info("✅ loginSucceeded() called for: {}", email);
        userRepository.findByEmail(email).ifPresent(user -> {
            log.info("👤 User found: {}, locked: {}, attempts: {}",
                    user.getEmail(), user.isAccountLocked(), user.getFailedLoginAttempts());

            // If still locked, do not reset (prevent login)
            if (user.isAccountLocked() && user.getLockExpiry() != null
                    && user.getLockExpiry().isAfter(LocalDateTime.now())) {
                log.info("🔒 User {} attempted login while locked – not resetting", user.getEmail());
                return;
            }
            // Unlock and reset attempts
            user.setFailedLoginAttempts(0);
            user.setAccountLocked(false);
            user.setLockExpiry(null);
            userRepository.save(user);
            log.info("🔄 Login attempts reset for user {}", user.getEmail());
        });
    }

    @Transactional
    public void unlockAccount(Long userId) {
        userRepository.findById(userId).ifPresent(user -> {
            user.setFailedLoginAttempts(0);
            user.setAccountLocked(false);
            user.setLockExpiry(null);
            userRepository.save(user);
            activityLogService.logActivity(
                    user.getId(),
                    "ACCOUNT_UNLOCKED",
                    "Account unlocked by admin",
                    null
            );
            log.info("🔓 Account unlocked for user {} by admin", user.getEmail());
        });
    }

    @Transactional
    public void unlockExpiredLocks() {
        List<AppUser> lockedUsers = userRepository.findByAccountLockedTrueAndLockExpiryBefore(LocalDateTime.now());
        if (lockedUsers.isEmpty()) {
            log.debug("No expired locks to unlock");
            return;
        }
        for (AppUser user : lockedUsers) {
            user.setAccountLocked(false);
            user.setLockExpiry(null);
            user.setFailedLoginAttempts(0);
            userRepository.save(user);
            activityLogService.logActivity(
                    user.getId(),
                    "ACCOUNT_UNLOCKED",
                    "Account automatically unlocked after lock expiry",
                    null
            );
            log.info("🔓 Automatically unlocked account for user {}", user.getEmail());
        }
        log.info("Unlocked {} expired locks", lockedUsers.size());
    }

    public boolean isAccountLocked(String email) {
        return userRepository.findByEmail(email)
                .map(user -> user.isAccountLocked() && (user.getLockExpiry() == null ||
                        user.getLockExpiry().isAfter(LocalDateTime.now())))
                .orElse(false);
    }
}