package com.enterprise.api;

import com.enterprise.audit.AuditRepository;
import com.enterprise.identity.AppUser;
import com.enterprise.identity.UserRepository;
import com.enterprise.notification.NotificationService;
import com.enterprise.reconciliation.ReconciliationRecordRepository;
import com.enterprise.reliability.DlqEntryRepository;
import com.enterprise.transaction.TransactionRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class DashboardController {

    private final TransactionRepository transactionRepository;
    private final AuditRepository auditRepository;
    private final ReconciliationRecordRepository reconciliationRecordRepository;
    private final DlqEntryRepository dlqEntryRepository;
    private final NotificationService notificationService;
    private final UserRepository userRepository;

    public DashboardController(TransactionRepository transactionRepository,
                               AuditRepository auditRepository,
                               ReconciliationRecordRepository reconciliationRecordRepository,
                               DlqEntryRepository dlqEntryRepository,
                               NotificationService notificationService,
                               UserRepository userRepository) {
        this.transactionRepository = transactionRepository;
        this.auditRepository = auditRepository;
        this.reconciliationRecordRepository = reconciliationRecordRepository;
        this.dlqEntryRepository = dlqEntryRepository;
        this.notificationService = notificationService;
        this.userRepository = userRepository;
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model, Authentication authentication) {
        // Get the logged-in user
        AppUser user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
        Long userId = user.getId();

        // Global stats (can be made role‑specific later)
        long totalTransactions = transactionRepository.count();
        long totalAuditEvents = auditRepository.count();
        long pendingDlq = dlqEntryRepository.countByStatus("PENDING");
        long reconciled = reconciliationRecordRepository.count();

        model.addAttribute("totalTransactions", totalTransactions);
        model.addAttribute("totalAuditEvents", totalAuditEvents);
        model.addAttribute("pendingDlq", pendingDlq);
        model.addAttribute("reconciled", reconciled);
        model.addAttribute("username", user.getEmail());
        model.addAttribute("roles", authentication.getAuthorities());

        // Notifications for this user
        long unreadCount = notificationService.countUnread(userId);
        var recentNotifications = notificationService.getRecentNotifications(userId, 5);

        model.addAttribute("unreadCount", unreadCount);
        model.addAttribute("notifications", recentNotifications);

        return "dashboard";
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/ping")
@ResponseBody
public String ping() {
    return "pong";
}
}