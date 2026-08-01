package com.enterprise.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class RateLimitService {

    private final StringRedisTemplate redisTemplate;

    @Value("${app.rate-limit.payment-requests:100}")
    private int paymentRequestLimit;

    @Value("${app.rate-limit.payment-requests-window:60}")
    private int paymentRequestWindowSeconds;

    @Value("${app.rate-limit.read-requests:200}")
    private int readRequestLimit;

    @Value("${app.rate-limit.read-requests-window:60}")
    private int readRequestWindowSeconds;

    public RateLimitService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public enum RateLimitType {
        PAYMENT,
        READ
    }

    // Original method (for API key)
    public boolean isAllowed(String key, RateLimitType type) {
        return isAllowed("rate:" + type.name().toLowerCase() + ":" + key, getLimit(type), getWindow(type));
    }

    // New method: tenant-aware
    public boolean isAllowedForTenant(Long tenantId, String apiKey, RateLimitType type) {
        String key = "rate:tenant:" + tenantId + ":" + type.name().toLowerCase() + ":" + apiKey;
        return isAllowed(key, getLimit(type), getWindow(type));
    }

    private boolean isAllowed(String redisKey, int limit, int windowSeconds) {
        Long count = redisTemplate.opsForValue().increment(redisKey);
        if (count == null) {
            return true; // fallback: allow if Redis fails
        }
        if (count == 1) {
            redisTemplate.expire(redisKey, Duration.ofSeconds(windowSeconds));
        }
        return count <= limit;
    }

    private int getLimit(RateLimitType type) {
        return type == RateLimitType.PAYMENT ? paymentRequestLimit : readRequestLimit;
    }

    private int getWindow(RateLimitType type) {
        return type == RateLimitType.PAYMENT ? paymentRequestWindowSeconds : readRequestWindowSeconds;
    }

    public long getRemainingQuota(String key, RateLimitType type) {
        String redisKey = "rate:" + type.name().toLowerCase() + ":" + key;
        String count = redisTemplate.opsForValue().get(redisKey);
        if (count == null) return getLimit(type);
        long current = Long.parseLong(count);
        return Math.max(0, getLimit(type) - current);
    }

    public long getRemainingQuotaForTenant(Long tenantId, String apiKey, RateLimitType type) {
        String redisKey = "rate:tenant:" + tenantId + ":" + type.name().toLowerCase() + ":" + apiKey;
        String count = redisTemplate.opsForValue().get(redisKey);
        if (count == null) return getLimit(type);
        long current = Long.parseLong(count);
        return Math.max(0, getLimit(type) - current);
    }
}