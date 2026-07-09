package com.enterprise.webhook;

import com.enterprise.events.RefundProcessedEvent;
import com.enterprise.events.TransactionSettledEvent;
import com.enterprise.feature.FeatureFlagService;
import com.enterprise.reliability.Reliable;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Service
public class WebhookService {

    private static final Logger log = LoggerFactory.getLogger(WebhookService.class);

    private final WebhookRepository webhookRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final FeatureFlagService featureFlagService;

    public WebhookService(WebhookRepository webhookRepository,
                          RestTemplate restTemplate,
                          ObjectMapper objectMapper,
                          FeatureFlagService featureFlagService) {
        this.webhookRepository = webhookRepository;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.featureFlagService = featureFlagService;
    }

    @Async
    @EventListener
    public void onTransactionSettled(TransactionSettledEvent event) {
        sendWebhooks("TRANSACTION_SETTLED", event.getTransaction());
    }

    @Async
    @EventListener
    public void onRefundProcessed(RefundProcessedEvent event) {
        sendWebhooks("REFUND_PROCESSED", event.getRefundTransaction(), event.getOriginalTransaction());
    }

    @Reliable
    protected void sendWebhooks(String eventType, Object... payloadObjects) {
        // ----- FEATURE FLAG CHECK -----
        if (!featureFlagService.isEnabled("WEBHOOKS")) {
            log.info("Webhooks are disabled – skipping delivery for event: {}", eventType);
            return;
        }

        List<WebhookConfig> configs = webhookRepository.findByEventTypeAndActiveTrue(eventType);
        if (configs.isEmpty()) {
            log.debug("No active webhooks for event type: {}", eventType);
            return;
        }

        Map<String, Object> payload = buildPayload(eventType, payloadObjects);

        for (WebhookConfig config : configs) {
            sendWebhook(config, payload);
        }
    }

    @Reliable
    protected void sendWebhook(WebhookConfig config, Map<String, Object> payload) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(
                config.getUrl(),
                request,
                String.class
            );

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Webhook sent successfully to {} (event: {})", config.getUrl(), config.getEventType());
            } else {
                log.warn("Webhook to {} returned status: {}", config.getUrl(), response.getStatusCode());
            }
        } catch (Exception e) {
            log.error("Failed to send webhook to {} (event: {})", config.getUrl(), config.getEventType(), e);
            throw new RuntimeException("Webhook delivery failed", e);
        }
    }

    private Map<String, Object> buildPayload(String eventType, Object[] payloadObjects) {
        Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("eventType", eventType);
        payload.put("timestamp", java.time.Instant.now().toString());
        if (payloadObjects.length > 0) {
            try {
                for (Object obj : payloadObjects) {
                    payload.put(obj.getClass().getSimpleName().toLowerCase(),
                        objectMapper.convertValue(obj, Map.class));
                }
            } catch (Exception e) {
                log.warn("Could not convert payload to map", e);
            }
        }
        return payload;
    }
}