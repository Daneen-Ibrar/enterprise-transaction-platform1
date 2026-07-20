package com.enterprise.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

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

    public boolean isAllowed(String key, RateLimitType type) {
        String redisKey = "rate:" + type.name().toLowerCase() + ":" + key;
        int limit = type == RateLimitType.PAYMENT ? paymentRequestLimit : readRequestLimit;
        int window = type == RateLimitType.PAYMENT ? paymentRequestWindowSeconds : readRequestWindowSeconds;

        Long count = redisTemplate.opsForValue().increment(redisKey);

        if (count == null) {
            // Redis unavailable – fallback to allow (or reject based on your choice)
            // We'll allow but log a warning
            return true;
        }

        // Set expiry on first request
        if (count == 1) {
            redisTemplate.expire(redisKey, Duration.ofSeconds(window));
        }

        return count <= limit;
    }

    public void resetLimit(String key, RateLimitType type) {
        String redisKey = "rate:" + type.name().toLowerCase() + ":" + key;
        redisTemplate.delete(redisKey);
    }

    public long getRemainingQuota(String key, RateLimitType type) {
        String redisKey = "rate:" + type.name().toLowerCase() + ":" + key;
        String count = redisTemplate.opsForValue().get(redisKey);
        if (count == null) {
            return type == RateLimitType.PAYMENT ? paymentRequestLimit : readRequestLimit;
        }
        long current = Long.parseLong(count);
        long limit = type == RateLimitType.PAYMENT ? paymentRequestLimit : readRequestLimit;
        return Math.max(0, limit - current);
    }

    public enum RateLimitType {
        PAYMENT,
        READ
    }
}