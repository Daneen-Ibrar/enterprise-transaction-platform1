package com.enterprise.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    private static final int MAX_REQUESTS = 100; // per minute
    private static final long TIME_WINDOW = 60000; // 1 minute

    private final ConcurrentHashMap<String, RateLimitInfo> requestCounts = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String apiKey = request.getHeader("X-API-Key");
        if (apiKey == null) {
            filterChain.doFilter(request, response);
            return;
        }

        RateLimitInfo info = requestCounts.computeIfAbsent(apiKey, k -> new RateLimitInfo());
        synchronized (info) {
            long now = System.currentTimeMillis();
            if (now - info.lastReset > TIME_WINDOW) {
                info.count.set(0);
                info.lastReset = now;
            }
            if (info.count.incrementAndGet() > MAX_REQUESTS) {
                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.getWriter().write("{\"error\":\"Rate limit exceeded\",\"message\":\"Maximum 100 requests per minute\"}");
                response.setContentType("application/json");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private static class RateLimitInfo {
        private final AtomicInteger count = new AtomicInteger(0);
        private long lastReset = System.currentTimeMillis();
    }
}