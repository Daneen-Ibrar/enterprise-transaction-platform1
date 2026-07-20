ALTER TABLE invoice ADD COLUMN currency VARCHAR(3) NOT NULL DEFAULT 'GBP';
ALTER TABLE transaction ADD COLUMN currency VARCHAR(3) NOT NULL DEFAULT 'GBP';

CREATE TABLE IF NOT EXISTS exchange_rate (
    id BIGSERIAL PRIMARY KEY,
    from_currency VARCHAR(3) NOT NULL,
    to_currency VARCHAR(3) NOT NULL,
    rate DECIMAL(19,6) NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Seed default rates (stub)
INSERT INTO exchange_rate (from_currency, to_currency, rate) VALUES
    ('GBP', 'USD', 1.27),
    ('GBP', 'EUR', 1.17),
    ('USD', 'GBP', 0.79),
    ('USD', 'EUR', 0.92),
    ('EUR', 'GBP', 0.85),
    ('EUR', 'USD', 1.09)
ON CONFLICT DO NOTHING;