package com.enterprise.outbox;

import com.enterprise.tenant.TenantContext;
import com.enterprise.webhook.WebhookDeliveryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class OutboxProcessor {

    private static final Logger log = LoggerFactory.getLogger(OutboxProcessor.class);

    private final OutboxRepository outboxRepository;
    private final WebhookDeliveryService webhookDeliveryService;
    private final TransactionTemplate transactionTemplate;
    private static final int BATCH_SIZE = 10;

    public OutboxProcessor(OutboxRepository outboxRepository,
                           WebhookDeliveryService webhookDeliveryService,
                           TransactionTemplate transactionTemplate) {
        this.outboxRepository = outboxRepository;
        this.webhookDeliveryService = webhookDeliveryService;
        this.transactionTemplate = transactionTemplate;
    }

    @Scheduled(fixedDelay = 5000)
    public void processOutboxEvents() {
        // Set a default tenant context for the background job
        // The outbox events themselves have tenant_id, but the repository query needs context
        Long originalTenant = TenantContext.getTenantId();
        try {
            // For system-wide operations, we need to run without tenant filter
            // The repository query will fetch all pending events regardless of tenant
            TenantContext.setTenantId(null);

            List<OutboxEvent> events = outboxRepository.findPendingEvents(PageRequest.of(0, BATCH_SIZE));

            if (events.isEmpty()) {
                return;
            }

            log.info("Processing {} outbox events", events.size());

            for (OutboxEvent event : events) {
                // Set tenant context for this specific event
                try {
                    // We need to get tenant ID from the event or associated data
                    // Since outbox events don't store tenant_id directly, we'll process without filter
                    TenantContext.setTenantId(null);

                    outboxRepository.markAsProcessing(event.getId());

                    boolean delivered = webhookDeliveryService.deliverWebhook(event);

                    if (delivered) {
                        outboxRepository.markAsDelivered(event.getId());
                        log.info("Outbox event delivered: id={}", event.getId());
                    } else {
                        handleFailure(event, "Delivery failed");
                    }

                } catch (Exception e) {
                    log.error("Failed to process outbox event: {}", event.getId(), e);
                    handleFailure(event, e.getMessage());
                }
            }
        } finally {
            // Restore original tenant context
            if (originalTenant != null) {
                TenantContext.setTenantId(originalTenant);
            } else {
                TenantContext.clear();
            }
        }
    }

    private void handleFailure(OutboxEvent event, String errorMessage) {
        if (isPermanentFailure(errorMessage)) {
            outboxRepository.markAsFailed(event.getId(), "Permanent failure: " + errorMessage);
            log.warn("Outbox event marked as permanent failure: id={}, error={}", event.getId(), errorMessage);
            return;
        }

        int newRetryCount = event.getRetryCount() + 1;

        if (newRetryCount >= event.getMaxRetries()) {
            outboxRepository.markAsFailed(event.getId(), errorMessage);
            log.warn("Outbox event failed permanently: id={}, retries={}", event.getId(), newRetryCount);
        } else {
            long delay = (long) (Math.pow(2, newRetryCount) * 1000);
            LocalDateTime nextAttempt = LocalDateTime.now().plusSeconds(delay / 1000);
            outboxRepository.incrementRetry(event.getId(), nextAttempt);
            log.warn("Outbox event retry scheduled: id={}, attempt={}, nextAttempt={}",
                    event.getId(), newRetryCount, nextAttempt);
        }
    }

    private boolean isPermanentFailure(String errorMessage) {
        if (errorMessage == null) return false;
        String lower = errorMessage.toLowerCase();
        return lower.contains("validation") ||
               lower.contains("invalid") ||
               lower.contains("not found") ||
               lower.contains("bad request") ||
               lower.contains("unauthorized") ||
               lower.contains("forbidden") ||
               lower.contains("400") ||
               lower.contains("401") ||
               lower.contains("403") ||
               lower.contains("404");
    }

    public long getPendingCount() {
        return outboxRepository.countPendingEvents();
    }
}