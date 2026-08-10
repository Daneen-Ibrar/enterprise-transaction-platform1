package com.enterprise.scheduler;

import com.enterprise.identity.AppUser;
import com.enterprise.identity.UserRepository;
import com.enterprise.notification.NotificationService;
import com.enterprise.reporting.PdfExportService;
import com.enterprise.reporting.ReportHistoryService;
import com.enterprise.reporting.ReportingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
public class ReportScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReportScheduler.class);

    private final ReportingService reportingService;
    private final PdfExportService pdfExportService;
    private final ReportHistoryService reportHistoryService;
    private final NotificationService notificationService;
    private final UserRepository userRepository;

    public ReportScheduler(ReportingService reportingService,
                           PdfExportService pdfExportService,
                           ReportHistoryService reportHistoryService,
                           NotificationService notificationService,
                           UserRepository userRepository) {
        this.reportingService = reportingService;
        this.pdfExportService = pdfExportService;
        this.reportHistoryService = reportHistoryService;
        this.notificationService = notificationService;
        this.userRepository = userRepository;
    }

    // Weekly report – every Monday at 9 AM
    @Scheduled(cron = "0 0 9 * * MON")
    public void sendWeeklyReport() {
        log.info("Generating weekly report...");
        // For scheduled reports, we use tenant 1 as default
        Long tenantId = 1L;
        generateAndStoreReport("WEEKLY", 7, tenantId);
    }

    // Daily summary – every day at 6 PM
    @Scheduled(cron = "0 0 18 * * *")
    public void sendDailySummary() {
        log.info("Generating daily summary...");
        Long tenantId = 1L;
        generateAndStoreReport("DAILY", 1, tenantId);
    }

    private void generateAndStoreReport(String reportType, int daysBack, Long tenantId) {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(daysBack);
        String dateRange = startDate.format(DateTimeFormatter.ISO_LOCAL_DATE) + " to " + endDate.format(DateTimeFormatter.ISO_LOCAL_DATE);

        try {
            // Generate PDF – 👈 now passing tenantId
            byte[] pdfBytes = pdfExportService.generateReport(startDate, endDate, tenantId).toByteArray();

            // Store in database
            String filename = reportType.toLowerCase() + "_report_" + startDate + ".pdf";
            var report = reportHistoryService.saveReport(
                    filename,
                    reportType,
                    startDate,
                    endDate,
                    pdfBytes,
                    "application/pdf"
            );

            // Notify admins
            List<AppUser> admins = userRepository.findByRolesName("ADMIN");
            if (admins.isEmpty()) {
                log.warn("No admin users found to notify.");
                return;
            }

            String title = reportType + " Report Ready";
            String message = "Your " + reportType.toLowerCase() + " report (" + dateRange + ") is ready to download.";
            String link = "/admin/reports/history";

            for (AppUser admin : admins) {
                notificationService.createNotification(
                        admin.getId(),
                        "REPORT_READY",
                        title,
                        message,
                        link
                );
                log.info("Notification sent to admin: {}", admin.getEmail());
            }

            log.info("Report stored and notifications sent for {}", reportType);

        } catch (Exception e) {
            log.error("Failed to generate/store {} report: {}", reportType, e.getMessage(), e);
        }
    }
}