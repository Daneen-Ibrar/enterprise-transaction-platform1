package com.enterprise.security;

import com.enterprise.identity.AppUser;
import com.enterprise.identity.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class ActiveUserFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ActiveUserFilter.class);

    private final UserRepository userRepository;

    public ActiveUserFilter(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            String email = auth.getName();
            log.debug("🔍 ActiveUserFilter checking: {}", email);

            AppUser user = userRepository.findByEmail(email).orElse(null);

            if (user == null || !user.isActive()) {
                log.warn("❌ User inactive or not found: {}", email);
                new SecurityContextLogoutHandler().logout(request, response, auth);
                response.sendRedirect("/login?error=disabled");
                return;
            }

            // ----- CHECK ACCOUNT LOCKED -----
            if (user.isAccountLocked() && (user.getLockExpiry() == null ||
                    user.getLockExpiry().isAfter(LocalDateTime.now()))) {
                log.warn("🔒 User is locked: {}, expiry: {}", email, user.getLockExpiry());
                new SecurityContextLogoutHandler().logout(request, response, auth);
                response.sendRedirect("/login?error=locked");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}