-- V5 made (invoice_id, reminder_type, status) unique, which rejects a second COMPLETED or
-- DISMISSED reminder for the same invoice and type. Only OPEN reminders must be unique.
ALTER TABLE reminders DROP CONSTRAINT IF EXISTS uq_reminder_invoice_type_open;
CREATE UNIQUE INDEX IF NOT EXISTS uq_reminders_open_invoice_type
    ON reminders (invoice_id, reminder_type)
    WHERE status = 'OPEN';
CREATE INDEX IF NOT EXISTS idx_reminders_invoice ON reminders (invoice_id);
