package com.enterprise.api;

import com.enterprise.identity.AppUser;
import com.enterprise.identity.UserRepository;
import com.enterprise.reporting.ReportConfig;
import com.enterprise.reporting.ReportConfigService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/api/reports")
public class ReportConfigController {

    private final ReportConfigService reportConfigService;
    private final UserRepository userRepository;

    public ReportConfigController(ReportConfigService reportConfigService,
                                  UserRepository userRepository) {
        this.reportConfigService = reportConfigService;
        this.userRepository = userRepository;
    }

    private Long getUserId(Authentication auth) {
        AppUser user = userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
        return user.getId();
    }

    @PostMapping("/save")
    public ReportConfig saveReport(@RequestParam String name,
                                   @RequestParam String reportType,
                                   @RequestBody Map<String, Object> filters,
                                   Authentication authentication) {
        Long userId = getUserId(authentication);
        return reportConfigService.saveReport(name, reportType, filters, userId);
    }

    @GetMapping
    public List<ReportConfig> listReports(Authentication authentication) {
        Long userId = getUserId(authentication);
        return reportConfigService.getUserReports(userId);
    }

    @GetMapping("/type/{reportType}")
    public List<ReportConfig> listReportsByType(@PathVariable String reportType,
                                                Authentication authentication) {
        Long userId = getUserId(authentication);
        return reportConfigService.getUserReportsByType(userId, reportType);
    }

    @GetMapping("/{id}")
    public Map<String, Object> getReport(@PathVariable Long id, Authentication authentication) {
        Long userId = getUserId(authentication);
        ReportConfig config = reportConfigService.getReport(id, userId);
        return reportConfigService.deserializeFilters(config);
    }

    @DeleteMapping("/{id}")
    public void deleteReport(@PathVariable Long id, Authentication authentication) {
        Long userId = getUserId(authentication);
        reportConfigService.deleteReport(id, userId);
    }
}