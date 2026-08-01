package com.enterprise.reporting;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class ReportConfigService {

    private final ReportConfigRepository repository;
    private final ObjectMapper objectMapper;

    public ReportConfigService(ReportConfigRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ReportConfig saveReport(String name, String reportType, Map<String, Object> filters, Long userId) {
        String filtersJson;
        try {
            filtersJson = objectMapper.writeValueAsString(filters);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize filters", e);
        }

        ReportConfig config = new ReportConfig();
        config.setName(name);
        config.setUserId(userId);
        config.setReportType(reportType);
        config.setFilters(filtersJson);
        config.setUpdatedAt(LocalDateTime.now());
        return repository.save(config);
    }

    @Transactional
    public void deleteReport(Long id, Long userId) {
        ReportConfig config = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Report not found"));
        if (!config.getUserId().equals(userId)) {
            throw new SecurityException("You do not own this report");
        }
        repository.deleteById(id);
    }

    public ReportConfig getReport(Long id, Long userId) {
        ReportConfig config = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Report not found"));
        if (!config.getUserId().equals(userId)) {
            throw new SecurityException("You do not own this report");
        }
        return config;
    }

    public List<ReportConfig> getUserReports(Long userId) {
        return repository.findByUserIdOrderByName(userId);
    }

    public List<ReportConfig> getUserReportsByType(Long userId, String reportType) {
        return repository.findByUserIdAndReportTypeOrderByName(userId, reportType);
    }

    public Map<String, Object> deserializeFilters(ReportConfig config) {
        try {
            return objectMapper.readValue(config.getFilters(), Map.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize filters", e);
        }
    }
}