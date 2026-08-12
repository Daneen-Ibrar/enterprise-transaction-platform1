-- V72__add_xero_token_columns.sql
-- ============================================================
-- Fix xero_token table - add missing columns if not exist
-- ============================================================

-- Add tenant_id column if missing
ALTER TABLE xero_token ADD COLUMN IF NOT EXISTS tenant_id BIGINT DEFAULT 1;
ALTER TABLE xero_token ALTER COLUMN tenant_id SET NOT NULL;

-- Add foreign key constraint
ALTER TABLE xero_token ADD CONSTRAINT fk_xero_token_tenant 
    FOREIGN KEY (tenant_id) REFERENCES tenant(id);

-- Create index if missing
CREATE INDEX IF NOT EXISTS idx_xero_token_tenant ON xero_token(tenant_id);

-- Create index on expires_at if missing
CREATE INDEX IF NOT EXISTS idx_xero_token_expires ON xero_token(expires_at);

-- Update existing rows to tenant 1 if null
UPDATE xero_token SET tenant_id = 1 WHERE tenant_id IS NULL;