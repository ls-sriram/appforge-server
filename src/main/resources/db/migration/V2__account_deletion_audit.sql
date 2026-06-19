BEGIN;

CREATE TABLE IF NOT EXISTS account_deletion_audit_records (
    id          VARCHAR(255) PRIMARY KEY,
    user_id     VARCHAR(255) NOT NULL,
    event       VARCHAR(100) NOT NULL,
    status      VARCHAR(50) NOT NULL,
    detail      TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_account_deletion_audit_user_created
    ON account_deletion_audit_records(user_id, created_at DESC);

COMMIT;
