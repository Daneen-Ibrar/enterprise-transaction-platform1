-- V62__fix_seed_policies.sql
-- ================================================================
-- FIX: Only seed policies for existing tenants
-- ================================================================

-- Delete any partial data from failed migration
DELETE FROM circuit_breaker_state WHERE tenant_id NOT IN (SELECT id FROM tenant);
DELETE FROM circuit_breaker_policy WHERE tenant_id NOT IN (SELECT id FROM tenant);
DELETE FROM retry_policy WHERE tenant_id NOT IN (SELECT id FROM tenant);

-- Re-insert policies only for tenants that exist
INSERT INTO retry_policy (operation_type, max_attempts, backoff_strategy, base_delay_ms, max_delay_ms, jitter_enabled, active, tenant_id)
SELECT 'TransactionService.processPayment', 3, 'EXPONENTIAL', 1000, 30000, true, true, t.id
FROM tenant t
WHERE NOT EXISTS (
    SELECT 1 FROM retry_policy rp 
    WHERE rp.operation_type = 'TransactionService.processPayment' AND rp.tenant_id = t.id
);

INSERT INTO retry_policy (operation_type, max_attempts, backoff_strategy, base_delay_ms, max_delay_ms, jitter_enabled, active, tenant_id)
SELECT 'WebhookService.sendWebhook', 3, 'EXPONENTIAL', 1000, 30000, true, true, t.id
FROM tenant t
WHERE NOT EXISTS (
    SELECT 1 FROM retry_policy rp 
    WHERE rp.operation_type = 'WebhookService.sendWebhook' AND rp.tenant_id = t.id
);

INSERT INTO retry_policy (operation_type, max_attempts, backoff_strategy, base_delay_ms, max_delay_ms, jitter_enabled, active, tenant_id)
SELECT 'InvoiceService.approveInvoice', 3, 'EXPONENTIAL', 500, 10000, true, true, t.id
FROM tenant t
WHERE NOT EXISTS (
    SELECT 1 FROM retry_policy rp 
    WHERE rp.operation_type = 'InvoiceService.approveInvoice' AND rp.tenant_id = t.id
);

INSERT INTO retry_policy (operation_type, max_attempts, backoff_strategy, base_delay_ms, max_delay_ms, jitter_enabled, active, tenant_id)
SELECT 'RefundService.processRefund', 3, 'EXPONENTIAL', 1000, 30000, true, true, t.id
FROM tenant t
WHERE NOT EXISTS (
    SELECT 1 FROM retry_policy rp 
    WHERE rp.operation_type = 'RefundService.processRefund' AND rp.tenant_id = t.id
);

-- Circuit Breaker Policies
INSERT INTO circuit_breaker_policy (operation_type, failure_threshold, success_threshold, timeout_ms, evaluation_window_sec, active, tenant_id)
SELECT 'TransactionService.processPayment', 5, 3, 30000, 30, true, t.id
FROM tenant t
WHERE NOT EXISTS (
    SELECT 1 FROM circuit_breaker_policy cb 
    WHERE cb.operation_type = 'TransactionService.processPayment' AND cb.tenant_id = t.id
);

INSERT INTO circuit_breaker_policy (operation_type, failure_threshold, success_threshold, timeout_ms, evaluation_window_sec, active, tenant_id)
SELECT 'WebhookService.sendWebhook', 5, 3, 30000, 30, true, t.id
FROM tenant t
WHERE NOT EXISTS (
    SELECT 1 FROM circuit_breaker_policy cb 
    WHERE cb.operation_type = 'WebhookService.sendWebhook' AND cb.tenant_id = t.id
);

INSERT INTO circuit_breaker_policy (operation_type, failure_threshold, success_threshold, timeout_ms, evaluation_window_sec, active, tenant_id)
SELECT 'InvoiceService.approveInvoice', 5, 3, 15000, 30, true, t.id
FROM tenant t
WHERE NOT EXISTS (
    SELECT 1 FROM circuit_breaker_policy cb 
    WHERE cb.operation_type = 'InvoiceService.approveInvoice' AND cb.tenant_id = t.id
);

INSERT INTO circuit_breaker_policy (operation_type, failure_threshold, success_threshold, timeout_ms, evaluation_window_sec, active, tenant_id)
SELECT 'RefundService.processRefund', 5, 3, 30000, 30, true, t.id
FROM tenant t
WHERE NOT EXISTS (
    SELECT 1 FROM circuit_breaker_policy cb 
    WHERE cb.operation_type = 'RefundService.processRefund' AND cb.tenant_id = t.id
);

-- Circuit Breaker States
INSERT INTO circuit_breaker_state (operation_type, state, failure_count, success_count, updated_at, tenant_id)
SELECT 'TransactionService.processPayment', 'CLOSED', 0, 0, NOW(), t.id
FROM tenant t
WHERE NOT EXISTS (
    SELECT 1 FROM circuit_breaker_state cs 
    WHERE cs.operation_type = 'TransactionService.processPayment' AND cs.tenant_id = t.id
);

INSERT INTO circuit_breaker_state (operation_type, state, failure_count, success_count, updated_at, tenant_id)
SELECT 'WebhookService.sendWebhook', 'CLOSED', 0, 0, NOW(), t.id
FROM tenant t
WHERE NOT EXISTS (
    SELECT 1 FROM circuit_breaker_state cs 
    WHERE cs.operation_type = 'WebhookService.sendWebhook' AND cs.tenant_id = t.id
);

INSERT INTO circuit_breaker_state (operation_type, state, failure_count, success_count, updated_at, tenant_id)
SELECT 'InvoiceService.approveInvoice', 'CLOSED', 0, 0, NOW(), t.id
FROM tenant t
WHERE NOT EXISTS (
    SELECT 1 FROM circuit_breaker_state cs 
    WHERE cs.operation_type = 'InvoiceService.approveInvoice' AND cs.tenant_id = t.id
);

INSERT INTO circuit_breaker_state (operation_type, state, failure_count, success_count, updated_at, tenant_id)
SELECT 'RefundService.processRefund', 'CLOSED', 0, 0, NOW(), t.id
FROM tenant t
WHERE NOT EXISTS (
    SELECT 1 FROM circuit_breaker_state cs 
    WHERE cs.operation_type = 'RefundService.processRefund' AND cs.tenant_id = t.id
);

-- Mark as system policies
UPDATE retry_policy SET is_system = true 
WHERE operation_type IN (
    'TransactionService.processPayment',
    'WebhookService.sendWebhook',
    'InvoiceService.approveInvoice',
    'RefundService.processRefund'
);

UPDATE circuit_breaker_policy SET is_system = true 
WHERE operation_type IN (
    'TransactionService.processPayment',
    'WebhookService.sendWebhook',
    'InvoiceService.approveInvoice',
    'RefundService.processRefund'
);

UPDATE circuit_breaker_state SET is_system = true 
WHERE operation_type IN (
    'TransactionService.processPayment',
    'WebhookService.sendWebhook',
    'InvoiceService.approveInvoice',
    'RefundService.processRefund'
);