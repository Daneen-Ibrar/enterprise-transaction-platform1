package com.enterprise.webhook;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WebhookDeliveryRepository extends JpaRepository<WebhookDelivery, Long> {
    Page<WebhookDelivery> findByWebhookConfigIdOrderByCreatedAtDesc(Long configId, Pageable pageable);
    List<WebhookDelivery> findByWebhookConfigIdOrderByCreatedAtDesc(Long configId);

    // For scheduled retry of failed deliveries (optional)
    List<WebhookDelivery> findBySuccessFalseAndAttemptNumberLessThan(int maxAttempts);
}