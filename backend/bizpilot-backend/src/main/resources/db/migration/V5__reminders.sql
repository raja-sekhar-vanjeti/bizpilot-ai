CREATE TABLE reminders (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id UUID NOT NULL REFERENCES users(id),
    invoice_id UUID NOT NULL REFERENCES invoices(id),
    reminder_type VARCHAR(20) NOT NULL CHECK (reminder_type IN ('UPCOMING','OVERDUE')),
    status VARCHAR(20) NOT NULL CHECK (status IN ('OPEN','COMPLETED','DISMISSED')),
    message TEXT NOT NULL,
    reminder_date DATE NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uq_reminder_invoice_type_open UNIQUE (invoice_id, reminder_type, status)
);
CREATE INDEX idx_reminders_owner_status ON reminders (owner_id, status);
