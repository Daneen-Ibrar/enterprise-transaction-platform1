-- ============================================================
-- V69__fix_api_key_unique_constraint.sql
-- Description: Make api_key unique per tenant instead of globally
-- ============================================================

-- Drop the old unique constraint (key_value only)
ALTER TABLE api_key DROP CONSTRAINT IF EXISTS api_key_key_value_key;
ALTER TABLE api_key DROP CONSTRAINT IF EXISTS uk_api_key_value;

-- Ensure tenant_id is NOT NULL
ALTER TABLE api_key ALTER COLUMN tenant_id SET NOT NULL;

-- Add tenant-aware unique constraint
ALTER TABLE api_key ADD CONSTRAINT uk_api_key_value_tenant UNIQUE (key_value, tenant_id);

-- Create composite index for performance
CREATE INDEX IF NOT EXISTS idx_api_key_tenant_value ON api_key(tenant_id, key_value);

-- Add comment for documentation
COMMENT ON CONSTRAINT uk_api_key_value_tenant ON api_key IS 'Ensures API key values are unique per tenant';