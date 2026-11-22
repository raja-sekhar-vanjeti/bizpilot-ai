CREATE TABLE email_deliveries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id UUID NOT NULL REFERENCES users(id),
    reminder_id UUID NOT NULL REFERENCES reminders(id),
    recipient_email VARCHAR(255) NOT NULL,
    subject VARCHAR(300) NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING','SENT','FAILED')),
    failure_reason VARCHAR(255),
    sent_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX idx_email_deliveries_reminder ON email_deliveries (reminder_id, status);
CREATE INDEX idx_email_deliveries_owner ON email_deliveries (owner_id);
