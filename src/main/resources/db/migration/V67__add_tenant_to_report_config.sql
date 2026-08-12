-- ============================================================
-- V67__add_tenant_to_report_config.sql
-- Description: Add tenant_id to report_config table for multi-tenant isolation
-- ============================================================

-- Add tenant_id column
ALTER TABLE report_config ADD COLUMN IF NOT EXISTS tenant_id BIGINT NOT NULL DEFAULT 1;

-- Add foreign key constraint
ALTER TABLE report_config ADD CONSTRAINT fk_report_config_tenant 
    FOREIGN KEY (tenant_id) REFERENCES tenant(id);

-- Create indexes for performance
CREATE INDEX IF NOT EXISTS idx_report_config_tenant ON report_config(tenant_id);
CREATE INDEX IF NOT EXISTS idx_report_config_tenant_user ON report_config(tenant_id, user_id);
CREATE INDEX IF NOT EXISTS idx_report_config_tenant_type ON report_config(tenant_id, report_type);

-- Add comment for documentation
COMMENT ON COLUMN report_config.tenant_id IS 'Tenant ID for multi-tenant isolation';