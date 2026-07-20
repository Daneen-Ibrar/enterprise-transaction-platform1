CREATE TABLE IF NOT EXISTS private_message (
    id BIGSERIAL PRIMARY KEY,
    invoice_id BIGINT NOT NULL REFERENCES invoice(id) ON DELETE CASCADE,
    sender_id BIGINT NOT NULL REFERENCES app_user(id),
    recipient_id BIGINT NOT NULL REFERENCES app_user(id),
    message TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_private_message_invoice_id ON private_message(invoice_id);
CREATE INDEX idx_private_message_sender_id ON private_message(sender_id);
CREATE INDEX idx_private_message_recipient_id ON private_message(recipient_id);