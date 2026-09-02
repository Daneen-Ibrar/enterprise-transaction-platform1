-- ============================================================
-- V86: Seed default tax rules for ALL tenants
-- ============================================================
-- This migration ensures every tenant (current and future) 
-- gets the default tax rules automatically.
-- ============================================================

-- Create a function that seeds tax rules for a given tenant
CREATE OR REPLACE FUNCTION seed_tax_rules_for_tenant(p_tenant_id BIGINT)
RETURNS VOID AS $$
BEGIN
    -- Insert default tax rules for this tenant
    INSERT INTO tax_rule (country_code, tax_rate, tax_name, is_default, tenant_id, is_active)
    VALUES 
        ('GB', 20.0, 'VAT', TRUE, p_tenant_id, TRUE),
        ('DE', 19.0, 'VAT', FALSE, p_tenant_id, TRUE),
        ('FR', 20.0, 'VAT', FALSE, p_tenant_id, TRUE),
        ('IT', 22.0, 'VAT', FALSE, p_tenant_id, TRUE),
        ('ES', 21.0, 'VAT', FALSE, p_tenant_id, TRUE),
        ('NL', 21.0, 'VAT', FALSE, p_tenant_id, TRUE),
        ('BE', 21.0, 'VAT', FALSE, p_tenant_id, TRUE),
        ('PL', 23.0, 'VAT', FALSE, p_tenant_id, TRUE),
        ('PT', 23.0, 'VAT', FALSE, p_tenant_id, TRUE),
        ('IE', 23.0, 'VAT', FALSE, p_tenant_id, TRUE),
        ('AT', 20.0, 'VAT', FALSE, p_tenant_id, TRUE),
        ('SE', 25.0, 'VAT', FALSE, p_tenant_id, TRUE),
        ('FI', 24.0, 'VAT', FALSE, p_tenant_id, TRUE),
        ('DK', 25.0, 'VAT', FALSE, p_tenant_id, TRUE),
        ('NO', 25.0, 'VAT', FALSE, p_tenant_id, TRUE),
        ('CH', 7.7, 'VAT', FALSE, p_tenant_id, TRUE),
        ('US', 0.0, 'Sales Tax', FALSE, p_tenant_id, TRUE),
        ('AU', 10.0, 'GST', FALSE, p_tenant_id, TRUE),
        ('NZ', 15.0, 'GST', FALSE, p_tenant_id, TRUE),
        ('CA', 13.0, 'HST', FALSE, p_tenant_id, TRUE)
    ON CONFLICT (tenant_id, country_code) DO NOTHING;
END;
$$ LANGUAGE plpgsql;

-- Seed all existing tenants
DO $$
DECLARE
    tenant_record RECORD;
BEGIN
    FOR tenant_record IN SELECT id FROM tenant LOOP
        PERFORM seed_tax_rules_for_tenant(tenant_record.id);
    END LOOP;
END $$;

-- Create a trigger to automatically seed tax rules when a new tenant is created
CREATE OR REPLACE FUNCTION seed_tax_rules_on_tenant_creation()
RETURNS TRIGGER AS $$
BEGIN
    PERFORM seed_tax_rules_for_tenant(NEW.id);
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Drop trigger if it exists
DROP TRIGGER IF EXISTS trigger_seed_tax_rules_on_tenant_creation ON tenant;

-- Create the trigger
CREATE TRIGGER trigger_seed_tax_rules_on_tenant_creation
AFTER INSERT ON tenant
FOR EACH ROW
EXECUTE FUNCTION seed_tax_rules_on_tenant_creation();

-- Add comments
COMMENT ON FUNCTION seed_tax_rules_for_tenant(BIGINT) IS 'Seeds default tax rules for a tenant';
COMMENT ON FUNCTION seed_tax_rules_on_tenant_creation() IS 'Automatically seeds tax rules when a new tenant is created';
COMMENT ON TRIGGER trigger_seed_tax_rules_on_tenant_creation ON tenant IS 'Triggers tax rule seeding on tenant creation';