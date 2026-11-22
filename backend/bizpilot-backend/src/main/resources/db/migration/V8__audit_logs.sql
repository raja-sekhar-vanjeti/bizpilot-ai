CREATE TABLE audit_logs (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id     UUID NOT NULL REFERENCES users(id),
    action       VARCHAR(20) NOT NULL,
    entity_type  VARCHAR(40) NOT NULL,
    entity_id    VARCHAR(64),
    summary      VARCHAR(500) NOT NULL,
    created_at   TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_audit_logs_owner_created ON audit_logs (owner_id, created_at);
CREATE INDEX idx_audit_logs_owner_entity_type ON audit_logs (owner_id, entity_type);
