package com.enterprise.webhook;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface WebhookRepository extends JpaRepository<WebhookConfig, Long> {
    List<WebhookConfig> findByEventTypeAndActiveTrue(String eventType);
    List<WebhookConfig> findByActiveTrue();
}