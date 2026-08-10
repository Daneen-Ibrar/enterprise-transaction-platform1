-- ================================================================
-- RETRY POLICIES (with ON CONFLICT)
-- ================================================================

INSERT INTO retry_policy (operation_type, max_attempts, backoff_strategy, base_delay_ms, max_delay_ms, jitter_enabled, active, tenant_id)
VALUES
('TransactionService.processPayment', 3, 'EXPONENTIAL', 1000, 30000, true, true, 1),
('TransactionService.processPayment', 3, 'EXPONENTIAL', 1000, 30000, true, true, 2),
('WebhookService.sendWebhook', 3, 'EXPONENTIAL', 1000, 30000, true, true, 1),
('WebhookService.sendWebhook', 3, 'EXPONENTIAL', 1000, 30000, true, true, 2),
('InvoiceService.approveInvoice', 3, 'EXPONENTIAL', 500, 10000, true, true, 1),
('InvoiceService.approveInvoice', 3, 'EXPONENTIAL', 500, 10000, true, true, 2),
('RefundService.processRefund', 3, 'EXPONENTIAL', 1000, 30000, true, true, 1),
('RefundService.processRefund', 3, 'EXPONENTIAL', 1000, 30000, true, true, 2)
ON CONFLICT (tenant_id, operation_type) DO NOTHING;

-- ================================================================
-- CIRCUIT BREAKER POLICIES
-- ================================================================

INSERT INTO circuit_breaker_policy (operation_type, failure_threshold, success_threshold, timeout_ms, evaluation_window_sec, active, tenant_id)
VALUES
('TransactionService.processPayment', 5, 3, 30000, 30, true, 1),
('TransactionService.processPayment', 5, 3, 30000, 30, true, 2),
('WebhookService.sendWebhook', 5, 3, 30000, 30, true, 1),
('WebhookService.sendWebhook', 5, 3, 30000, 30, true, 2),
('InvoiceService.approveInvoice', 5, 3, 15000, 30, true, 1),
('InvoiceService.approveInvoice', 5, 3, 15000, 30, true, 2),
('RefundService.processRefund', 5, 3, 30000, 30, true, 1),
('RefundService.processRefund', 5, 3, 30000, 30, true, 2)
ON CONFLICT (tenant_id, operation_type) DO NOTHING;

-- ================================================================
-- CIRCUIT BREAKER STATES (initialise to CLOSED)
-- ================================================================

INSERT INTO circuit_breaker_state (operation_type, state, failure_count, success_count, updated_at, tenant_id)
VALUES
('TransactionService.processPayment', 'CLOSED', 0, 0, NOW(), 1),
('TransactionService.processPayment', 'CLOSED', 0, 0, NOW(), 2),
('WebhookService.sendWebhook', 'CLOSED', 0, 0, NOW(), 1),
('WebhookService.sendWebhook', 'CLOSED', 0, 0, NOW(), 2),
('InvoiceService.approveInvoice', 'CLOSED', 0, 0, NOW(), 1),
('InvoiceService.approveInvoice', 'CLOSED', 0, 0, NOW(), 2),
('RefundService.processRefund', 'CLOSED', 0, 0, NOW(), 1),
('RefundService.processRefund', 'CLOSED', 0, 0, NOW(), 2)
ON CONFLICT (tenant_id, operation_type) DO NOTHING;

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