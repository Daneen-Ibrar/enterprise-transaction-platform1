-- ============================================================
-- V52__add_webhook_reliability_policies.sql
-- Description: Add retry and circuit breaker policies for webhooks
-- ============================================================

-- Retry policy for webhook deliveries
INSERT INTO retry_policy (operation_type, max_attempts, backoff_strategy, base_delay_ms, max_delay_ms, jitter_enabled, active)
VALUES ('WebhookService.sendWebhook', 3, 'EXPONENTIAL', 1000, 30000, true, true)
ON CONFLICT (operation_type) DO NOTHING;

-- Circuit breaker policy per tenant
INSERT INTO circuit_breaker_policy (operation_type, failure_threshold, success_threshold, timeout_ms, evaluation_window_sec, active, tenant_id)
SELECT 'WebhookService.sendWebhook', 5, 3, 60000, 30, true, t.id
FROM tenant t
ON CONFLICT (tenant_id, operation_type) DO NOTHING;

-- Initial circuit breaker state per tenant
INSERT INTO circuit_breaker_state (operation_type, state, tenant_id)
SELECT 'WebhookService.sendWebhook', 'CLOSED', t.id
FROM tenant t
ON CONFLICT (tenant_id, operation_type) DO NOTHING;