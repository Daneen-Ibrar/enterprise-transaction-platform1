package com.enterprise.scheduler;

import com.enterprise.webhook.WebhookDelivery;
import com.enterprise.webhook.WebhookDeliveryRepository;
import com.enterprise.webhook.WebhookService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class WebhookRetryScheduler {

    private static final Logger log = LoggerFactory.getLogger(WebhookRetryScheduler.class);

    private final WebhookDeliveryRepository deliveryRepository;
    private final WebhookService webhookService;

    public WebhookRetryScheduler(WebhookDeliveryRepository deliveryRepository,
                                 WebhookService webhookService) {
        this.deliveryRepository = deliveryRepository;
        this.webhookService = webhookService;
    }

    // Run every minute
    @Scheduled(fixedDelay = 60000)
    public void retryFailedDeliveries() {
        log.debug("Running scheduled webhook retry");
        List<WebhookDelivery> failed = deliveryRepository.findBySuccessFalseAndAttemptNumberLessThan(5);
        if (failed.isEmpty()) {
            return;
        }

        log.info("Found {} failed webhook deliveries to retry", failed.size());
        for (WebhookDelivery delivery : failed) {
            try {
                webhookService.retryDelivery(delivery.getId());
                log.info("Retry successful for delivery {}", delivery.getId());
            } catch (Exception e) {
                log.error("Retry failed for delivery {}: {}", delivery.getId(), e.getMessage());
            }
        }
    }
}