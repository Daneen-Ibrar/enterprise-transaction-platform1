package com.enterprise.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class AuditDiffService {

    private static final Logger log = LoggerFactory.getLogger(AuditDiffService.class);
    private final ObjectMapper objectMapper;

    public AuditDiffService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<DiffEntry> diff(String previousJson, String currentJson) {
        if (previousJson == null || currentJson == null) {
            log.debug("One of the snapshots is null, cannot compute diff.");
            return List.of();
        }
        try {
            // Trim and handle empty strings
            previousJson = previousJson.trim();
            currentJson = currentJson.trim();
            if (previousJson.isEmpty() || currentJson.isEmpty()) {
                return List.of();
            }
            Map<String, Object> prev = objectMapper.readValue(previousJson, Map.class);
            Map<String, Object> curr = objectMapper.readValue(currentJson, Map.class);
            return diffMaps(prev, curr);
        } catch (Exception e) {
            log.error("Failed to compute diff between {} and {}", previousJson, currentJson, e);
            return List.of();
        }
    }

    private List<DiffEntry> diffMaps(Map<String, Object> prev, Map<String, Object> curr) {
        List<DiffEntry> diffs = new ArrayList<>();
        Set<String> allKeys = new HashSet<>(prev.keySet());
        allKeys.addAll(curr.keySet());

        for (String key : allKeys) {
            Object oldVal = prev.get(key);
            Object newVal = curr.get(key);

            if (oldVal == null && newVal == null) continue;
            if (oldVal == null) {
                diffs.add(new DiffEntry(key, "added", null, newVal));
            } else if (newVal == null) {
                diffs.add(new DiffEntry(key, "removed", oldVal, null));
            } else if (!Objects.equals(oldVal, newVal)) {
                diffs.add(new DiffEntry(key, "changed", oldVal, newVal));
            }
        }
        return diffs;
    }

    public static class DiffEntry {
        private final String field;
        private final String type;
        private final Object oldValue;
        private final Object newValue;

        public DiffEntry(String field, String type, Object oldValue, Object newValue) {
            this.field = field;
            this.type = type;
            this.oldValue = oldValue;
            this.newValue = newValue;
        }

        public String getField() { return field; }
        public String getType() { return type; }
        public Object getOldValue() { return oldValue; }
        public Object getNewValue() { return newValue; }
    }
}