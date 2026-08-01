package com.enterprise.webhook;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface WebhookConfigRepository extends JpaRepository<WebhookConfig, Long> {
    Optional<WebhookConfig> findByTenantIdAndEventType(Long tenantId, String eventType);
}