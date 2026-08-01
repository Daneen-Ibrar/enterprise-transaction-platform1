package com.enterprise.security;

import com.enterprise.audit.UserActivityLogService;
import com.enterprise.identity.UserRepository;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;

@Component
public class AuthenticationFailureHandler extends SimpleUrlAuthenticationFailureHandler {

    private static final Logger log = LoggerFactory.getLogger(AuthenticationFailureHandler.class);

    private final UserActivityLogService activityLogService;
    private final UserRepository userRepository;
    private final LoginAttemptService loginAttemptService;

    public AuthenticationFailureHandler(UserActivityLogService activityLogService,
                                        UserRepository userRepository,
                                        LoginAttemptService loginAttemptService) {
        this.activityLogService = activityLogService;
        this.userRepository = userRepository;
        this.loginAttemptService = loginAttemptService;
        setDefaultFailureUrl("/login?error");
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request,
                                        HttpServletResponse response,
                                        AuthenticationException exception) throws IOException, ServletException {
        String username = request.getParameter("username");
        log.info("❌ Authentication FAILURE for user: {}", username);
        log.info("   Exception: {}", exception.getClass().getSimpleName());

        if (username != null && !username.isEmpty()) {
            // Log failure attempt
            userRepository.findByEmail(username).ifPresent(user -> {
                activityLogService.logActivity(
                        user.getId(),
                        "LOGIN_FAILURE",
                        "Failed login attempt: " + exception.getMessage(),
                        request
                );
            });

            // Increment failed attempts and lock if needed
            loginAttemptService.loginFailed(username);

            // Check if the account is now locked
            userRepository.findByEmail(username).ifPresent(user -> {
                if (user.isAccountLocked() && (user.getLockExpiry() == null ||
                        user.getLockExpiry().isAfter(LocalDateTime.now()))) {
                    setDefaultFailureUrl("/login?error=locked");
                    log.info("🔒 Redirecting to /login?error=locked for user {}", username);
                } else {
                    setDefaultFailureUrl("/login?error");
                }
            });
        }
        super.onAuthenticationFailure(request, response, exception);
    }
}