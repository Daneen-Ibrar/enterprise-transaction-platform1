package com.enterprise.audit;

import com.enterprise.identity.AppUser;
import com.enterprise.identity.UserRepository;
import com.enterprise.tenant.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class UserActivityLogService {

    private static final Logger log = LoggerFactory.getLogger(UserActivityLogService.class);

    private final UserActivityLogRepository repository;
    private final UserRepository userRepository;

    public UserActivityLogService(UserActivityLogRepository repository,
                                  UserRepository userRepository) {
        this.repository = repository;
        this.userRepository = userRepository;
    }

    @Transactional
    public void logActivity(Long userId, String action, String details, HttpServletRequest request) {
        String ipAddress = getClientIp(request);
        String userAgent = request != null ? request.getHeader("User-Agent") : null;

        UserActivityLog entry = new UserActivityLog();
        entry.setUserId(userId);
        entry.setAction(action);
        entry.setDetails(details);
        entry.setIpAddress(ipAddress);
        entry.setUserAgent(userAgent);
        entry.setTenantId(TenantContext.getRequiredTenantId());

        repository.save(entry);
        log.info("Activity logged: userId={}, action={}", userId, action);
    }

    @Transactional
    public void logActivity(Long userId, String action, String details) {
        logActivity(userId, action, details, null);
    }

    public Page<UserActivityLog> getRecentActivity(Pageable pageable) {
        return repository.findAllByOrderByCreatedAtDesc(pageable);
    }

    public Page<UserActivityLog> getActivityByUser(Long userId, Pageable pageable) {
        return repository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
    }

    public Page<UserActivityLog> search(Long userId, String action, LocalDateTime startDate, LocalDateTime endDate, Pageable pageable) {
        log.info("Searching activity logs: userId={}, action={}, start={}, end={}", userId, action, startDate, endDate);

        // 👇 Get current user to check if they are Super Admin
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        AppUser currentUser = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        boolean isSuperAdmin = currentUser.getRoles().stream()
                .anyMatch(r -> r.getName().equals("SUPER_ADMIN"));

        if (isSuperAdmin) {
            // Super Admin sees ALL logs (no tenant filter)
            Specification<UserActivityLog> spec = Specification.where(null);
            if (userId != null) {
                spec = spec.and(UserActivityLogSpecifications.hasUserId(userId));
            }
            if (action != null && !action.isEmpty()) {
                spec = spec.and(UserActivityLogSpecifications.hasAction(action));
            }
            if (startDate != null) {
                spec = spec.and(UserActivityLogSpecifications.createdAtAfter(startDate));
            }
            if (endDate != null) {
                spec = spec.and(UserActivityLogSpecifications.createdAtBefore(endDate));
            }
            Page<UserActivityLog> result = repository.findAll(spec, pageable);
            log.info("Found {} logs (total pages {})", result.getTotalElements(), result.getTotalPages());
            return result;
        } else {
            // Merchant Admin – only their own tenant
            Long tenantId = TenantContext.getRequiredTenantId();
            Page<UserActivityLog> result = repository.findAllByTenantId(tenantId, pageable);
            log.info("Found {} logs (total pages {})", result.getTotalElements(), result.getTotalPages());
            return result;
        }
    }

    public List<String> getDistinctActions() {
        return repository.findDistinctActions();
    }

    private String getClientIp(HttpServletRequest request) {
        if (request == null) return null;
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}