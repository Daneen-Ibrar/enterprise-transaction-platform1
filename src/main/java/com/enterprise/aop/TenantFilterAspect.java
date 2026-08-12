package com.enterprise.aop;

import com.enterprise.tenant.TenantContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.hibernate.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Aspect
@Component
@Order(1) // Ensure this runs early in the interceptor chain
public class TenantFilterAspect {

    private static final Logger log = LoggerFactory.getLogger(TenantFilterAspect.class);

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Enable tenant filter before ANY repository method execution.
     * Super Admin is exempt from tenant filtering.
     * Uses Authentication authorities to avoid database calls (prevents infinite recursion).
     */
    @Before("execution(* com.enterprise..*Repository.*(..))")
    public void enableTenantFilter() {
        Long tenantId = TenantContext.getTenantId();

        // Check if current user is Super Admin WITHOUT hitting the database
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            boolean isSuperAdmin = auth.getAuthorities().stream()
                    .anyMatch(grantedAuthority -> grantedAuthority.getAuthority().equals("ROLE_SUPER_ADMIN"));
            if (isSuperAdmin) {
                log.debug("Super Admin detected – skipping tenant filter");
                return;
            }
        }

        // Only apply filter if tenant ID is present
        if (tenantId != null) {
            try {
                Session session = entityManager.unwrap(Session.class);
                session.enableFilter("tenantFilter").setParameter("tenantId", tenantId);
                log.debug("Tenant filter enabled for tenant: {}", tenantId);
            } catch (Exception e) {
                log.warn("Failed to enable tenant filter: {}", e.getMessage());
            }
        } else {
            log.debug("No tenant ID in context, skipping tenant filter for this query");
        }
    }

    /**
     * Alternative: Around advice to ensure filter is properly cleaned up
     * after method execution (prevents filter leakage between requests).
     */
    @Around("execution(* com.enterprise..*Repository.*(..))")
    public Object aroundRepositoryMethod(ProceedingJoinPoint joinPoint) throws Throwable {
        try {
            // Enable filter before method execution
            enableTenantFilter();
            return joinPoint.proceed();
        } finally {
            // Clean up tenant filter after method execution to prevent leakage
            try {
                Session session = entityManager.unwrap(Session.class);
                session.disableFilter("tenantFilter");
            } catch (Exception e) {
                // Ignore cleanup errors
            }
        }
    }
}