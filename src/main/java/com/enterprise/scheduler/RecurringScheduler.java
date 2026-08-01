package com.enterprise.scheduler;

import com.enterprise.invoice.RecurringScheduleService;
import com.enterprise.tenant.Tenant;
import com.enterprise.tenant.TenantContext;
import com.enterprise.tenant.TenantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RecurringScheduler {

    private static final Logger log = LoggerFactory.getLogger(RecurringScheduler.class);
    private final RecurringScheduleService scheduleService;
    private final TenantRepository tenantRepository;

    public RecurringScheduler(RecurringScheduleService scheduleService,
                              TenantRepository tenantRepository) {
        this.scheduleService = scheduleService;
        this.tenantRepository = tenantRepository;
    }

    // Run every hour at minute 5 (to avoid conflict with other jobs)
    @Scheduled(cron = "0 5 * * * *")
    public void processRecurringSchedules() {
        log.info("Running recurring schedule processor");

        // Get all active tenants
        List<Tenant> tenants = tenantRepository.findAll();

        if (tenants.isEmpty()) {
            log.warn("No tenants found – skipping recurring schedule processing");
            return;
        }

        int totalCreated = 0;
        for (Tenant tenant : tenants) {
            try {
                TenantContext.setTenantId(tenant.getId());
                int created = scheduleService.processDueSchedules();
                if (created > 0) {
                    log.info("Recurring scheduler created {} invoices for tenant {} ({})", 
                            created, tenant.getId(), tenant.getName());
                    totalCreated += created;
                }
            } catch (Exception e) {
                log.error("Error processing recurring schedules for tenant {} ({})", 
                        tenant.getId(), tenant.getName(), e);
            } finally {
                TenantContext.clear();
            }
        }

        if (totalCreated > 0) {
            log.info("Recurring scheduler created {} invoices across all tenants", totalCreated);
        }
    }
}