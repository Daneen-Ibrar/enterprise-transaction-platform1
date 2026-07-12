package com.enterprise.security;

import com.enterprise.identity.AppUser;
import com.enterprise.identity.UserRepository;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class CustomAuthenticationSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {

    private final UserRepository userRepository;

    public CustomAuthenticationSuccessHandler(UserRepository userRepository) {
        this.userRepository = userRepository;
        setDefaultTargetUrl("/dashboard");
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        String email = authentication.getName();
        try {
            AppUser user = userRepository.findByEmail(email).orElse(null);

            if (user != null && user.isTwoFactorEnabled()) {
                HttpSession session = request.getSession();
                session.setAttribute("2FA_PENDING", true);
                session.removeAttribute("2FA_AUTHENTICATED");
                getRedirectStrategy().sendRedirect(request, response, "/2fa/verify");
                return;
            }
        } catch (Exception e) {
            // Log the error and fallback to default target
            System.err.println("Error in CustomAuthenticationSuccessHandler: " + e.getMessage());
            e.printStackTrace();
        }

        // Default: go to dashboard
        super.onAuthenticationSuccess(request, response, authentication);
    }
}