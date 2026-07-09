package com.enterprise.api;

import com.enterprise.feature.FeatureFlagService;
import com.enterprise.webhook.WebhookConfig;
import com.enterprise.webhook.WebhookRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.util.List;

@Controller
@RequestMapping("/admin/webhooks")
@PreAuthorize("hasRole('ADMIN')")
public class AdminWebhookController {

    private final WebhookRepository webhookRepository;
    private final FeatureFlagService featureFlagService;   // <-- ADDED

    public AdminWebhookController(WebhookRepository webhookRepository,
                                  FeatureFlagService featureFlagService) {   // <-- ADDED
        this.webhookRepository = webhookRepository;
        this.featureFlagService = featureFlagService;
    }

    @GetMapping
    public String listWebhooks(Model model) {
        List<WebhookConfig> webhooks = webhookRepository.findAll();
        model.addAttribute("webhooks", webhooks);
        model.addAttribute("webhooksEnabled", featureFlagService.isEnabled("WEBHOOKS"));   // <-- ADDED
        return "admin/webhooks/list";
    }

    @GetMapping("/create")
    public String showCreateForm(Model model) {
        model.addAttribute("webhook", new WebhookConfig());
        model.addAttribute("eventTypes", List.of("TRANSACTION_SETTLED", "REFUND_PROCESSED"));
        return "admin/webhooks/create";
    }

    @PostMapping
    public String createWebhook(@ModelAttribute WebhookConfig webhook,
                                RedirectAttributes redirectAttributes) {
        webhook.setCreatedAt(LocalDateTime.now());
        webhookRepository.save(webhook);
        redirectAttributes.addFlashAttribute("success", "Webhook created.");
        return "redirect:/admin/webhooks";
    }

    @GetMapping("/{id}/edit")
    public String showEditForm(@PathVariable Long id, Model model) {
        WebhookConfig webhook = webhookRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Webhook not found"));
        model.addAttribute("webhook", webhook);
        model.addAttribute("eventTypes", List.of("TRANSACTION_SETTLED", "REFUND_PROCESSED"));
        return "admin/webhooks/edit";
    }

    @PostMapping("/{id}")
    public String updateWebhook(@PathVariable Long id,
                                @ModelAttribute WebhookConfig webhook,
                                RedirectAttributes redirectAttributes) {
        webhook.setId(id);
        webhook.setUpdatedAt(LocalDateTime.now());
        webhookRepository.save(webhook);
        redirectAttributes.addFlashAttribute("success", "Webhook updated.");
        return "redirect:/admin/webhooks";
    }

    @PostMapping("/{id}/delete")
    public String deleteWebhook(@PathVariable Long id,
                                RedirectAttributes redirectAttributes) {
        webhookRepository.deleteById(id);
        redirectAttributes.addFlashAttribute("success", "Webhook deleted.");
        return "redirect:/admin/webhooks";
    }

    @PostMapping("/{id}/toggle")
    public String toggleWebhook(@PathVariable Long id,
                                RedirectAttributes redirectAttributes) {
        WebhookConfig webhook = webhookRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Webhook not found"));
        webhook.setActive(!webhook.isActive());
        webhook.setUpdatedAt(LocalDateTime.now());
        webhookRepository.save(webhook);
        redirectAttributes.addFlashAttribute("success",
                webhook.isActive() ? "Webhook activated." : "Webhook deactivated.");
        return "redirect:/admin/webhooks";
    }
}