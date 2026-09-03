package com.enterprise.reporting;

import com.enterprise.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class ReportHistoryService {

    private static final Logger log = LoggerFactory.getLogger(ReportHistoryService.class);

    private final GeneratedReportRepository reportRepository;

    public ReportHistoryService(GeneratedReportRepository reportRepository) {
        this.reportRepository = reportRepository;
    }

    @Transactional
    public GeneratedReport saveReport(String filename, String reportType,
                                      LocalDate startDate, LocalDate endDate,
                                      byte[] content, String contentType) {
        Long tenantId = TenantContext.getRequiredTenantId();

        GeneratedReport report = new GeneratedReport();
        report.setFilename(filename);
        report.setReportType(reportType);
        report.setStartDate(startDate);
        report.setEndDate(endDate);
        report.setContentType(contentType);
        report.setTenantId(tenantId);
        report.setGeneratedAt(LocalDateTime.now());
        report.setContent(content);

        return reportRepository.save(report);
    }

    public List<GeneratedReport> getReportsForCurrentTenant() {
        Long tenantId = TenantContext.getRequiredTenantId();
        return reportRepository.findByTenantIdOrderByGeneratedAtDesc(tenantId);
    }

    public List<GeneratedReport> getReportsForCurrentTenantByType(String reportType) {
        Long tenantId = TenantContext.getRequiredTenantId();
        return reportRepository.findByTenantIdAndReportTypeOrderByGeneratedAtDesc(tenantId, reportType);
    }

    public GeneratedReport getReport(Long id) {
        return reportRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Report not found"));
    }

    public byte[] getReportContent(Long id) {
        GeneratedReport report = getReport(id);
        return report.getContent();
    }

    @Transactional
    public void deleteReport(Long id) {
        GeneratedReport report = getReport(id);
        reportRepository.delete(report);
        log.info("✅ Report deleted: {}", id);
    }
}