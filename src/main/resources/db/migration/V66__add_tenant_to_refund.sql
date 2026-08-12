-- ============================================================
-- V66__add_tenant_to_refund.sql
-- Description: Add tenant_id to refund table for multi-tenant isolation
-- ============================================================

-- Add tenant_id column
ALTER TABLE refund ADD COLUMN IF NOT EXISTS tenant_id BIGINT NOT NULL DEFAULT 1;

-- Add foreign key constraint
ALTER TABLE refund ADD CONSTRAINT fk_refund_tenant 
    FOREIGN KEY (tenant_id) REFERENCES tenant(id);

-- Create indexes for performance
CREATE INDEX IF NOT EXISTS idx_refund_tenant ON refund(tenant_id);
CREATE INDEX IF NOT EXISTS idx_refund_tenant_status ON refund(tenant_id, status);
CREATE INDEX IF NOT EXISTS idx_refund_tenant_transaction ON refund(tenant_id, transaction_id);

-- Add comment for documentation
COMMENT ON COLUMN refund.tenant_id IS 'Tenant ID for multi-tenant isolation';