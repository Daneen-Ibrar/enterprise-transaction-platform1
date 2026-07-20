package com.enterprise.config;

import com.enterprise.feature.FeatureFlagService;
import com.enterprise.security.ApiKeyAuthenticationFilter;
import com.enterprise.security.AuthenticationFailureHandler;
import com.enterprise.security.CustomAuthenticationSuccessHandler;
import com.enterprise.security.CustomPermissionEvaluator;
import com.enterprise.security.LogoutSuccessHandler;
import com.enterprise.security.RateLimitingFilter;
import com.enterprise.security.TwoFactorAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
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
    private final AuthenticationFailureHandler authenticationFailureHandler;
    private final LogoutSuccessHandler logoutSuccessHandler;
    private final ApiKeyAuthenticationFilter apiKeyAuthenticationFilter;
    private final RateLimitingFilter rateLimitingFilter;
    private final FeatureFlagService featureFlagService;

    public SecurityConfig(CustomPermissionEvaluator customPermissionEvaluator,
                          CustomAuthenticationSuccessHandler customAuthenticationSuccessHandler,
                          AuthenticationFailureHandler authenticationFailureHandler,
                          LogoutSuccessHandler logoutSuccessHandler,
                          ApiKeyAuthenticationFilter apiKeyAuthenticationFilter,
                          RateLimitingFilter rateLimitingFilter,
                          FeatureFlagService featureFlagService) {
        this.customPermissionEvaluator = customPermissionEvaluator;
        this.customAuthenticationSuccessHandler = customAuthenticationSuccessHandler;
        this.authenticationFailureHandler = authenticationFailureHandler;
        this.logoutSuccessHandler = logoutSuccessHandler;
        this.apiKeyAuthenticationFilter = apiKeyAuthenticationFilter;
        this.rateLimitingFilter = rateLimitingFilter;
        this.featureFlagService = featureFlagService;
    }

    // ===== API Security Chain (Stateless, No CSRF, API Key Auth) =====
    @Bean
    @Order(1)
    public SecurityFilterChain apiFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/api/**")
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/public/**").permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(apiKeyAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(rateLimitingFilter, ApiKeyAuthenticationFilter.class);

        return http.build();
    }

    // ===== UI Security Chain (Session-based, CSRF Enabled) =====
    @Bean
    @Order(2)
    public SecurityFilterChain uiFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/**")
            .csrf(csrf -> csrf.ignoringRequestMatchers("/api/**", "/admin/**", "/login", "/invoices/**", "/pay/**"))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/login", "/css/**", "/health/**", "/pay/**", "/test/email",
                                 "/2fa/**", "/swagger-ui/**", "/v3/api-docs/**", "/actuator/health",
                                 "/actuator/info", "/actuator/metrics", "/actuator/prometheus").permitAll()
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .successHandler(customAuthenticationSuccessHandler)
                .failureHandler(authenticationFailureHandler)
                .permitAll()
            )
            .logout(logout -> logout
                .logoutSuccessHandler(logoutSuccessHandler)
                .permitAll()
            )
            .addFilterAfter(new TwoFactorAuthenticationFilter(featureFlagService), UsernamePasswordAuthenticationFilter.class);

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
