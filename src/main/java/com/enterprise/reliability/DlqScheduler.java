package com.enterprise.reliability;

import com.enterprise.audit.AuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class DlqScheduler {

    private static final Logger log = LoggerFactory.getLogger(DlqScheduler.class);

    private final DlqEntryRepository dlqEntryRepository;
    private final DlqProcessorService dlqProcessorService;
    private final AuditService auditService;

    @Value("${app.dlq.max-retry-attempts:3}")
    private int maxRetryAttempts;

    @Value("${app.dlq.batch-size:10}")
    private int batchSize;

    public DlqScheduler(DlqEntryRepository dlqEntryRepository,
                        DlqProcessorService dlqProcessorService,
                        AuditService auditService) {
        this.dlqEntryRepository = dlqEntryRepository;
        this.dlqProcessorService = dlqProcessorService;
        this.auditService = auditService;
    }

    // Runs every 5 minutes by default – configurable via cron
    @Scheduled(cron = "${app.dlq.scheduler.cron:0 */5 * * * *}")
    public void processDlq() {
        log.info("Starting scheduled DLQ processing");

        // Fetch pending entries that haven't exceeded max retry attempts
        List<DlqEntry> pending = dlqEntryRepository.findByStatusAndFailureCountLessThan("PENDING", maxRetryAttempts);

        if (pending.isEmpty()) {
            log.info("No pending DLQ entries to process");
            return;
        }

        log.info("Found {} pending DLQ entries to process", pending.size());

        // Process in batches
        int processed = 0;
        int success = 0;
        int failed = 0;

        for (DlqEntry entry : pending) {
            if (processed >= batchSize) {
                log.info("Reached batch size limit ({}), stopping for this run", batchSize);
                break;
            }
            try {
                log.info("Processing DLQ entry {}", entry.getId());
                dlqProcessorService.retryDlq(entry.getId());
                success++;
                auditService.recordEvent(
                    "DLQ_RETRY_SUCCESS",
                    null, // no specific user
                    Map.of("dlqEntryId", entry.getId(), "operationType", entry.getOperationType())
                );
            } catch (Exception e) {
                failed++;
                log.error("Failed to process DLQ entry {}", entry.getId(), e);
                // Increment failure count
                entry.setFailureCount(entry.getFailureCount() + 1);
                if (entry.getFailureCount() >= maxRetryAttempts) {
                    entry.setStatus("FAILED");
                    log.warn("DLQ entry {} moved to FAILED after {} attempts", entry.getId(), maxRetryAttempts);
                    auditService.recordEvent(
                        "DLQ_RETRY_FAILED",
                        null,
                        Map.of("dlqEntryId", entry.getId(), "reason", e.getMessage())
                    );
                } else {
                    entry.setStatus("PENDING");
                }
                dlqEntryRepository.save(entry);
            }
            processed++;
        }

        log.info("DLQ processing completed: processed={}, success={}, failed={}", processed, success, failed);
    }
}