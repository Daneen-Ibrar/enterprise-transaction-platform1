package com.enterprise.webhook;

import com.enterprise.events.RefundProcessedEvent;
import com.enterprise.events.TransactionSettledEvent;
import com.enterprise.feature.FeatureFlagService;
import com.enterprise.reliability.DlqProcessorService;
import com.enterprise.reliability.RecoveryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class WebhookService {

    private static final Logger log = LoggerFactory.getLogger(WebhookService.class);

    private final WebhookRepository webhookRepository;
    private final WebhookDeliveryRepository deliveryRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final FeatureFlagService featureFlagService;
    private final RecoveryService recoveryService;
    private final DlqProcessorService dlqProcessorService;

    @Value("${app.webhook.hmac-secret:default-secret-change-me}")
    private String hmacSecret;

    public WebhookService(WebhookRepository webhookRepository,
                          WebhookDeliveryRepository deliveryRepository,
                          RestTemplate restTemplate,
                          ObjectMapper objectMapper,
                          FeatureFlagService featureFlagService,
                          RecoveryService recoveryService,
                          DlqProcessorService dlqProcessorService) {
        this.webhookRepository = webhookRepository;
        this.deliveryRepository = deliveryRepository;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.featureFlagService = featureFlagService;
        this.recoveryService = recoveryService;
        this.dlqProcessorService = dlqProcessorService;
    }

    @PostConstruct
    public void registerDlqHandler() {
        dlqProcessorService.registerOperation(
            "WebhookService.sendWebhook",
            (context) -> {
                Long configId = (Long) context.get("configId");
                Map<String, Object> payload = (Map<String, Object>) context.get("payload");
                WebhookConfig config = webhookRepository.findById(configId)
                        .orElseThrow(() -> new RuntimeException("Webhook config not found"));
                sendWebhook(config, payload);
                return "OK";
            }
        );
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

    public void sendWebhooks(String eventType, Object... payloadObjects) {
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
            Map<String, Object> context = new HashMap<>();
            context.put("configId", config.getId());
            context.put("payload", payload);

            try {
                recoveryService.executeWithRetry(
                    "WebhookService.sendWebhook",
                    () -> {
                        sendWebhook(config, payload);
                        return null;
                    },
                    context
                );
            } catch (Exception e) {
                log.error("Webhook delivery to {} failed after retries: {}", config.getUrl(), e.getMessage());
                // The DLQ entry is already created by RecoveryService, so we just log.
            }
        }
    }

    @Transactional
    public void sendWebhook(WebhookConfig config, Map<String, Object> payload) {
        WebhookDelivery delivery = new WebhookDelivery();
        delivery.setWebhookConfigId(config.getId());
        delivery.setEventType(config.getEventType());
        delivery.setUrl(config.getUrl());
        delivery.setAttemptNumber(1);

        try {
            String payloadJson = objectMapper.writeValueAsString(payload);
            delivery.setPayload(payloadJson);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            String signature = computeHmac(payloadJson);
            headers.set("X-Webhook-Signature", signature);

            HttpEntity<String> request = new HttpEntity<>(payloadJson, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(
                config.getUrl(),
                request,
                String.class
            );

            delivery.setStatusCode(response.getStatusCode().value());
            delivery.setResponseBody(response.getBody());
            delivery.setSuccess(response.getStatusCode().is2xxSuccessful());

            if (!delivery.isSuccess()) {
                delivery.setErrorMessage("HTTP " + response.getStatusCode());
                throw new RuntimeException("Webhook returned non-2xx: " + response.getStatusCode());
            } else {
                log.info("Webhook sent successfully to {} (event: {})", config.getUrl(), config.getEventType());
            }

        } catch (Exception e) {
            delivery.setSuccess(false);
            delivery.setErrorMessage(e.getMessage());
            log.error("Webhook delivery to {} failed: {}", config.getUrl(), e.getMessage(), e);
            throw new RuntimeException("Webhook delivery failed", e);
        } finally {
            deliveryRepository.save(delivery);
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

    private String computeHmac(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(hmacSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKeySpec);
            byte[] hmacBytes = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hmacBytes);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            log.error("Failed to compute HMAC", e);
            return "";
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    @Transactional
    public WebhookDelivery retryDelivery(Long deliveryId) {
        WebhookDelivery delivery = deliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new RuntimeException("Delivery not found"));

        WebhookConfig config = webhookRepository.findById(delivery.getWebhookConfigId())
                .orElseThrow(() -> new RuntimeException("Webhook config not found"));

        try {
            Map<String, Object> payload = objectMapper.readValue(delivery.getPayload(), Map.class);
            sendWebhook(config, payload);
            List<WebhookDelivery> deliveries = deliveryRepository.findByWebhookConfigIdOrderByCreatedAtDesc(config.getId());
            return deliveries.get(0);
        } catch (Exception e) {
            log.error("Retry failed for delivery {}", deliveryId, e);
            WebhookDelivery failedDelivery = new WebhookDelivery();
            failedDelivery.setWebhookConfigId(config.getId());
            failedDelivery.setEventType(delivery.getEventType());
            failedDelivery.setUrl(config.getUrl());
            failedDelivery.setPayload(delivery.getPayload());
            failedDelivery.setSuccess(false);
            failedDelivery.setErrorMessage("Retry failed: " + e.getMessage());
            failedDelivery.setAttemptNumber(delivery.getAttemptNumber() + 1);
            return deliveryRepository.save(failedDelivery);
        }
    }
}