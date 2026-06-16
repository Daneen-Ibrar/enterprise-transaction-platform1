package com.enterprise.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);
    private static final String HASH_ALGORITHM = "SHA-256";

    private final AuditRepository auditRepository;
    private final ObjectMapper objectMapper;

    public AuditService(AuditRepository auditRepository, ObjectMapper objectMapper) {
        this.auditRepository = auditRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public AuditEvent recordEvent(String eventType, Long userId, Object details) {
        try {
            // 1. Serialize details to JSON
            String detailsJson = objectMapper.writeValueAsString(details);

            // 2. Get the previous event (latest)
            String previousHash = auditRepository.findFirstByOrderByCreatedAtDesc()
                    .map(AuditEvent::getCurrentHash)
                    .orElse("");

            // 3. Compute hash: SHA‑256(previousHash + detailsJson + eventType + userId)
            String input = previousHash + detailsJson + eventType + userId;
            String currentHash = hash(input);

            // 4. Create and persist the event
            AuditEvent event = new AuditEvent(eventType, userId, detailsJson, previousHash, currentHash);
            return auditRepository.save(event);

        } catch (JsonProcessingException | NoSuchAlgorithmException e) {
            log.error("Failed to record audit event", e);
            throw new RuntimeException("Audit recording failed", e);
        }
    }

    private String hash(String input) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
        byte[] hashBytes = digest.digest(input.getBytes());
        return HexFormat.of().formatHex(hashBytes);
    }

    // Verification: check the entire chain
    public boolean verifyChain() {
        List<AuditEvent> events = auditRepository.findAll();
        if (events.isEmpty()) return true;

        String expectedPreviousHash = "";
        for (AuditEvent event : events) {
            if (!event.getPreviousHash().equals(expectedPreviousHash)) {
                log.warn("Chain broken at event {}: expected previous hash {}, got {}",
                        event.getId(), expectedPreviousHash, event.getPreviousHash());
                return false;
            }
            // Recompute current hash
            String input = event.getPreviousHash() + event.getDetails() + event.getEventType() + event.getUserId();
            try {
                String recomputed = hash(input);
                if (!recomputed.equals(event.getCurrentHash())) {
                    log.warn("Hash mismatch at event {}: stored {}, computed {}",
                            event.getId(), event.getCurrentHash(), recomputed);
                    return false;
                }
                expectedPreviousHash = event.getCurrentHash();
            } catch (NoSuchAlgorithmException e) {
                log.error("Hash algorithm not found", e);
                return false;
            }
        }
        return true;
    }

    public List<AuditEvent> getEventsForUser(Long userId) {
        return auditRepository.findByUserIdOrderByCreatedAtAsc(userId);
    }

    public List<AuditEvent> getAllEvents() {
        return auditRepository.findAll();
    }
}