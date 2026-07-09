-- Ensure refund rules use #amount for SpEL variables
UPDATE refund_rule
SET condition_expression = REPLACE(condition_expression, 'amount', '#amount')
WHERE condition_expression LIKE 'amount%';

-- If no rules exist, insert default ones (optional)
INSERT INTO refund_rule (rule_priority, condition_expression, action, active)
SELECT 1, '#amount <= 1000', 'ALLOW', true
WHERE NOT EXISTS (SELECT 1 FROM refund_rule);

INSERT INTO refund_rule (rule_priority, condition_expression, action, active)
SELECT 2, '#amount > 1000', 'DENY', true
WHERE NOT EXISTS (SELECT 1 FROM refund_rule WHERE rule_priority = 2);