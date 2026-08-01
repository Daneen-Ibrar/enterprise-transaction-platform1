CREATE TABLE IF NOT EXISTS invoice_message (
    id BIGSERIAL PRIMARY KEY,
    invoice_id BIGINT NOT NULL REFERENCES invoice(id) ON DELETE CASCADE,
    sender_id BIGINT NOT NULL REFERENCES app_user(id),
    message TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_invoice_message_invoice_id ON invoice_message(invoice_id);