package com.enterprise.api;

import com.enterprise.audit.UserActivityLog;
import com.enterprise.audit.UserActivityLogService;
import com.enterprise.identity.AppUser;
import com.enterprise.identity.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/admin/activity")
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MERCHANT_ADMIN')")
public class AdminActivityController {

    private static final Logger log = LoggerFactory.getLogger(AdminActivityController.class);

    private final UserActivityLogService activityLogService;
    private final UserRepository userRepository;

    public AdminActivityController(UserActivityLogService activityLogService,
                                   UserRepository userRepository) {
        this.activityLogService = activityLogService;
        this.userRepository = userRepository;
    }

    @GetMapping
    public String listActivity(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Model model,
            Authentication authentication) {

        log.info("Filtering activity logs: userId={}, action={}, start={}, end={}", userId, action, startDate, endDate);

        AppUser admin = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));

        LocalDateTime start = startDate != null ? startDate.atStartOfDay() : null;
        LocalDateTime end = endDate != null ? endDate.atTime(23, 59, 59) : null;

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<UserActivityLog> logs = activityLogService.search(userId, action, start, end, pageable);

        // Get all users for dropdown (tenant-scoped unless Super Admin)
        boolean isSuperAdmin = admin.getRoles().stream().anyMatch(r -> r.getName().equals("SUPER_ADMIN"));
        List<AppUser> users;
        if (isSuperAdmin) {
            users = userRepository.findAll();
        } else {
            users = userRepository.findAll().stream()
                    .filter(u -> u.getTenantId().equals(admin.getTenantId()))
                    .collect(Collectors.toList());
        }

        List<String> actions = activityLogService.getDistinctActions();

        // Enrich logs with user emails
        for (UserActivityLog logEntry : logs.getContent()) {
            userRepository.findById(logEntry.getUserId())
                    .ifPresent(u -> logEntry.setUserEmail(u.getEmail()));
        }

        model.addAttribute("logs", logs);
        model.addAttribute("users", users);
        model.addAttribute("actions", actions);
        model.addAttribute("selectedUserId", userId);
        model.addAttribute("selectedAction", action);
        model.addAttribute("startDate", startDate);
        model.addAttribute("endDate", endDate);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", logs.getTotalPages());
        model.addAttribute("isSuperAdmin", isSuperAdmin);

        return "admin/activity/list";
    }
}