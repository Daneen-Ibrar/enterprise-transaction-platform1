package com.enterprise.security;

import com.enterprise.apikey.ApiKey;
import com.enterprise.apikey.ApiKeyService;
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
        if (!path.startsWith("/api/public")) {
            filterChain.doFilter(request, response);
            return;
        }

        String apiKey = request.getHeader("X-API-Key");
        log.debug("API Key received: {}", apiKey);

        if (apiKey == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("{\"error\":\"Missing X-API-Key header\"}");
            response.setContentType("application/json");
            return;
        }

        var keyOptional = apiKeyService.validateKey(apiKey);
        if (keyOptional.isEmpty()) {
            log.warn("Invalid or inactive API Key: {}", apiKey);
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("{\"error\":\"Invalid or inactive API Key\"}");
            response.setContentType("application/json");
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

        log.debug("API Key authenticated for user: {}", key.getUser().getEmail());
        filterChain.doFilter(request, response);
    }
}