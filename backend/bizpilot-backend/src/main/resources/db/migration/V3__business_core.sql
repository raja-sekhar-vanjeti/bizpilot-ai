CREATE TABLE customers (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id        UUID NOT NULL REFERENCES users(id),
    name            VARCHAR(160) NOT NULL,
    email           VARCHAR(255),
    phone           VARCHAR(20),
    company_name    VARCHAR(160),
    billing_address VARCHAR(500),
    notes           VARCHAR(1000),
    archived        BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_customers_owner_name ON customers (owner_id, name);
CREATE INDEX idx_customers_owner_email ON customers (owner_id, email);

CREATE TABLE invoices (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id            UUID NOT NULL REFERENCES users(id),
    customer_id         UUID NOT NULL REFERENCES customers(id),
    invoice_number      VARCHAR(40) NOT NULL,
    invoice_date        DATE NOT NULL,
    due_date            DATE NOT NULL,
    subtotal            NUMERIC(14,2) NOT NULL DEFAULT 0,
    tax_percent         NUMERIC(5,2) NOT NULL DEFAULT 0,
    tax_amount          NUMERIC(14,2) NOT NULL DEFAULT 0,
    total_amount        NUMERIC(14,2) NOT NULL DEFAULT 0,
    paid_amount         NUMERIC(14,2) NOT NULL DEFAULT 0,
    outstanding_amount  NUMERIC(14,2) NOT NULL DEFAULT 0,
    notes               VARCHAR(1000),
    status              VARCHAR(20) NOT NULL,
    created_at          TIMESTAMP NOT NULL DEFAULT now(),
    updated_at          TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uq_invoice_owner_number UNIQUE (owner_id, invoice_number),
    CONSTRAINT ck_invoice_status CHECK (status IN ('DRAFT','SENT','PARTIALLY_PAID','PAID','OVERDUE','CANCELLED'))
);

CREATE INDEX idx_invoices_owner_status ON invoices (owner_id, status);
CREATE INDEX idx_invoices_owner_due_date ON invoices (owner_id, due_date);
CREATE INDEX idx_invoices_customer ON invoices (customer_id);

CREATE TABLE invoice_items (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    invoice_id  UUID NOT NULL REFERENCES invoices(id) ON DELETE CASCADE,
    description VARCHAR(300) NOT NULL,
    quantity    NUMERIC(12,2) NOT NULL,
    unit_price  NUMERIC(14,2) NOT NULL,
    line_total  NUMERIC(14,2) NOT NULL
);

CREATE INDEX idx_invoice_items_invoice ON invoice_items (invoice_id);

CREATE TABLE payments (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id     UUID NOT NULL REFERENCES users(id),
    invoice_id   UUID NOT NULL REFERENCES invoices(id),
    amount       NUMERIC(14,2) NOT NULL CHECK (amount > 0),
    payment_date DATE NOT NULL,
    method       VARCHAR(20) NOT NULL CHECK (method IN ('CASH','BANK_TRANSFER','UPI','CARD','OTHER')),
    reference    VARCHAR(100),
    notes        VARCHAR(500),
    created_at   TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_payments_invoice ON payments (invoice_id);
CREATE INDEX idx_payments_owner_date ON payments (owner_id, payment_date);

CREATE TABLE expenses (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id       UUID NOT NULL REFERENCES users(id),
    description    VARCHAR(300) NOT NULL,
    category       VARCHAR(20) NOT NULL CHECK (category IN ('RENT','SALARY','SOFTWARE','MARKETING','TRAVEL','UTILITIES','EQUIPMENT','OTHER')),
    amount         NUMERIC(14,2) NOT NULL CHECK (amount > 0),
    expense_date   DATE NOT NULL,
    payment_method VARCHAR(20) NOT NULL CHECK (payment_method IN ('CASH','BANK_TRANSFER','UPI','CARD','OTHER')),
    notes          VARCHAR(500),
    created_at     TIMESTAMP NOT NULL DEFAULT now(),
    updated_at     TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_expenses_owner_date ON expenses (owner_id, expense_date);
