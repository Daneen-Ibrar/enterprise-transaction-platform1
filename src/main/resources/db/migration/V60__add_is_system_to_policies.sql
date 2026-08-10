-- Add is_system columns to all three tables
ALTER TABLE retry_policy ADD COLUMN is_system BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE circuit_breaker_policy ADD COLUMN is_system BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE circuit_breaker_state ADD COLUMN is_system BOOLEAN NOT NULL DEFAULT false;