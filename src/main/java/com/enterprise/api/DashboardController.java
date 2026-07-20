package com.enterprise.api;

import com.enterprise.audit.AuditRepository;
import com.enterprise.identity.AppUser;
import com.enterprise.identity.UserRepository;
import com.enterprise.invoice.InvoiceService;
import com.enterprise.notification.NotificationService;
import com.enterprise.reconciliation.ReconciliationRecordRepository;
import com.enterprise.reliability.DlqEntryRepository;
import com.enterprise.reporting.ReportingService;
import com.enterprise.transaction.TransactionRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Controller
public class DashboardController {

    private final TransactionRepository transactionRepository;
    private final AuditRepository auditRepository;
    private final ReconciliationRecordRepository reconciliationRecordRepository;
    private final DlqEntryRepository dlqEntryRepository;
    private final NotificationService notificationService;
    private final UserRepository userRepository;
    private final InvoiceService invoiceService;
    private final ReportingService reportingService;

    public DashboardController(TransactionRepository transactionRepository,
                               AuditRepository auditRepository,
                               ReconciliationRecordRepository reconciliationRecordRepository,
                               DlqEntryRepository dlqEntryRepository,
                               NotificationService notificationService,
                               UserRepository userRepository,
                               InvoiceService invoiceService,
                               ReportingService reportingService) {
        this.transactionRepository = transactionRepository;
        this.auditRepository = auditRepository;
        this.reconciliationRecordRepository = reconciliationRecordRepository;
        this.dlqEntryRepository = dlqEntryRepository;
        this.notificationService = notificationService;
        this.userRepository = userRepository;
        this.invoiceService = invoiceService;
        this.reportingService = reportingService;
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model, Authentication authentication) {
        AppUser user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
        Long userId = user.getId();

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

        long unreadCount = notificationService.countUnread(userId);
        var recentNotifications = notificationService.getRecentNotifications(userId, 5);
        model.addAttribute("unreadCount", unreadCount);
        model.addAttribute("notifications", recentNotifications);

        long suspiciousCount = invoiceService.findAll().stream()
                .filter(inv -> inv.getRiskLevel() != null && !"GREEN".equals(inv.getRiskLevel()))
                .count();
        model.addAttribute("suspiciousCount", suspiciousCount);

        // ===== CHART DATA FOR MAIN DASHBOARD (Last 7 days) =====
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(7);

        var volume = reportingService.getDailyVolume(startDate, endDate);
        var statusDist = reportingService.getStatusDistribution(startDate, endDate);

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        List<String> labels = volume.keySet().stream().sorted().map(d -> d.format(formatter)).collect(Collectors.toList());
        List<Long> volumeData = labels.stream()
                .map(label -> volume.getOrDefault(LocalDate.parse(label, formatter), 0L))
                .collect(Collectors.toList());

        List<String> statusLabels = new ArrayList<>(statusDist.keySet());
        List<Long> statusValues = statusLabels.stream().map(statusDist::get).collect(Collectors.toList());

        model.addAttribute("dashboardLabels", labels);
        model.addAttribute("dashboardVolumeData", volumeData);
        model.addAttribute("dashboardStatusLabels", statusLabels);
        model.addAttribute("dashboardStatusValues", statusValues);

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