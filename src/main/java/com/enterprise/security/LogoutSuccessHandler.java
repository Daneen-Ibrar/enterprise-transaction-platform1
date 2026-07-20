package com.enterprise.security;

import com.enterprise.audit.UserActivityLogService;
import com.enterprise.identity.UserRepository;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.SimpleUrlLogoutSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class LogoutSuccessHandler extends SimpleUrlLogoutSuccessHandler {

    private final UserActivityLogService activityLogService;
    private final UserRepository userRepository;

    public LogoutSuccessHandler(UserActivityLogService activityLogService,
                                UserRepository userRepository) {
        this.activityLogService = activityLogService;
        this.userRepository = userRepository;
    }

    @Override
    public void onLogoutSuccess(HttpServletRequest request,
                                HttpServletResponse response,
                                Authentication authentication) throws IOException, ServletException {
        if (authentication != null && authentication.getName() != null) {
            userRepository.findByEmail(authentication.getName()).ifPresent(user -> {
                activityLogService.logActivity(
                        user.getId(),
                        "LOGOUT",
                        "User logged out",
                        request
                );
            });
        }
        super.onLogoutSuccess(request, response, authentication);
    }
}