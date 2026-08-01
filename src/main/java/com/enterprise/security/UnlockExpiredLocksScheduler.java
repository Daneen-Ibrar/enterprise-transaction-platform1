package com.enterprise.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class UnlockExpiredLocksScheduler {

    private static final Logger log = LoggerFactory.getLogger(UnlockExpiredLocksScheduler.class);
    private final LoginAttemptService loginAttemptService;

    public UnlockExpiredLocksScheduler(LoginAttemptService loginAttemptService) {
        this.loginAttemptService = loginAttemptService;
    }

    // Run every minute
    @Scheduled(cron = "0 * * * * *")
    public void unlockExpiredLocks() {
        log.debug("Running scheduled task: unlock expired locks");
        loginAttemptService.unlockExpiredLocks();
    }
}