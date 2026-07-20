package com.enterprise.webhook;

import com.enterprise.events.RefundProcessedEvent;
import com.enterprise.events.TransactionSettledEvent;
import com.enterprise.outbox.OutboxService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class WebhookService {

    private static final Logger log = LoggerFactory.getLogger(WebhookService.class);

    private final WebhookRepository webhookRepository;
    private final OutboxService outboxService;
    private final ObjectMapper objectMapper;

    public WebhookService(WebhookRepository webhookRepository,
                          OutboxService outboxService,
                          ObjectMapper objectMapper) {
        this.webhookRepository = webhookRepository;
        this.outboxService = outboxService;
        this.objectMapper = objectMapper;
    }

    @Async
    @EventListener
    public void onTransactionSettled(TransactionSettledEvent event) {
        log.info("Transaction settled event received: {}", event.getTransaction().getId());
        createOutboxEvent("TRANSACTION_SETTLED", event.getTransaction());
    }

    @Async
    @EventListener
    public void onRefundProcessed(RefundProcessedEvent event) {
        log.info("Refund processed event received: {}", event.getRefundTransaction().getId());
        createOutboxEvent("REFUND_PROCESSED", event.getRefundTransaction());
    }

    private void createOutboxEvent(String eventType, Object payload) {
        try {
            Map<String, Object> payloadMap = objectMapper.convertValue(payload, Map.class);
            Map<String, String> headers = Map.of(
                    "X-Event-Version", "1.0",
                    "X-Source-System", "transaction-platform"
            );

            // Aggregate ID – assume payload has getId() or use reflection
            Long aggregateId = extractId(payload);

            outboxService.createWebhookEvent(
                    aggregateId,
                    payload.getClass().getSimpleName(),
                    payloadMap,
                    headers
            );

            log.info("Outbox event created for event type: {}", eventType);
        } catch (Exception e) {
            log.error("Failed to create outbox event for: {}", eventType, e);
        }
    }

    private Long extractId(Object payload) {
        try {
            return (Long) payload.getClass().getMethod("getId").invoke(payload);
        } catch (Exception e) {
            return System.currentTimeMillis();
        }
    }
}