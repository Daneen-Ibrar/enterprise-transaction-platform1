-- ============================================================
-- V50__add_tenant_to_circuit_breaker_state.sql
-- Description: Make circuit breaker state tenant-aware
-- ============================================================

-- Add tenant_id column to circuit_breaker_state
ALTER TABLE circuit_breaker_state ADD COLUMN IF NOT EXISTS tenant_id BIGINT REFERENCES tenant(id);

-- Set default tenant for existing rows (tenant 1)
UPDATE circuit_breaker_state SET tenant_id = 1 WHERE tenant_id IS NULL;

-- Make tenant_id NOT NULL
ALTER TABLE circuit_breaker_state ALTER COLUMN tenant_id SET NOT NULL;

-- Drop the old unique constraint (operation_type) and create new (tenant_id, operation_type)
ALTER TABLE circuit_breaker_state DROP CONSTRAINT IF EXISTS circuit_breaker_state_operation_type_key;

-- Add unique constraint per tenant
ALTER TABLE circuit_breaker_state ADD CONSTRAINT uk_circuit_breaker_state_tenant_operation UNIQUE (tenant_id, operation_type);

-- Create index for faster lookups
CREATE INDEX IF NOT EXISTS idx_circuit_breaker_state_tenant ON circuit_breaker_state(tenant_id);

-- Add tenant_id to circuit_breaker_policy (optional, for consistency)
ALTER TABLE circuit_breaker_policy ADD COLUMN IF NOT EXISTS tenant_id BIGINT REFERENCES tenant(id);
UPDATE circuit_breaker_policy SET tenant_id = 1 WHERE tenant_id IS NULL;
ALTER TABLE circuit_breaker_policy ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE circuit_breaker_policy DROP CONSTRAINT IF EXISTS circuit_breaker_policy_operation_type_key;
ALTER TABLE circuit_breaker_policy ADD CONSTRAINT uk_circuit_breaker_policy_tenant_operation UNIQUE (tenant_id, operation_type);
CREATE INDEX idx_circuit_breaker_policy_tenant ON circuit_breaker_policy(tenant_id);

-- Insert initial states for all tenants (if missing)
INSERT INTO circuit_breaker_state (operation_type, state, tenant_id)
SELECT cp.operation_type, 'CLOSED', t.id
FROM circuit_breaker_policy cp
CROSS JOIN tenant t
WHERE NOT EXISTS (
    SELECT 1 FROM circuit_breaker_state cs
    WHERE cs.operation_type = cp.operation_type AND cs.tenant_id = t.id
)
ON CONFLICT (tenant_id, operation_type) DO NOTHING;