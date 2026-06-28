package com.enterprise.config;

import com.enterprise.security.ApiKeyAuthenticationFilter;
import com.enterprise.security.CustomAuthenticationSuccessHandler;
import com.enterprise.security.CustomPermissionEvaluator;
import com.enterprise.security.RateLimitingFilter;
import com.enterprise.security.TwoFactorAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    private final CustomPermissionEvaluator customPermissionEvaluator;
    private final CustomAuthenticationSuccessHandler customAuthenticationSuccessHandler;
    private final ApiKeyAuthenticationFilter apiKeyAuthenticationFilter;
    private final RateLimitingFilter rateLimitingFilter;

    public SecurityConfig(CustomPermissionEvaluator customPermissionEvaluator,
                          CustomAuthenticationSuccessHandler customAuthenticationSuccessHandler,
                          ApiKeyAuthenticationFilter apiKeyAuthenticationFilter,
                          RateLimitingFilter rateLimitingFilter) {
        this.customPermissionEvaluator = customPermissionEvaluator;
        this.customAuthenticationSuccessHandler = customAuthenticationSuccessHandler;
        this.apiKeyAuthenticationFilter = apiKeyAuthenticationFilter;
        this.rateLimitingFilter = rateLimitingFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.ignoringRequestMatchers("/api/**", "/admin/**", "/login", "/invoices/**", "/pay/**", "/api/public/**"))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/login", "/css/**", "/health/**", "/pay/**", "/test/email",
                                 "/2fa/**", "/api/public/**", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .successHandler(customAuthenticationSuccessHandler)
                .permitAll()
            )
            .logout(logout -> logout
                .logoutSuccessUrl("/login?logout")
                .permitAll()
            )
            // Add 2FA filter
            .addFilterAfter(new TwoFactorAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class)
            // Add API Key authentication and rate limiting filters
            .addFilterBefore(apiKeyAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(rateLimitingFilter, ApiKeyAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public MethodSecurityExpressionHandler methodSecurityExpressionHandler() {
        DefaultMethodSecurityExpressionHandler handler = new DefaultMethodSecurityExpressionHandler();
        handler.setPermissionEvaluator(customPermissionEvaluator);
        return handler;
    }
}