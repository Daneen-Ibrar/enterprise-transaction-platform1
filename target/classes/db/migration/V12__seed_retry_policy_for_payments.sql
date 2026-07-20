-- Ensure the retry policy exists for payments
INSERT INTO retry_policy (operation_type, max_attempts, backoff_strategy, base_delay_ms, max_delay_ms, jitter_enabled, active)
VALUES ('TransactionService.processPayment', 3, 'EXPONENTIAL', 1000, 30000, true, true)
ON CONFLICT (operation_type) DO UPDATE SET active = true, max_attempts = 3, updated_at = CURRENT_TIMESTAMP;

INSERT INTO circuit_breaker_policy (operation_type, failure_threshold, success_threshold, timeout_ms, evaluation_window_sec, active)
VALUES ('TransactionService.processPayment', 5, 3, 60000, 30, true)
ON CONFLICT (operation_type) DO UPDATE SET active = true, updated_at = CURRENT_TIMESTAMP;

INSERT INTO circuit_breaker_state (operation_type, state)
SELECT 'TransactionService.processPayment', 'CLOSED'
WHERE NOT EXISTS (SELECT 1 FROM circuit_breaker_state WHERE operation_type = 'TransactionService.processPayment');