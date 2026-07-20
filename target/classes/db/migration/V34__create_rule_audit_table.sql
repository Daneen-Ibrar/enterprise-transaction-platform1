-- Rule audit table – tracks changes to approval/refund/suspicion rules
CREATE TABLE IF NOT EXISTS rule_audit (
    id BIGSERIAL PRIMARY KEY,
    rule_type VARCHAR(30) NOT NULL,          -- APPROVAL, REFUND, SUSPICION
    rule_id BIGINT NOT NULL,                 -- ID of the rule that was changed
    action VARCHAR(20) NOT NULL,             -- CREATE, UPDATE, DELETE, TOGGLE
    old_values TEXT,                         -- JSON of old values (before change)
    new_values TEXT,                         -- JSON of new values (after change)
    changed_by BIGINT NOT NULL,              -- User ID who made the change
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for fast lookups
CREATE INDEX idx_rule_audit_rule_type ON rule_audit(rule_type);
CREATE INDEX idx_rule_audit_rule_id ON rule_audit(rule_id);
CREATE INDEX idx_rule_audit_changed_by ON rule_audit(changed_by);
CREATE INDEX idx_rule_audit_created_at ON rule_audit(created_at DESC);