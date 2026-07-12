-- Insert default rules if they don't exist
INSERT INTO suspicion_rule (rule_priority, condition_expression, risk_level, description)
SELECT 10, 'description.toLowerCase().contains("urgent")', 'YELLOW', 'Contains "urgent"'
WHERE NOT EXISTS (SELECT 1 FROM suspicion_rule WHERE condition_expression = 'description.toLowerCase().contains("urgent")');

INSERT INTO suspicion_rule (rule_priority, condition_expression, risk_level, description)
SELECT 20, 'description.toLowerCase().contains("fraud")', 'RED', 'Contains "fraud"'
WHERE NOT EXISTS (SELECT 1 FROM suspicion_rule WHERE condition_expression = 'description.toLowerCase().contains("fraud")');

INSERT INTO suspicion_rule (rule_priority, condition_expression, risk_level, description)
SELECT 30, 'description.toLowerCase().contains("scam")', 'RED', 'Contains "scam"'
WHERE NOT EXISTS (SELECT 1 FROM suspicion_rule WHERE condition_expression = 'description.toLowerCase().contains("scam")');

INSERT INTO suspicion_rule (rule_priority, condition_expression, risk_level, description)
SELECT 40, 'customerEmail.toLowerCase().contains("test")', 'YELLOW', 'Test email domain'
WHERE NOT EXISTS (SELECT 1 FROM suspicion_rule WHERE condition_expression = 'customerEmail.toLowerCase().contains("test")');