package com.enterprise.api;

import com.enterprise.webhook.WebhookConfig;
import com.enterprise.webhook.WebhookConfigRepository;
import com.enterprise.webhook.WebhookDelivery;
import com.enterprise.webhook.WebhookDeliveryRepository;
import com.enterprise.webhook.WebhookService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/admin/webhook-deliveries")
@PreAuthorize("hasRole('ADMIN')")
public class AdminWebhookDeliveryController {

    private final WebhookDeliveryRepository deliveryRepository;
    private final WebhookService webhookService;
    private final WebhookConfigRepository webhookConfigRepository;

    public AdminWebhookDeliveryController(WebhookDeliveryRepository deliveryRepository,
                                          WebhookService webhookService,
                                          WebhookConfigRepository webhookConfigRepository) {
        this.deliveryRepository = deliveryRepository;
        this.webhookService = webhookService;
        this.webhookConfigRepository = webhookConfigRepository;
    }

    @GetMapping
    public String listDeliveries(
            @RequestParam(required = false) Long configId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Model model) {

        Page<WebhookDelivery> deliveries;
        if (configId != null) {
            deliveries = deliveryRepository.findByWebhookConfigIdOrderByCreatedAtDesc(
                    configId, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        } else {
            deliveries = deliveryRepository.findAll(
                    PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        }

        model.addAttribute("deliveries", deliveries);
        model.addAttribute("configId", configId);
        return "admin/webhook-deliveries/list";
    }

    // ===== RETRY endpoint (HTMX-friendly) =====
    @PostMapping("/{id}/retry")
    @ResponseBody
    public String retryDelivery(@PathVariable Long id) {
        try {
            webhookService.retryDelivery(id);
            return "✅ Retry initiated for delivery " + id;
        } catch (Exception e) {
            return "❌ Retry failed: " + e.getMessage();
        }
    }

    // ===== TEST FAILED DELIVERY =====
    @PostMapping("/test-fail")
    @ResponseBody
    public String testFailedDelivery(@RequestParam Long configId) {
        WebhookConfig config = webhookConfigRepository.findById(configId)
                .orElseThrow(() -> new RuntimeException("Webhook config not found"));

        WebhookDelivery delivery = new WebhookDelivery();
        delivery.setWebhookConfigId(configId);
        delivery.setEventType(config.getEventType());
        delivery.setUrl(config.getUrl());
        delivery.setPayload("{\"test\":\"data\"}");
        delivery.setSuccess(false);
        delivery.setStatusCode(500);
        delivery.setErrorMessage("Simulated failure for testing");
        delivery.setAttemptNumber(1);
        deliveryRepository.save(delivery);

        return "Failed delivery created with ID: " + delivery.getId();
    }
}