package com.enterprise.security;

import com.enterprise.feature.FeatureFlagService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class TwoFactorAuthenticationFilter extends OncePerRequestFilter {

    private static final List<String> PUBLIC_PATHS = Arrays.asList(
            "/2fa/**", "/logout", "/css/", "/js/"
    );

    private final FeatureFlagService featureFlagService;

    public TwoFactorAuthenticationFilter(FeatureFlagService featureFlagService) {
        this.featureFlagService = featureFlagService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        if (!featureFlagService.isEnabled("TWO_FACTOR_AUTH")) {
            filterChain.doFilter(request, response);
            return;
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getPrincipal().equals("anonymousUser")) {
            filterChain.doFilter(request, response);
            return;
        }

        HttpSession session = request.getSession(false);
        if (session == null) {
            filterChain.doFilter(request, response);
            return;
        }

        // ----- CHECK 2FA REQUIRED FOR ADMIN -----
        Boolean required = (Boolean) session.getAttribute("2FA_REQUIRED");
        if (required != null && required) {
            String uri = request.getRequestURI();
            // Allow 2FA pages, logout, and static resources
            if (uri.startsWith("/2fa/") || uri.startsWith("/logout") || uri.startsWith("/css/") || uri.startsWith("/js/")) {
                filterChain.doFilter(request, response);
                return;
            }
            // Otherwise redirect to 2FA setup
            response.sendRedirect("/2fa/setup?required=true");
            return;
        }

        // ----- NORMAL 2FA VERIFICATION -----
        Boolean pending = (Boolean) session.getAttribute("2FA_PENDING");
        Boolean authenticated = (Boolean) session.getAttribute("2FA_AUTHENTICATED");

        if (pending == null || !pending || (authenticated != null && authenticated)) {
            filterChain.doFilter(request, response);
            return;
        }

        String uri = request.getRequestURI();
        // Allow 2FA pages, logout, static resources
        if (uri.startsWith("/2fa/") || uri.startsWith("/logout") || uri.startsWith("/css/") || uri.startsWith("/js/")) {
            filterChain.doFilter(request, response);
            return;
        }

        response.sendRedirect("/2fa/verify");
    }
}