package com.enterprise.api;

import com.enterprise.audit.AuditRepository;
import com.enterprise.currency.ExchangeRateService;
import com.enterprise.identity.AppUser;
import com.enterprise.identity.UserRepository;
import com.enterprise.invoice.Invoice;
import com.enterprise.invoice.InvoiceRepository;
import com.enterprise.invoice.InvoiceService;
import com.enterprise.notification.NotificationService;
import com.enterprise.reconciliation.ReconciliationRecordRepository;
import com.enterprise.reliability.DlqEntryRepository;
import com.enterprise.reporting.ReportingService;
import com.enterprise.tenant.Tenant;
import com.enterprise.tenant.TenantRepository;
import com.enterprise.transaction.Transaction;
import com.enterprise.transaction.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Controller
public class DashboardController {

    private static final Logger log = LoggerFactory.getLogger(DashboardController.class);

    private final TransactionRepository transactionRepository;
    private final AuditRepository auditRepository;
    private final ReconciliationRecordRepository reconciliationRecordRepository;
    private final DlqEntryRepository dlqEntryRepository;
    private final NotificationService notificationService;
    private final UserRepository userRepository;
    private final InvoiceService invoiceService;
    private final ReportingService reportingService;
    private final InvoiceRepository invoiceRepository;
    private final TenantRepository tenantRepository;
    private final ExchangeRateService exchangeRateService;

    public DashboardController(TransactionRepository transactionRepository,
                               AuditRepository auditRepository,
                               ReconciliationRecordRepository reconciliationRecordRepository,
                               DlqEntryRepository dlqEntryRepository,
                               NotificationService notificationService,
                               UserRepository userRepository,
                               InvoiceService invoiceService,
                               ReportingService reportingService,
                               InvoiceRepository invoiceRepository,
                               TenantRepository tenantRepository,
                               ExchangeRateService exchangeRateService) {
        this.transactionRepository = transactionRepository;
        this.auditRepository = auditRepository;
        this.reconciliationRecordRepository = reconciliationRecordRepository;
        this.dlqEntryRepository = dlqEntryRepository;
        this.notificationService = notificationService;
        this.userRepository = userRepository;
        this.invoiceService = invoiceService;
        this.reportingService = reportingService;
        this.invoiceRepository = invoiceRepository;
        this.tenantRepository = tenantRepository;
        this.exchangeRateService = exchangeRateService;
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model, Authentication authentication) {
        AppUser user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
        Long userId = user.getId();

        // Common stats
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

        // Suspicious invoices (admin only)
        if (user.getRoles().stream().anyMatch(r -> r.getName().equals("ADMIN"))) {
            long suspiciousCount = invoiceService.findAll().stream()
                    .filter(inv -> inv.getRiskLevel() != null && !"GREEN".equals(inv.getRiskLevel()))
                    .count();
            model.addAttribute("suspiciousCount", suspiciousCount);
        }

        // ===== MERCHANT & CUSTOMER SPECIFIC DATA =====
        boolean isMerchant = user.getRoles().stream().anyMatch(r -> r.getName().equals("MERCHANT"));
        boolean isCustomer = user.getRoles().stream().anyMatch(r -> r.getName().equals("CUSTOMER"));

        if (isMerchant) {
            List<Invoice> recentInvoices = invoiceRepository.findByMerchantIdOrderByCreatedAtDesc(userId)
                    .stream().limit(5).collect(Collectors.toList());
            model.addAttribute("recentInvoices", recentInvoices);

            List<Transaction> recentTransactions = transactionRepository.findByMerchantIdOrderByCreatedAtDesc(userId)
                    .stream().limit(5).collect(Collectors.toList());
            model.addAttribute("recentTransactions", recentTransactions);

            List<Invoice> recentInvoicesForEmails = invoiceRepository.findByMerchantIdOrderByCreatedAtDesc(userId)
                    .stream().limit(20).collect(Collectors.toList());
            List<String> recentEmails = recentInvoicesForEmails.stream()
                    .map(Invoice::getCustomerEmail)
                    .distinct()
                    .limit(5)
                    .collect(Collectors.toList());
            model.addAttribute("recentEmails", recentEmails);

            long pendingApprovalCount = invoiceRepository.findByMerchantIdAndStatus(userId, "PENDING_APPROVAL").size();
            model.addAttribute("pendingApprovalCount", pendingApprovalCount);
        }

        if (isCustomer) {
            List<Invoice> recentInvoices = invoiceRepository.findByCustomerEmailOrderByCreatedAtDesc(user.getEmail())
                    .stream().limit(5).collect(Collectors.toList());
            model.addAttribute("recentInvoices", recentInvoices);

            List<Transaction> recentTransactions = transactionRepository.findByCustomerIdOrderByCreatedAtDesc(userId)
                    .stream().limit(5).collect(Collectors.toList());
            model.addAttribute("recentTransactions", recentTransactions);
        }

        // ===== MULTI-CURRENCY DASHBOARD =====
        // Get tenant base currency
        Tenant tenant = tenantRepository.findById(user.getTenantId()).orElse(null);
        String baseCurrency = (tenant != null && tenant.getBaseCurrency() != null) ? tenant.getBaseCurrency() : "GBP";
        model.addAttribute("baseCurrency", baseCurrency);

        // Convert total transaction volume to base currency
        BigDecimal totalVolume = BigDecimal.ZERO;
        for (Transaction tx : transactionRepository.findAll()) {
            totalVolume = totalVolume.add(exchangeRateService.convert(tx.getAmount(), tx.getCurrency(), baseCurrency));
        }
        model.addAttribute("totalVolume", totalVolume);

        // Chart data (convert daily volumes to base currency)
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(7);
        var volume = reportingService.getDailyVolume(startDate, endDate);
        var statusDist = reportingService.getStatusDistribution(startDate, endDate);

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        List<String> labels = volume.keySet().stream().sorted().map(d -> d.format(formatter)).collect(Collectors.toList());
        List<BigDecimal> volumeData = labels.stream()
                .map(label -> {
                    LocalDate date = LocalDate.parse(label, formatter);
                    BigDecimal dailyTotal = BigDecimal.ZERO;
                    // Sum transactions for that day (simplified – in real app, use query)
                    for (Transaction tx : transactionRepository.findAll()) {
                        if (tx.getCreatedAt().toLocalDate().equals(date)) {
                            dailyTotal = dailyTotal.add(exchangeRateService.convert(tx.getAmount(), tx.getCurrency(), baseCurrency));
                        }
                    }
                    return dailyTotal;
                })
                .collect(Collectors.toList());

        List<String> statusLabels = new ArrayList<>(statusDist.keySet());
        List<Long> statusValues = statusLabels.stream().map(statusDist::get).collect(Collectors.toList());

        model.addAttribute("dashboardLabels", labels);
        model.addAttribute("dashboardVolumeData", volumeData);
        model.addAttribute("dashboardStatusLabels", statusLabels);
        model.addAttribute("dashboardStatusValues", statusValues);

        return "dashboard";
    }

    @PostMapping("/dashboard/invoice")
    public String createInvoice(
            @RequestParam String customerEmail,
            @RequestParam BigDecimal amount,
            @RequestParam String description,
            @RequestParam(defaultValue = "GBP") String currency,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {

        AppUser merchant = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (merchant.getRoles().stream().noneMatch(r -> r.getName().equals("MERCHANT"))) {
            redirectAttributes.addFlashAttribute("error", "Only merchants can create invoices.");
            return "redirect:/dashboard";
        }

        try {
            Invoice invoice = invoiceService.createInvoice(
                    amount,
                    description,
                    customerEmail,
                    merchant.getId(),
                    false,
                    currency
            );
            redirectAttributes.addFlashAttribute("success", "Invoice #" + invoice.getId() + " created successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to create invoice: " + e.getMessage());
        }

        return "redirect:/dashboard";
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