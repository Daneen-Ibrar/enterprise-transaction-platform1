package com.enterprise.reconciliation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ReconciliationScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationScheduler.class);
    private final ReconciliationService reconciliationService;

    public ReconciliationScheduler(ReconciliationService reconciliationService) {
        this.reconciliationService = reconciliationService;
    }

    // Run daily at midnight
    @Scheduled(cron = "0 0 0 * * *")
    public void scheduledReconciliation() {
        log.info("Scheduled reconciliation job started");
        try {
            reconciliationService.runReconciliation();
        } catch (Exception e) {
            log.error("Scheduled reconciliation failed", e);
        }
    }
}