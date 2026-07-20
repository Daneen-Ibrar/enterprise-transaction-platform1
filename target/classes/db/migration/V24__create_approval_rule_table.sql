-- ============================================================
-- V26: Create approval_rule table (policy-driven approval rules)
-- Replaces hardcoded amount > 5000 threshold
-- ============================================================

CREATE TABLE IF NOT EXISTS approval_rule (
    id BIGSERIAL PRIMARY KEY,
    rule_priority INT NOT NULL,
    condition_expression TEXT NOT NULL,
    requires_approval BOOLEAN NOT NULL DEFAULT TRUE,
    description TEXT,
    active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Seed default rules
INSERT INTO approval_rule (rule_priority, condition_expression, requires_approval, description)
VALUES
    (10, '#amount > 5000', TRUE, 'Amount exceeds £5,000 – requires approval'),
    (20, '#amount <= 5000', FALSE, 'Amount £5,000 or less – auto-approved')
ON CONFLICT DO NOTHING;

-- (Optional) More complex rules:
-- (30, '#amount > 10000 && #merchantRiskLevel == "HIGH"', TRUE, 'High-risk merchant over £10,000')