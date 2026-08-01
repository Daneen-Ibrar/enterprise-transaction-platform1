CREATE TABLE IF NOT EXISTS refund_rule (
    id BIGSERIAL PRIMARY KEY,
    rule_priority INT NOT NULL,
    condition_expression TEXT NOT NULL,
    action VARCHAR(30) NOT NULL,
    required_permission VARCHAR(100) NULL,
    active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO refund_rule (rule_priority, condition_expression, action, required_permission)
VALUES (10, 'amount < 1000', 'ALLOW', NULL),
       (20, 'amount >= 1000 AND amount < 5000', 'REQUIRE_APPROVAL', 'refund:approve'),
       (30, 'amount >= 5000', 'DENY', NULL)
ON CONFLICT DO NOTHING;