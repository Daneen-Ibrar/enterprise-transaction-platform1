package com.enterprise.reporting;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ReportConfigRepository extends JpaRepository<ReportConfig, Long> {
    List<ReportConfig> findByUserIdOrderByName(Long userId);
    List<ReportConfig> findByUserIdAndReportTypeOrderByName(Long userId, String reportType);
}