-- V54__add_unique_currency_pair.sql
-- Remove duplicate rows, keeping only the most recently updated one.
DELETE FROM exchange_rate
WHERE id NOT IN (
    SELECT MIN(id)
    FROM exchange_rate
    GROUP BY from_currency, to_currency
);

-- Add unique constraint to prevent future duplicates.
ALTER TABLE exchange_rate ADD CONSTRAINT unique_currency_pair UNIQUE (from_currency, to_currency);