package com.enterprise.audit;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class UserActivityLogService {

    private static final Logger log = LoggerFactory.getLogger(UserActivityLogService.class);

    private final UserActivityLogRepository repository;

    public UserActivityLogService(UserActivityLogRepository repository) {
        this.repository = repository;
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