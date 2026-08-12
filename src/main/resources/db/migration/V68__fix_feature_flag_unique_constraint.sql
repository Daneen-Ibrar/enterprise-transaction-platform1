-- ============================================================
-- V68__fix_feature_flag_unique_constraint.sql
-- Description: Make feature_flag unique per tenant instead of globally
-- ============================================================

-- Drop the old unique constraint (name only)
ALTER TABLE feature_flag DROP CONSTRAINT IF EXISTS feature_flag_name_key;
ALTER TABLE feature_flag DROP CONSTRAINT IF EXISTS uk_feature_flag_name;

-- Ensure tenant_id is NOT NULL
ALTER TABLE feature_flag ALTER COLUMN tenant_id SET NOT NULL;

-- Add tenant-aware unique constraint
ALTER TABLE feature_flag ADD CONSTRAINT uk_feature_flag_name_tenant UNIQUE (name, tenant_id);

-- Create composite index for performance
CREATE INDEX IF NOT EXISTS idx_feature_flag_tenant_name ON feature_flag(tenant_id, name);

-- Add comment for documentation
COMMENT ON CONSTRAINT uk_feature_flag_name_tenant ON feature_flag IS 'Ensures feature flag names are unique per tenant';