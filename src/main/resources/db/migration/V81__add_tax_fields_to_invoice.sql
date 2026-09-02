-- ============================================================
-- V85: Add tax fields to invoice table
-- ============================================================

ALTER TABLE invoice ADD COLUMN IF NOT EXISTS tax_amount DECIMAL(19,2);
ALTER TABLE invoice ADD COLUMN IF NOT EXISTS tax_rate DECIMAL(10,4);
ALTER TABLE invoice ADD COLUMN IF NOT EXISTS tax_name VARCHAR(50);
ALTER TABLE invoice ADD COLUMN IF NOT EXISTS total_with_tax DECIMAL(19,2);
ALTER TABLE invoice ADD COLUMN IF NOT EXISTS customer_country VARCHAR(2);
ALTER TABLE invoice ADD COLUMN IF NOT EXISTS is_b2b BOOLEAN DEFAULT FALSE;

CREATE INDEX IF NOT EXISTS idx_invoice_customer_country ON invoice(customer_country);

COMMENT ON COLUMN invoice.tax_amount IS 'Tax amount calculated from tax rate';
COMMENT ON COLUMN invoice.tax_rate IS 'Tax rate applied (percentage, e.g., 20.0 for 20% VAT)';
COMMENT ON COLUMN invoice.tax_name IS 'Name of tax (VAT, GST, Sales Tax)';
COMMENT ON COLUMN invoice.total_with_tax IS 'Total amount including tax (subtotal + tax)';
COMMENT ON COLUMN invoice.customer_country IS 'Customer country code for tax calculation';
COMMENT ON COLUMN invoice.is_b2b IS 'True if customer is a business (B2B) - may affect tax exemption';