package com.enterprise.reporting;

import com.enterprise.tenant.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class ReportHistoryService {

    private final GeneratedReportRepository repository;

    public ReportHistoryService(GeneratedReportRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public GeneratedReport saveReport(String filename, String reportType, LocalDate startDate, LocalDate endDate,
                                      byte[] content, String contentType) {
        GeneratedReport report = new GeneratedReport();
        report.setFilename(filename);
        report.setReportType(reportType);
        report.setStartDate(startDate);
        report.setEndDate(endDate);
        report.setContent(content);
        report.setContentType(contentType);
        report.setTenantId(TenantContext.getRequiredTenantId());
        return repository.save(report);
    }

    public List<GeneratedReport> getReportsForCurrentTenant() {
        Long tenantId = TenantContext.getRequiredTenantId();
        return repository.findByTenantIdOrderByGeneratedAtDesc(tenantId);
    }

    public List<GeneratedReport> getReportsForCurrentTenantByType(String reportType) {
        Long tenantId = TenantContext.getRequiredTenantId();
        return repository.findByTenantIdAndReportTypeOrderByGeneratedAtDesc(tenantId, reportType);
    }

    public GeneratedReport getReport(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Report not found"));
    }
}