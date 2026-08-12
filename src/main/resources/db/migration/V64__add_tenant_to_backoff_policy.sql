-- ============================================================
-- V64__add_tenant_to_backoff_policy.sql
-- Description: Add tenant_id to backoff_policy table for multi-tenant isolation
-- ============================================================

-- Add tenant_id column
ALTER TABLE backoff_policy ADD COLUMN IF NOT EXISTS tenant_id BIGINT NOT NULL DEFAULT 1;

-- Add foreign key constraint
ALTER TABLE backoff_policy ADD CONSTRAINT fk_backoff_policy_tenant 
    FOREIGN KEY (tenant_id) REFERENCES tenant(id);

-- Create index for performance
CREATE INDEX IF NOT EXISTS idx_backoff_policy_tenant ON backoff_policy(tenant_id);

-- Create composite index for lookups
CREATE INDEX IF NOT EXISTS idx_backoff_policy_tenant_retry ON backoff_policy(tenant_id, retry_policy_id);

-- Add comment for documentation
COMMENT ON COLUMN backoff_policy.tenant_id IS 'Tenant ID for multi-tenant isolation';