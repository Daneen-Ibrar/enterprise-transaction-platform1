package com.enterprise.reliability;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

@Service
public class DlqProcessorService {

    private static final Logger log = LoggerFactory.getLogger(DlqProcessorService.class);
    private final DlqEntryRepository dlqEntryRepository;
    private final ObjectMapper objectMapper;
    private final Map<String, Supplier<Object>> operationRegistry = new ConcurrentHashMap<>();

    public DlqProcessorService(DlqEntryRepository dlqEntryRepository, ObjectMapper objectMapper) {
        this.dlqEntryRepository = dlqEntryRepository;
        this.objectMapper = objectMapper;
    }

    // Register a handler for a given operation type
    public void registerOperation(String operationType, Supplier<Object> operation) {
        operationRegistry.put(operationType, operation);
    }

    @Transactional
    public void retryDlq(Long entryId) {
        DlqEntry entry = dlqEntryRepository.findById(entryId)
                .orElseThrow(() -> new RuntimeException("DLQ entry not found"));
        if (!"PENDING".equals(entry.getStatus()) && !"RETRYING".equals(entry.getStatus())) {
            throw new IllegalStateException("Cannot retry entry with status " + entry.getStatus());
        }

        // Deserialize payload
        Map<String, Object> context;
        try {
            context = objectMapper.readValue(entry.getPayload(), Map.class);
        } catch (Exception e) {
            log.error("Failed to deserialize payload", e);
            entry.setStatus("FAILED");
            dlqEntryRepository.save(entry);
            throw new RuntimeException("Corrupted DLQ entry", e);
        }

        Supplier<Object> operation = operationRegistry.get(entry.getOperationType());
        if (operation == null) {
            entry.setStatus("FAILED");
            dlqEntryRepository.save(entry);
            throw new RuntimeException("No handler for operation: " + entry.getOperationType());
        }

        try {
            Object result = operation.get();
            entry.setStatus("RESOLVED");
            dlqEntryRepository.save(entry);
            log.info("DLQ entry {} resolved successfully", entryId);
        } catch (Exception e) {
            entry.setFailureCount(entry.getFailureCount() + 1);
            entry.setFailureReason(e.getMessage());
            entry.setStatus("PENDING"); // or FAILED after threshold
            dlqEntryRepository.save(entry);
            log.error("Retry failed for DLQ entry {}", entryId, e);
            throw new RuntimeException("Retry failed", e);
        }
    }
}