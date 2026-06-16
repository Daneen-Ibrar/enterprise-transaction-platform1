package com.enterprise.api;

import com.enterprise.notification.NotificationService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping("/mark-read")
    public String markAllRead(Authentication authentication) {
        // In real implementation, map email to userId.
        Long userId = 1L; // placeholder
        notificationService.markAllAsRead(userId);
        return "redirect:/dashboard";
    }
}