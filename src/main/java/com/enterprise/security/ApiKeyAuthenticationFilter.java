package com.enterprise.security;

import com.enterprise.apikey.ApiKey;
import com.enterprise.apikey.ApiKeyService;
import com.enterprise.tenant.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyAuthenticationFilter.class);
    private final ApiKeyService apiKeyService;

    public ApiKeyAuthenticationFilter(ApiKeyService apiKeyService) {
        this.apiKeyService = apiKeyService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI();

        // Skip non-API endpoints
        if (!path.startsWith("/api/")) {
            filterChain.doFilter(request, response);
            return;
        }

        // Allow public API endpoints without key
        if (path.startsWith("/api/public")) {
            log.debug("Public API endpoint: {}", path);
            filterChain.doFilter(request, response);
            return;
        }

        // For all other /api/ endpoints, validate API key
        String apiKey = request.getHeader("X-API-Key");
        log.info("API Key received for protected endpoint: {}", path);

        if (apiKey == null || apiKey.trim().isEmpty()) {
            log.warn("Missing X-API-Key header for: {}", path);
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Missing X-API-Key header\"}");
            return;
        }

        var keyOptional = apiKeyService.validateKey(apiKey);
        if (keyOptional.isEmpty()) {
            log.warn("Invalid or inactive API Key for: {}", path);
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Invalid or inactive API Key\"}");
            return;
        }

        ApiKey key = keyOptional.get();
        apiKeyService.updateLastUsed(key);

        // Authenticate the request
        var auth = new UsernamePasswordAuthenticationToken(
                key.getUser().getEmail(),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_API_USER"))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);

        // ✅ Set tenant context for this request
        if (key.getUser().getTenantId() != null) {
            TenantContext.setTenantId(key.getUser().getTenantId());
            log.debug("Tenant context set to: {}", key.getUser().getTenantId());
        } else {
            log.warn("API key user has no tenant ID: {}", key.getUser().getEmail());
        }

        log.info("API Key authenticated for user: {}", key.getUser().getEmail());
        filterChain.doFilter(request, response);
    }
}