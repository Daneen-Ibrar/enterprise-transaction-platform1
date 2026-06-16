package com.enterprise.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    public void recordEvent(String eventType, String details, Long userId) {
        // In M4, this will write to the immutable audit table with hash chaining.
        // For now, just log it.
        log.info("AUDIT: {} | user={} | details={}", eventType, userId, details);
    }
}