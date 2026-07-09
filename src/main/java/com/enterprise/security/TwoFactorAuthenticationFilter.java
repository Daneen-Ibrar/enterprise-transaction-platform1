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

    private final FeatureFlagService featureFlagService;   // <-- ADDED

    public TwoFactorAuthenticationFilter(FeatureFlagService featureFlagService) {   // <-- ADDED
        this.featureFlagService = featureFlagService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // ----- FEATURE FLAG CHECK -----
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

        Boolean pending = (Boolean) session.getAttribute("2FA_PENDING");
        Boolean authenticated = (Boolean) session.getAttribute("2FA_AUTHENTICATED");

        // If 2FA is not pending or already verified, proceed
        if (pending == null || !pending || (authenticated != null && authenticated)) {
            filterChain.doFilter(request, response);
            return;
        }

        String uri = request.getRequestURI();

        // Allow POST to /2fa/verify to pass through
        if (uri.equals("/2fa/verify") && "POST".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        // Public paths (GET only)
        if (uri.startsWith("/2fa/verify") || uri.startsWith("/2fa/setup") || uri.startsWith("/2fa/enable") ||
            uri.startsWith("/2fa/disable") || uri.startsWith("/logout") || uri.startsWith("/css/") || uri.startsWith("/js/")) {
            filterChain.doFilter(request, response);
            return;
        }

        // Otherwise redirect to 2FA verification
        response.sendRedirect("/2fa/verify");
    }
}