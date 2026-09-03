-- ================================================================
-- V61: Seed Retry and Circuit Breaker Policies
-- ================================================================

-- ================================================================
-- DELETE EXISTING ROWS (so we can re-seed cleanly)
-- ================================================================

DELETE FROM retry_policy WHERE operation_type IN (
    'TransactionService.processPayment',
    'WebhookService.sendWebhook',
    'InvoiceService.approveInvoice',
    'RefundService.processRefund'
);

DELETE FROM circuit_breaker_policy WHERE operation_type IN (
    'TransactionService.processPayment',
    'WebhookService.sendWebhook',
    'InvoiceService.approveInvoice',
    'RefundService.processRefund'
);

DELETE FROM circuit_breaker_state WHERE operation_type IN (
    'TransactionService.processPayment',
    'WebhookService.sendWebhook',
    'InvoiceService.approveInvoice',
    'RefundService.processRefund'
);

-- ================================================================
-- RETRY POLICIES - ✅ Only Tenant 1
-- ================================================================

INSERT INTO retry_policy (operation_type, max_attempts, backoff_strategy, base_delay_ms, max_delay_ms, jitter_enabled, active, tenant_id)
VALUES
('TransactionService.processPayment', 3, 'EXPONENTIAL', 1000, 30000, true, true, 1),
('WebhookService.sendWebhook', 3, 'EXPONENTIAL', 1000, 30000, true, true, 1),
('InvoiceService.approveInvoice', 3, 'EXPONENTIAL', 500, 10000, true, true, 1),
('RefundService.processRefund', 3, 'EXPONENTIAL', 1000, 30000, true, true, 1);

-- ================================================================
-- CIRCUIT BREAKER POLICIES - ✅ Only Tenant 1
-- ================================================================

INSERT INTO circuit_breaker_policy (operation_type, failure_threshold, success_threshold, timeout_ms, evaluation_window_sec, active, tenant_id)
VALUES
('TransactionService.processPayment', 5, 3, 30000, 30, true, 1),
('WebhookService.sendWebhook', 5, 3, 30000, 30, true, 1),
('InvoiceService.approveInvoice', 5, 3, 15000, 30, true, 1),
('RefundService.processRefund', 5, 3, 30000, 30, true, 1);

-- ================================================================
-- CIRCUIT BREAKER STATES (initialise to CLOSED) - ✅ Only Tenant 1
-- ================================================================

INSERT INTO circuit_breaker_state (operation_type, state, failure_count, success_count, updated_at, tenant_id)
VALUES
('TransactionService.processPayment', 'CLOSED', 0, 0, NOW(), 1),
('WebhookService.sendWebhook', 'CLOSED', 0, 0, NOW(), 1),
('InvoiceService.approveInvoice', 'CLOSED', 0, 0, NOW(), 1),
('RefundService.processRefund', 'CLOSED', 0, 0, NOW(), 1);

-- ================================================================
-- MARK SEEDED POLICIES AS SYSTEM POLICIES
-- ================================================================

UPDATE retry_policy SET is_system = true WHERE operation_type IN (
    'TransactionService.processPayment',
    'WebhookService.sendWebhook',
    'InvoiceService.approveInvoice',
    'RefundService.processRefund'
);

UPDATE circuit_breaker_policy SET is_system = true WHERE operation_type IN (
    'TransactionService.processPayment',
    'WebhookService.sendWebhook',
    'InvoiceService.approveInvoice',
    'RefundService.processRefund'
);

UPDATE circuit_breaker_state SET is_system = true WHERE operation_type IN (
    'TransactionService.processPayment',
    'WebhookService.sendWebhook',
    'InvoiceService.approveInvoice',
    'RefundService.processRefund'
);