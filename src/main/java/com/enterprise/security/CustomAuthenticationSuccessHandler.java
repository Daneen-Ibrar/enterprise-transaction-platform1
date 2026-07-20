package com.enterprise.security;

import com.enterprise.audit.UserActivityLogService;
import com.enterprise.identity.AppUser;
import com.enterprise.identity.UserRepository;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class CustomAuthenticationSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {

    private static final Logger log = LoggerFactory.getLogger(CustomAuthenticationSuccessHandler.class);

    private final UserRepository userRepository;
    private final UserActivityLogService activityLogService;
    private final LoginAttemptService loginAttemptService;

    public CustomAuthenticationSuccessHandler(UserRepository userRepository,
                                              UserActivityLogService activityLogService,
                                              LoginAttemptService loginAttemptService) {
        this.userRepository = userRepository;
        this.activityLogService = activityLogService;
        this.loginAttemptService = loginAttemptService;
        setDefaultTargetUrl("/dashboard");
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        String email = authentication.getName();
        log.info("✅ Authentication SUCCESS for user: {}", email);

        try {
            AppUser user = userRepository.findByEmail(email).orElse(null);

            if (user != null) {
                log.info("   User: {}, locked: {}, attempts: {}",
                        user.getEmail(), user.isAccountLocked(), user.getFailedLoginAttempts());

                // Reset failed attempts and unlock if needed
                loginAttemptService.loginSucceeded(user.getEmail());

                // Log activity
                activityLogService.logActivity(
                        user.getId(),
                        "LOGIN_SUCCESS",
                        "User logged in successfully",
                        request
                );

                if (user.isTwoFactorEnabled()) {
                    HttpSession session = request.getSession();
                    session.setAttribute("2FA_PENDING", true);
                    session.removeAttribute("2FA_AUTHENTICATED");
                    getRedirectStrategy().sendRedirect(request, response, "/2fa/verify");
                    return;
                }
            }
        } catch (Exception e) {
            System.err.println("Error in CustomAuthenticationSuccessHandler: " + e.getMessage());
            e.printStackTrace();
        }

        super.onAuthenticationSuccess(request, response, authentication);
    }
}