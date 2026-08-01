-- Add tenant_id column to idempotency_key if it doesn't exist
DO $$ 
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_name = 'idempotency_key' AND column_name = 'tenant_id'
    ) THEN
        ALTER TABLE idempotency_key ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1;
    ELSE
        -- If the column exists, ensure it's NOT NULL and has a default
        ALTER TABLE idempotency_key ALTER COLUMN tenant_id SET NOT NULL;
        ALTER TABLE idempotency_key ALTER COLUMN tenant_id SET DEFAULT 1;
    END IF;
END $$;