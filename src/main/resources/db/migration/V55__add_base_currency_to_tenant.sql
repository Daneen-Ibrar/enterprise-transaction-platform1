-- V55__add_base_currency_to_tenant.sql
-- Add base_currency column to tenant table (default GBP)

ALTER TABLE tenant ADD COLUMN IF NOT EXISTS base_currency VARCHAR(3) NOT NULL DEFAULT 'GBP';

-- Update existing tenants to GBP (already default)
UPDATE tenant SET base_currency = 'GBP' WHERE base_currency IS NULL;

-- Add constraint to ensure it's a valid ISO code (optional)
-- ALTER TABLE tenant ADD CONSTRAINT check_base_currency CHECK (base_currency ~ '^[A-Z]{3}$');