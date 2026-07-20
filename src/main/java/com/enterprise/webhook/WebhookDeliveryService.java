package com.enterprise.webhook;

import com.enterprise.outbox.OutboxEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Map;

@Service
public class WebhookDeliveryService {

    private static final Logger log = LoggerFactory.getLogger(WebhookDeliveryService.class);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final WebhookSigningService webhookSigningService;

    @Value("${app.webhook.connection-timeout:5000}")
    private int connectionTimeout;

    @Value("${app.webhook.read-timeout:10000}")
    private int readTimeout;

    public WebhookDeliveryService(RestTemplate restTemplate,
                                  ObjectMapper objectMapper,
                                  WebhookSigningService webhookSigningService) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.webhookSigningService = webhookSigningService;
    }

    public boolean deliverWebhook(OutboxEvent event) {
        try {
            Map<String, Object> payload = objectMapper.readValue(event.getPayload(), Map.class);
            Map<String, String> headers = event.getHeaders() != null ?
                    objectMapper.readValue(event.getHeaders(), Map.class) : Map.of();

            // Get webhook config for this event
            // For now, we'll use a default – in production, you'd look up by event type
            String webhookUrl = System.getenv("WEBHOOK_URL");
            if (webhookUrl == null) {
                log.warn("No webhook URL configured");
                return false;
            }

            // Validate URL to prevent SSRF
            if (!isValidWebhookUrl(webhookUrl)) {
                log.warn("Webhook URL blocked: {}", webhookUrl);
                return false;
            }

            HttpHeaders httpHeaders = new HttpHeaders();
            httpHeaders.setContentType(MediaType.APPLICATION_JSON);
            httpHeaders.set("X-Event-Type", event.getEventType());
            httpHeaders.set("X-Event-Id", String.valueOf(event.getId()));

            // Add signing
            String payloadJson = objectMapper.writeValueAsString(payload);
            String signature = webhookSigningService.generateSignature(payloadJson);
            if (!signature.isEmpty()) {
                httpHeaders.set("X-Webhook-Signature", signature);
            }

            // Add custom headers
            headers.forEach(httpHeaders::set);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, httpHeaders);

            ResponseEntity<String> response = restTemplate.postForEntity(webhookUrl, request, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Webhook delivered: eventId={}, status={}", event.getId(), response.getStatusCode());
                return true;
            } else {
                log.warn("Webhook failed: eventId={}, status={}", event.getId(), response.getStatusCode());
                return false;
            }

        } catch (Exception e) {
            log.error("Webhook delivery failed: eventId={}", event.getId(), e);
            return false;
        }
    }
// Update the isValidWebhookUrl method with these additional checks:

private boolean isValidWebhookUrl(String url) {
    try {
        URI uri = new URI(url);
        String host = uri.getHost();
        String scheme = uri.getScheme();

        // Must be HTTP or HTTPS
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            return false;
        }

        if (host == null || host.isEmpty()) {
            return false;
        }

        // Block localhost variations
        String lowerHost = host.toLowerCase();
        if (lowerHost.equals("localhost") ||
            lowerHost.equals("127.0.0.1") ||
            lowerHost.equals("::1") ||
            lowerHost.equals("0.0.0.0") ||
            lowerHost.startsWith("127.") ||
            lowerHost.equals("169.254.169.254")) { // AWS metadata
            return false;
        }

        // Block private IP ranges
        try {
            InetAddress address = InetAddress.getByName(host);
            if (address.isLoopbackAddress() ||
                address.isLinkLocalAddress() ||
                address.isSiteLocalAddress()) {
                return false;
            }
        } catch (UnknownHostException e) {
            // If we can't resolve, allow it (or block it based on policy)
            return false;
        }

        // Block internal metadata endpoints
        if (host.contains("169.254.169.254")) {
            return false;
        }

        // Block any URL with @ (basic auth) to prevent credential leakage
        if (url.contains("@")) {
            return false;
        }

        // Block any URL with IP that's in private range
        // Already handled by the InetAddress check above

        return true;
    } catch (Exception e) {
        log.warn("Invalid webhook URL: {}", url, e);
        return false;
    }
}
}