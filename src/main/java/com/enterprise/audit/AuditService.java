package com.enterprise.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.enterprise.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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

    // ================================================================
    // RECORD EVENT – WITH RETRY AND PESSIMISTIC LOCKING
    // ================================================================
    @Retryable(
        value = {OptimisticLockingFailureException.class, org.springframework.dao.DataIntegrityViolationException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 100, multiplier = 2)
    )
    @Transactional
    public AuditEvent recordEvent(String eventType, Long userId, Object details,
                                  String entityType, Long entityId,
                                  Object previousState, Object currentState) {
        try {
            Long tenantId = TenantContext.getRequiredTenantId();
            Long effectiveUserId = (userId != null) ? userId : 0L;

            String detailsJson = objectMapper.writeValueAsString(details);
            String previousJson = previousState != null ? objectMapper.writeValueAsString(previousState) : null;
            String currentJson = currentState != null ? objectMapper.writeValueAsString(currentState) : null;

            // ================================================================
            // GET THE LATEST EVENT WITH PESSIMISTIC LOCKING – only ONE result
            // ================================================================
            AuditEvent lastEvent = null;
            if (entityType != null && entityId != null) {
                // Use findFirst (returns only ONE result)
                lastEvent = auditRepository
                        .findFirstByEntityTypeAndEntityIdOrderByIdDesc(entityType, entityId)
                        .orElse(null);
            } else {
                lastEvent = auditRepository
                        .findFirstByOrderByCreatedAtDesc()
                        .orElse(null);
            }

            String previousHash = lastEvent != null ? lastEvent.getCurrentHash() : "";

            // ================================================================
            // COMPUTE HASH
            // ================================================================
            String input = previousHash + detailsJson + eventType + effectiveUserId;
            String currentHash = hash(input);

            AuditEvent event = new AuditEvent(eventType, effectiveUserId, detailsJson, previousHash, currentHash);
            event.setEntityType(entityType);
            event.setEntityId(entityId);
            event.setPreviousState(previousJson);
            event.setCurrentState(currentJson);
            event.setTenantId(tenantId);

            // ================================================================
            // SAVE AND VALIDATE
            // ================================================================
            AuditEvent savedEvent = auditRepository.save(event);

            // Validate the saved hash immediately
            String recomputedHash = hash(savedEvent.getPreviousHash() + savedEvent.getDetails() + savedEvent.getEventType() + savedEvent.getUserId());
            if (!recomputedHash.equals(savedEvent.getCurrentHash())) {
                log.warn("🚨 Hash mismatch detected immediately after save for event {}. Fixing...", savedEvent.getId());
                savedEvent.setCurrentHash(recomputedHash);
                savedEvent = auditRepository.save(savedEvent);
                log.info("✅ Fixed hash for event {} to {}", savedEvent.getId(), recomputedHash);
            }

            log.debug("✅ Audit event recorded: {} for {}#{}", eventType, entityType, entityId);
            return savedEvent;

        } catch (JsonProcessingException | NoSuchAlgorithmException e) {
            log.error("Failed to record audit event", e);
            throw new RuntimeException("Audit recording failed", e);
        }
    }

    // ================================================================
    // OVERLOADED METHODS
    // ================================================================
    @Transactional
    public AuditEvent recordEvent(String eventType, Long userId, Object details) {
        return recordEvent(eventType, userId, details, null, null, null, null);
    }

    @Transactional
    public AuditEvent recordEvent(String eventType, Long userId, Map<String, Object> details,
                                  String entityType, Long entityId) {
        return recordEvent(eventType, userId, details, entityType, entityId, null, null);
    }

    // ================================================================
    // HASH UTILITY
    // ================================================================
    private String hash(String input) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
        byte[] hashBytes = digest.digest(input.getBytes());
        return HexFormat.of().formatHex(hashBytes);
    }

    // ================================================================
    // VERIFY CHAIN INTEGRITY
    // ================================================================
    public boolean verifyChain() {
        Long tenantId = TenantContext.getRequiredTenantId();
        return verifyChainForTenant(tenantId);
    }

    public boolean verifyChainForTenant(Long tenantId) {
        List<AuditEvent> allEvents = auditRepository.findAll();

        var eventsByEntity = allEvents.stream()
            .filter(e -> e.getEntityType() != null && e.getEntityId() != null)
            .collect(java.util.stream.Collectors.groupingBy(
                e -> e.getEntityType() + ":" + e.getEntityId(),
                java.util.stream.Collectors.toList()
            ));

        for (var entry : eventsByEntity.entrySet()) {
            List<AuditEvent> entityEvents = entry.getValue();
            entityEvents.sort((a, b) -> a.getId().compareTo(b.getId()));

            String expectedPreviousHash = "";
            boolean first = true;

            for (AuditEvent event : entityEvents) {
                if (first) {
                    if (!event.getPreviousHash().isEmpty()) {
                        log.warn("First event for entity {} has non-empty previous hash: {}",
                            entry.getKey(), event.getPreviousHash());
                        return false;
                    }
                    first = false;
                } else {
                    if (!event.getPreviousHash().equals(expectedPreviousHash)) {
                        log.warn("Chain broken at event {} for entity {}: expected {}, got {}",
                                event.getId(), entry.getKey(), expectedPreviousHash, event.getPreviousHash());
                        return false;
                    }
                }

                String input = event.getPreviousHash() + event.getDetails() + event.getEventType() + event.getUserId();
                try {
                    String recomputed = hash(input);
                    if (!recomputed.equals(event.getCurrentHash())) {
                        log.warn("Hash mismatch at event {} for entity {}: stored {}, computed {}",
                                event.getId(), entry.getKey(), event.getCurrentHash(), recomputed);
                        return false;
                    }
                } catch (NoSuchAlgorithmException e) {
                    log.error("Hash algorithm not found", e);
                    return false;
                }

                expectedPreviousHash = event.getCurrentHash();
            }
        }

        var globalEvents = allEvents.stream()
            .filter(e -> e.getEntityType() == null || e.getEntityId() == null)
            .sorted((a, b) -> a.getId().compareTo(b.getId()))
            .collect(java.util.stream.Collectors.toList());

        String expectedPreviousHash = "";
        boolean first = true;

        for (AuditEvent event : globalEvents) {
            if (first) {
                if (!event.getPreviousHash().isEmpty()) {
                    log.warn("First global event has non-empty previous hash: {}", event.getPreviousHash());
                    return false;
                }
                first = false;
            } else {
                if (!event.getPreviousHash().equals(expectedPreviousHash)) {
                    log.warn("Chain broken at global event {}: expected {}, got {}",
                            event.getId(), expectedPreviousHash, event.getPreviousHash());
                    return false;
                }
            }

            String input = event.getPreviousHash() + event.getDetails() + event.getEventType() + event.getUserId();
            try {
                String recomputed = hash(input);
                if (!recomputed.equals(event.getCurrentHash())) {
                    log.warn("Hash mismatch at global event {}: stored {}, computed {}",
                            event.getId(), event.getCurrentHash(), recomputed);
                    return false;
                }
            } catch (NoSuchAlgorithmException e) {
                log.error("Hash algorithm not found", e);
                return false;
            }

            expectedPreviousHash = event.getCurrentHash();
        }

        return true;
    }

    // ================================================================
    // QUERY METHODS
    // ================================================================
    public List<AuditEvent> getEventsForUser(Long userId) {
        return auditRepository.findByUserIdOrderByCreatedAtAsc(userId);
    }

    public List<AuditEvent> getEventsForEntity(String entityType, Long entityId) {
        return auditRepository.findByEntityTypeAndEntityIdOrderByCreatedAtAsc(entityType, entityId);
    }

    public List<AuditEvent> getAllEvents() {
        return auditRepository.findAll();
    }

    public Optional<AuditEvent> getEventById(Long id) {
        return auditRepository.findById(id);
    }
}