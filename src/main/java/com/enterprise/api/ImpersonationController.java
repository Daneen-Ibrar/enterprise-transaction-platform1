package com.enterprise.api;

import com.enterprise.identity.AppUser;
import com.enterprise.identity.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.stream.Collectors;

@Controller
@RequestMapping("/admin/users")
public class ImpersonationController {

    private static final Logger log = LoggerFactory.getLogger(ImpersonationController.class);

    private final UserRepository userRepository;

    public ImpersonationController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @PostMapping("/impersonate")
    public String impersonatePost(@RequestParam String username, HttpServletRequest request) {
        return impersonate(username, request);
    }

    @GetMapping("/impersonate")
    public String impersonateGet(@RequestParam String username, HttpServletRequest request) {
        return impersonate(username, request);
    }

    private String impersonate(String username, HttpServletRequest request) {
        String trimmedUsername = username.trim();
        log.info("🔍 Impersonation requested for username: '{}' (length: {})", trimmedUsername, trimmedUsername.length());

        // ✅ Use the global (tenant‑unaware) lookup
        AppUser targetUser = userRepository.findByEmailIgnoreCaseGlobal(trimmedUsername)
                .orElseThrow(() -> {
                    log.error("❌ User not found for email: '{}'", trimmedUsername);
                    return new RuntimeException("User not found");
                });

        log.info("✅ Target user found: {} (ID: {}, tenant: {}, active: {}, 2FA: {})",
                targetUser.getEmail(), targetUser.getId(), targetUser.getTenantId(),
                targetUser.isActive(), targetUser.isTwoFactorEnabled());

        if (!targetUser.isActive()) {
            log.warn("⛔ Target user is inactive: {}", targetUser.getEmail());
            throw new RuntimeException("User account is disabled");
        }

        // ✅ Build UserDetails manually from the target user
        UserDetails userDetails = User.withUsername(targetUser.getEmail())
                .password(targetUser.getPasswordHash() != null ? targetUser.getPasswordHash() : "")
                .authorities(targetUser.getRoles().stream()
                        .map(role -> new SimpleGrantedAuthority("ROLE_" + role.getName()))
                        .collect(Collectors.toList()))
                .build();

        UsernamePasswordAuthenticationToken newAuth = new UsernamePasswordAuthenticationToken(
                userDetails, null, userDetails.getAuthorities());
        newAuth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

        Authentication originalAuth = SecurityContextHolder.getContext().getAuthentication();
        HttpSession session = request.getSession();
        session.setAttribute("ORIGINAL_AUTH", originalAuth);

        SecurityContextHolder.getContext().setAuthentication(newAuth);

        log.info("🔄 Impersonation successful: {} -> {}", originalAuth.getName(), targetUser.getEmail());
        return "redirect:/dashboard";
    }

    @GetMapping("/exit-impersonation")
    public String exitImpersonation(HttpServletRequest request) {
        HttpSession session = request.getSession();
        Authentication originalAuth = (Authentication) session.getAttribute("ORIGINAL_AUTH");
        if (originalAuth != null) {
            SecurityContextHolder.getContext().setAuthentication(originalAuth);
            session.removeAttribute("ORIGINAL_AUTH");
            log.info("🔙 Exited impersonation, restored original auth: {}", originalAuth.getName());
        }
        return "redirect:/dashboard";
    }
}