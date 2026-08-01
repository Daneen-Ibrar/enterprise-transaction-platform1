package com.enterprise.reporting;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface GeneratedReportRepository extends JpaRepository<GeneratedReport, Long> {
    List<GeneratedReport> findByTenantIdOrderByGeneratedAtDesc(Long tenantId);

    @Query("SELECT r FROM GeneratedReport r WHERE r.tenantId = :tenantId AND r.reportType = :reportType ORDER BY r.generatedAt DESC")
    List<GeneratedReport> findByTenantIdAndReportTypeOrderByGeneratedAtDesc(@Param("tenantId") Long tenantId, @Param("reportType") String reportType);
}