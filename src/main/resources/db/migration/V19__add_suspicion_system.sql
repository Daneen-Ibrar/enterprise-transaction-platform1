      -- Add risk fields to invoice
ALTER TABLE invoice ADD COLUMN IF NOT EXISTS risk_level VARCHAR(20) DEFAULT 'GREEN';
ALTER TABLE invoice ADD COLUMN IF NOT EXISTS suspicion_reason TEXT;

-- Create suspicion_rule table
CREATE TABLE IF NOT EXISTS suspicion_rule (
    id BIGSERIAL PRIMARY KEY,
    rule_priority INT NOT NULL,
    condition_expression TEXT NOT NULL,
    risk_level VARCHAR(20) NOT NULL,
    description TEXT,
    active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Seed default rules (case‑insensitive)
INSERT INTO suspicion_rule (rule_priority, condition_expression, risk_level, description) VALUES
    (10, 'description.toLowerCase().contains("urgent")', 'YELLOW', 'Contains "urgent"'),
    (20, 'description.toLowerCase().contains("fraud")', 'RED', 'Contains "fraud"'),
    (30, 'description.toLowerCase().contains("scam")', 'RED', 'Contains "scam"'),
    (40, 'customerEmail.toLowerCase().contains("test")', 'YELLOW', 'Test email domain')
ON CONFLICT DO NOTHING;