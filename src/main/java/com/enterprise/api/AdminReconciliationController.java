package com.enterprise.api;

import com.enterprise.reconciliation.ReconciliationDetail;
import com.enterprise.reconciliation.ReconciliationRecord;
import com.enterprise.reconciliation.ReconciliationService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/reconciliation")
public class AdminReconciliationController {

    private final ReconciliationService reconciliationService;

    public AdminReconciliationController(ReconciliationService reconciliationService) {
        this.reconciliationService = reconciliationService;
    }

    @PostMapping("/run")
    @PreAuthorize("hasPermission(null, 'reconciliation:run')")
    public ReconciliationRecord runReconciliation() {
        return reconciliationService.runReconciliation();
    }

    @GetMapping("/reports")
    @PreAuthorize("hasPermission(null, 'reconciliation:view_report')")
    public List<ReconciliationRecord> getRecentReports() {
        return reconciliationService.getRecentReports();
    }

    @GetMapping("/reports/{id}/details")
    @PreAuthorize("hasPermission(null, 'reconciliation:view_report')")
    public List<ReconciliationDetail> getDetails(@PathVariable Long id) {
        return reconciliationService.getDetailsForRecord(id);
    }
}