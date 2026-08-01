ALTER TABLE invoice ADD COLUMN woo_order_id BIGINT;
ALTER TABLE invoice ADD COLUMN webhook_url TEXT;
ALTER TABLE invoice ADD COLUMN return_url TEXT;