package com.enterprise.api;

import com.enterprise.apikey.ApiKey;
import com.enterprise.apikey.ApiKeyRepository;
import com.enterprise.ratelimit.RateLimitConfig;
import com.enterprise.ratelimit.RateLimitViolation;
import com.enterprise.security.RateLimitService;
import com.enterprise.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/admin/api-keys/monitor")
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MERCHANT_ADMIN')")
public class AdminApiKeyMonitorController {

    private static final Logger log = LoggerFactory.getLogger(AdminApiKeyMonitorController.class);

    private final ApiKeyRepository apiKeyRepository;
    private final RateLimitService rateLimitService;

    public AdminApiKeyMonitorController(ApiKeyRepository apiKeyRepository,
                                        RateLimitService rateLimitService) {
        this.apiKeyRepository = apiKeyRepository;
        this.rateLimitService = rateLimitService;
    }
@GetMapping("/{apiKeyPrefix}")
public String detail(@PathVariable String apiKeyPrefix, Model model) {
    Long tenantId = TenantContext.getRequiredTenantId();
    log.info("📊 Loading API key details for: {}", apiKeyPrefix);

    try {
        // Get the API key
        ApiKey apiKey = apiKeyRepository.findByKeyPrefix(apiKeyPrefix)
                .orElseThrow(() -> new RuntimeException("API key not found: " + apiKeyPrefix));

        // Get rate limit config
        RateLimitConfig config = rateLimitService.getConfigForApiKey(apiKeyPrefix, tenantId);

        // Get violations for this key
        List<RateLimitViolation> violations = rateLimitService.getViolationsForApiKey(tenantId, apiKeyPrefix);

        // Get usage stats from Redis
        long paymentUsage = 0;
        long readUsage = 0;
        if (config != null) {
            // Get current usage from Redis - simplified
            String paymentKey = "rate:tenant:" + tenantId + ":payment:" + apiKey.getKeyValue();
            String readKey = "rate:tenant:" + tenantId + ":read:" + apiKey.getKeyValue();
            
            try {
                // You'd need to inject StringRedisTemplate for this
                // String paymentCount = redisTemplate.opsForValue().get(paymentKey);
                // String readCount = redisTemplate.opsForValue().get(readKey);
            } catch (Exception e) {
                log.warn("Could not get Redis usage for API key: {}", apiKeyPrefix);
            }
        }

        model.addAttribute("apiKey", apiKey);
        model.addAttribute("config", config);
        model.addAttribute("violations", violations);
        model.addAttribute("violationCount", violations.size());
        model.addAttribute("hasConfig", config != null);

        return "admin/api-keys/monitor-detail";

    } catch (Exception e) {
        log.error("Error loading API key details: {}", e.getMessage(), e);
        model.addAttribute("error", "Failed to load API key details: " + e.getMessage());
        return "admin/api-keys/monitor-detail";
    }
}
    @GetMapping
    public String monitor(Model model) {
        Long tenantId = TenantContext.getRequiredTenantId();
        log.info("📊 Loading API key monitor for tenant: {}", tenantId);

        try {
            // Get all API keys for this tenant
            List<ApiKey> apiKeys = apiKeyRepository.findAllByTenantId(tenantId);
            
            List<ApiKeyStatus> keyStatuses = new ArrayList<>();
            long totalViolations = 0;

            for (ApiKey key : apiKeys) {
                String prefix = key.getKeyPrefix();
                
                // Get rate limit config
                RateLimitConfig config = rateLimitService.getConfigForApiKey(prefix, tenantId);
                
                // Get violations for this key
                List<RateLimitViolation> violations = rateLimitService.getViolationsForApiKey(tenantId, prefix);
                long violationCount = violations.size();
                totalViolations += violationCount;

                // Get current usage
                long paymentUsage = 0;
                long readUsage = 0;
                if (config != null) {
                    // Get current usage from Redis
                    // This is a simplified approach - you'd need to get from Redis
                }

                ApiKeyStatus status = new ApiKeyStatus();
                status.setApiKey(key);
                status.setConfig(config);
                status.setViolationCount(violationCount);
                status.setRecentViolations(violations.stream().limit(5).collect(Collectors.toList()));
                status.setActive(key.isActive());
                status.setLastUsed(key.getLastUsedAt());
                status.setHasConfig(config != null);
                
                keyStatuses.add(status);
            }

            // Sort by violation count (highest first)
            keyStatuses.sort((a, b) -> Long.compare(b.getViolationCount(), a.getViolationCount()));

            model.addAttribute("keyStatuses", keyStatuses);
            model.addAttribute("totalKeys", apiKeys.size());
            model.addAttribute("totalViolations", totalViolations);
            model.addAttribute("keysWithConfigs", keyStatuses.stream().filter(ApiKeyStatus::isHasConfig).count());
            model.addAttribute("keysWithoutConfigs", keyStatuses.stream().filter(s -> !s.isHasConfig()).count());

            return "admin/api-keys/monitor";

        } catch (Exception e) {
            log.error("Error loading API key monitor: {}", e.getMessage(), e);
            model.addAttribute("error", "Failed to load API key monitor: " + e.getMessage());
            model.addAttribute("keyStatuses", List.of());
            model.addAttribute("totalKeys", 0);
            model.addAttribute("totalViolations", 0);
            model.addAttribute("keysWithConfigs", 0);
            model.addAttribute("keysWithoutConfigs", 0);
            return "admin/api-keys/monitor";
        }
    }

    // Inner class for API key status
    public static class ApiKeyStatus {
        private ApiKey apiKey;
        private RateLimitConfig config;
        private long violationCount;
        private List<RateLimitViolation> recentViolations;
        private boolean active;
        private LocalDateTime lastUsed;
        private boolean hasConfig;

        // Getters and setters
        public ApiKey getApiKey() { return apiKey; }
        public void setApiKey(ApiKey apiKey) { this.apiKey = apiKey; }

        public RateLimitConfig getConfig() { return config; }
        public void setConfig(RateLimitConfig config) { this.config = config; }

        public long getViolationCount() { return violationCount; }
        public void setViolationCount(long violationCount) { this.violationCount = violationCount; }

        public List<RateLimitViolation> getRecentViolations() { return recentViolations; }
        public void setRecentViolations(List<RateLimitViolation> recentViolations) { this.recentViolations = recentViolations; }

        public boolean isActive() { return active; }
        public void setActive(boolean active) { this.active = active; }

        public LocalDateTime getLastUsed() { return lastUsed; }
        public void setLastUsed(LocalDateTime lastUsed) { this.lastUsed = lastUsed; }

        public boolean isHasConfig() { return hasConfig; }
        public void setHasConfig(boolean hasConfig) { this.hasConfig = hasConfig; }

        // Helper methods
        public String getStatusBadge() {
            if (!active) return "danger";
            if (violationCount > 10) return "danger";
            if (violationCount > 5) return "warning";
            return "success";
        }

        public String getStatusText() {
            if (!active) return "Inactive";
            if (violationCount > 10) return "⚠️ High Violations";
            if (violationCount > 5) return "⚡ Moderate Violations";
            return "✅ Healthy";
        }

        public long getPaymentLimit() {
            return config != null ? config.getPaymentLimit() : 100;
        }

        public long getReadLimit() {
            return config != null ? config.getReadLimit() : 200;
        }
    }
}