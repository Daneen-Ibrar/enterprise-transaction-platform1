-- ============================================================
-- V65__add_tenant_to_job.sql
-- Description: Add tenant_id to job table for multi-tenant isolation
-- ============================================================

-- Add tenant_id column
ALTER TABLE job ADD COLUMN IF NOT EXISTS tenant_id BIGINT NOT NULL DEFAULT 1;

-- Add foreign key constraint
ALTER TABLE job ADD CONSTRAINT fk_job_tenant 
    FOREIGN KEY (tenant_id) REFERENCES tenant(id);

-- Create indexes for performance
CREATE INDEX IF NOT EXISTS idx_job_tenant ON job(tenant_id);
CREATE INDEX IF NOT EXISTS idx_job_tenant_status ON job(tenant_id, status);
CREATE INDEX IF NOT EXISTS idx_job_tenant_created ON job(tenant_id, created_at DESC);

-- Add comment for documentation
COMMENT ON COLUMN job.tenant_id IS 'Tenant ID for multi-tenant isolation';