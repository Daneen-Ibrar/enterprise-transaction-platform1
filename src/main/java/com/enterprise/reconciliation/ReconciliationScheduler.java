package com.enterprise.reconciliation;

import com.enterprise.tenant.Tenant;
import com.enterprise.tenant.TenantContext;
import com.enterprise.tenant.TenantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ReconciliationScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationScheduler.class);

    private final ReconciliationService reconciliationService;
    private final TenantRepository tenantRepository;

    public ReconciliationScheduler(ReconciliationService reconciliationService,
                                   TenantRepository tenantRepository) {
        this.reconciliationService = reconciliationService;
        this.tenantRepository = tenantRepository;
    }

    // Run daily at midnight
    @Scheduled(cron = "0 0 0 * * *")
    public void scheduledReconciliation() {
        log.info("Scheduled reconciliation job started");

        // ✅ FIX: Iterate over all tenants and run reconciliation per tenant
        List<Tenant> tenants = tenantRepository.findAll();
        if (tenants.isEmpty()) {
            log.warn("No tenants found – skipping reconciliation");
            return;
        }

        int successCount = 0;
        for (Tenant tenant : tenants) {
            try {
                TenantContext.setTenantId(tenant.getId());
                reconciliationService.runReconciliation();
                successCount++;
                log.info("✅ Reconciliation completed for tenant {} ({})", tenant.getId(), tenant.getName());
            } catch (Exception e) {
                log.error("❌ Reconciliation failed for tenant {} ({}): {}",
                        tenant.getId(), tenant.getName(), e.getMessage(), e);
            } finally {
                TenantContext.clear();
            }
        }

        log.info("Scheduled reconciliation job finished. Successful tenants: {}/{}",
                successCount, tenants.size());
    }
}