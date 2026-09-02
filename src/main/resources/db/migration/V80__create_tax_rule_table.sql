-- ============================================================
-- V84: Create tax_rule table for VAT/GST management
-- ============================================================

CREATE TABLE IF NOT EXISTS tax_rule (
    id BIGSERIAL PRIMARY KEY,
    country_code VARCHAR(2) NOT NULL,
    region_code VARCHAR(10),
    tax_rate DECIMAL(10,4) NOT NULL,
    tax_name VARCHAR(50) NOT NULL,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    is_b2b_exempt BOOLEAN NOT NULL DEFAULT FALSE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_tax_rule_tenant_country UNIQUE (tenant_id, country_code)
);

CREATE INDEX IF NOT EXISTS idx_tax_rule_tenant ON tax_rule(tenant_id);
CREATE INDEX IF NOT EXISTS idx_tax_rule_country ON tax_rule(country_code);
CREATE INDEX IF NOT EXISTS idx_tax_rule_active ON tax_rule(is_active);

-- Seed default tax rules for UK, US, EU
INSERT INTO tax_rule (country_code, tax_rate, tax_name, is_default, tenant_id) VALUES
('GB', 20.0, 'VAT', TRUE, 1),
('US', 0.0, 'Sales Tax', FALSE, 1),
('DE', 19.0, 'VAT', FALSE, 1),
('FR', 20.0, 'VAT', FALSE, 1),
('ES', 21.0, 'VAT', FALSE, 1),
('IT', 22.0, 'VAT', FALSE, 1),
('NL', 21.0, 'VAT', FALSE, 1),
('BE', 21.0, 'VAT', FALSE, 1),
('PL', 23.0, 'VAT', FALSE, 1)
ON CONFLICT (tenant_id, country_code) DO NOTHING;

COMMENT ON TABLE tax_rule IS 'Tax rules for VAT/GST calculation per country';
COMMENT ON COLUMN tax_rule.is_b2b_exempt IS 'If true, B2B transactions are exempt from tax';