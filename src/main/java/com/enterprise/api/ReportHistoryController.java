package com.enterprise.api;

import com.enterprise.reporting.GeneratedReport;
import com.enterprise.reporting.ReportHistoryService;
import com.enterprise.scheduler.ReportScheduler;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequestMapping("/admin/reports")
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MERCHANT_ADMIN')")
public class ReportHistoryController {

    private static final Logger log = LoggerFactory.getLogger(ReportHistoryController.class);

    private final ReportHistoryService reportHistoryService;
    private final ReportScheduler reportScheduler; // ✅ declared

    // ✅ Fix constructor to inject both dependencies
    public ReportHistoryController(ReportHistoryService reportHistoryService,
                                   ReportScheduler reportScheduler) {
        this.reportHistoryService = reportHistoryService;
        this.reportScheduler = reportScheduler;
    }

    @PostConstruct
    public void init() {
        log.info("🚀 ReportHistoryController initialized!");
    }

    @GetMapping("/history")
    public String history(@RequestParam(required = false) String type, Model model) {
        log.info("📊 Report history requested with type: {}", type);
        List<GeneratedReport> reports;
        if (type != null && !type.isEmpty()) {
            reports = reportHistoryService.getReportsForCurrentTenantByType(type);
        } else {
            reports = reportHistoryService.getReportsForCurrentTenant();
        }
        model.addAttribute("reports", reports);
        model.addAttribute("selectedType", type);
        return "admin/reports/history";
    }

    @GetMapping("/download/{id}")
    public ResponseEntity<byte[]> download(@PathVariable Long id) {
        GeneratedReport report = reportHistoryService.getReport(id);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(report.getContentType()));
        headers.setContentDispositionFormData("attachment", report.getFilename());
        headers.setContentLength(report.getContent().length);
        return ResponseEntity.ok().headers(headers).body(report.getContent());
    }

    @PostMapping("/generate")
    @ResponseBody
    public String generateNow() {
        reportScheduler.sendWeeklyReport();
        return "Weekly report generation triggered. Check /admin/reports/history in a few moments.";
    }

    // Temporary test endpoint – remove later
    @GetMapping("/ping")
    @ResponseBody
    public String ping() {
        return "Reports controller is alive!";
    }
}