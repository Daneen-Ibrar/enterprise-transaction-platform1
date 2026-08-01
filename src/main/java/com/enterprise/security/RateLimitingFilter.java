package com.enterprise.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    private final RateLimitService rateLimitService;

    public RateLimitingFilter(RateLimitService rateLimitService) {
        this.rateLimitService = rateLimitService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        // Only apply to API endpoints
        String path = request.getRequestURI();
        if (!path.startsWith("/api/")) {
            filterChain.doFilter(request, response);
            return;
        }

        String apiKey = request.getHeader("X-API-Key");
        if (apiKey == null || apiKey.isEmpty()) {
            filterChain.doFilter(request, response);
            return;
        }

        // Determine rate limit type based on HTTP method
        RateLimitService.RateLimitType type = "POST".equalsIgnoreCase(request.getMethod()) &&
                path.contains("/payments") ?
                RateLimitService.RateLimitType.PAYMENT :
                RateLimitService.RateLimitType.READ;

        boolean allowed = rateLimitService.isAllowed(apiKey, type);

        if (!allowed) {
            long remaining = rateLimitService.getRemainingQuota(apiKey, type);
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader("X-RateLimit-Limit", String.valueOf(
                    type == RateLimitService.RateLimitType.PAYMENT ? 100 : 200
            ));
            response.setHeader("X-RateLimit-Remaining", "0");
            response.setHeader("X-RateLimit-Reset", "60");
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Rate limit exceeded\",\"message\":\"Too many requests\"}");
            return;
        }

        // Add rate limit headers to response
        long remaining = rateLimitService.getRemainingQuota(apiKey, type);
        response.setHeader("X-RateLimit-Limit", String.valueOf(
                type == RateLimitService.RateLimitType.PAYMENT ? 100 : 200
        ));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(remaining));

        filterChain.doFilter(request, response);
    }
}