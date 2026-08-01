-- ============================================================
-- V49__add_simulator_feature_flag.sql
-- Description: Adds a global feature flag to enable/disable simulator tokens.
-- Default: DISABLED (false) – must be turned ON only in dev/test.
-- ============================================================

-- Insert the flag only if it doesn't already exist.
INSERT INTO feature_flag (name, enabled, description, tenant_id, updated_at)
SELECT 'SIMULATOR_ENABLED', false, 'Enables simulator tokens for testing (must be OFF in production)', 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM feature_flag WHERE name = 'SIMULATOR_ENABLED');