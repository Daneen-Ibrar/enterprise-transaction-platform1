package com.enterprise.security;

import com.enterprise.ratelimit.RateLimitConfig;
import com.enterprise.ratelimit.RateLimitConfigRepository;
import com.enterprise.ratelimit.RateLimitViolation;
import com.enterprise.ratelimit.RateLimitViolationRepository;
import com.enterprise.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import java.util.List;

import java.time.Duration;
import java.time.LocalDateTime;

@Service
public class RateLimitService {

    private static final Logger log = LoggerFactory.getLogger(RateLimitService.class);

    private final StringRedisTemplate redisTemplate;
    private final RateLimitConfigRepository configRepository;
    private final RateLimitViolationRepository violationRepository;

    @Value("${app.rate-limit.payment-requests:100}")
    private int defaultPaymentLimit;

    @Value("${app.rate-limit.payment-requests-window:60}")
    private int defaultPaymentWindow;

    @Value("${app.rate-limit.read-requests:200}")
    private int defaultReadLimit;

    @Value("${app.rate-limit.read-requests-window:60}")
    private int defaultReadWindow;

    public RateLimitService(StringRedisTemplate redisTemplate,
                            RateLimitConfigRepository configRepository,
                            RateLimitViolationRepository violationRepository) {
        this.redisTemplate = redisTemplate;
        this.configRepository = configRepository;
        this.violationRepository = violationRepository;
    }

    public enum RateLimitType {
        PAYMENT,
        READ
    }

    public boolean isAllowed(String apiKey, RateLimitType type) {
        String apiKeyPrefix = getApiKeyPrefix(apiKey);
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            tenantId = 1L;
        }

        // Get config for this API key
        RateLimitConfig config = configRepository
                .findByApiKeyPrefixAndTenantId(apiKeyPrefix, tenantId)
                .orElse(null);

        int limit = getLimit(config, type);
        int window = getWindow(config, type);

        String redisKey = "rate:tenant:" + tenantId + ":" + type.name().toLowerCase() + ":" + apiKey;

        Long count = redisTemplate.opsForValue().increment(redisKey);
        if (count == null) {
            return true;
        }

        if (count == 1) {
            redisTemplate.expire(redisKey, Duration.ofSeconds(window));
        }

        boolean allowed = count <= limit;

        // Log violation if exceeded
        if (!allowed) {
            log.warn("🚨 Rate limit exceeded for API key: {}, type: {}, count: {}, limit: {}",
                    apiKeyPrefix, type, count, limit);

            try {
                RateLimitViolation violation = new RateLimitViolation();
                violation.setApiKeyPrefix(apiKeyPrefix);
                violation.setTenantId(tenantId);
                violation.setLimitType(type.name());
                violation.setLimitValue(limit);
                violation.setActualUsage(count.intValue());
                violation.setRequestPath(""); // Will be set in filter
                violationRepository.save(violation);
            } catch (Exception e) {
                log.warn("Failed to log rate limit violation: {}", e.getMessage());
            }
        }

        return allowed;
    }

    public long getRemainingQuota(String apiKey, RateLimitType type) {
        String apiKeyPrefix = getApiKeyPrefix(apiKey);
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            tenantId = 1L;
        }

        RateLimitConfig config = configRepository
                .findByApiKeyPrefixAndTenantId(apiKeyPrefix, tenantId)
                .orElse(null);

        int limit = getLimit(config, type);
        String redisKey = "rate:tenant:" + tenantId + ":" + type.name().toLowerCase() + ":" + apiKey;
        String count = redisTemplate.opsForValue().get(redisKey);

        if (count == null) {
            return limit;
        }

        long current = Long.parseLong(count);
        return Math.max(0, limit - current);
    }

    // ============================================================
    // ADMIN METHODS
    // ============================================================

    public RateLimitConfig saveConfig(RateLimitConfig config) {
        config.setUpdatedAt(LocalDateTime.now());
        return configRepository.save(config);
    }

    public RateLimitConfig getConfigForApiKey(String apiKeyPrefix, Long tenantId) {
        return configRepository
                .findByApiKeyPrefixAndTenantId(apiKeyPrefix, tenantId)
                .orElse(null);
    }

    public List<RateLimitConfig> getAllConfigsForTenant(Long tenantId) {
        return configRepository.findAllByTenantId(tenantId);
    }

    public void deleteConfig(Long id) {
        configRepository.deleteById(id);
    }

    public List<RateLimitViolation> getViolationsForTenant(Long tenantId, int limit) {
        return violationRepository.findRecentByTenantId(tenantId, limit);
    }

    public long getViolationCountSince(Long tenantId, LocalDateTime since) {
        return violationRepository.countByTenantIdAndCreatedAtAfter(tenantId, since);
    }

    public List<RateLimitViolation> getViolationsForApiKey(Long tenantId, String apiKeyPrefix) {
        return violationRepository.findByTenantIdAndApiKeyPrefix(tenantId, apiKeyPrefix);
    }

    // ============================================================
    // HELPERS
    // ============================================================

    private String getApiKeyPrefix(String apiKey) {
        if (apiKey == null || apiKey.length() < 8) {
            return "unknown";
        }
        return apiKey.substring(0, 8);
    }

    private int getLimit(RateLimitConfig config, RateLimitType type) {
        if (config == null) {
            return type == RateLimitType.PAYMENT ? defaultPaymentLimit : defaultReadLimit;
        }
        return type == RateLimitType.PAYMENT ? config.getPaymentLimit() : config.getReadLimit();
    }

    private int getWindow(RateLimitConfig config, RateLimitType type) {
        if (config == null) {
            return type == RateLimitType.PAYMENT ? defaultPaymentWindow : defaultReadWindow;
        }
        return type == RateLimitType.PAYMENT ? config.getPaymentWindowSeconds() : config.getReadWindowSeconds();
    }
}