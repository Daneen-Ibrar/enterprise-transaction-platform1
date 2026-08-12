-- V72__add_order_key_to_invoice.sql
-- ============================================================
-- Add missing order_key column to invoice table
-- ============================================================

-- Add order_key column
ALTER TABLE invoice ADD COLUMN IF NOT EXISTS order_key VARCHAR(255);

-- Create index for lookups
CREATE INDEX IF NOT EXISTS idx_invoice_order_key ON invoice(order_key);

-- Add comment
COMMENT ON COLUMN invoice.order_key IS 'WooCommerce order key for payment redirect';