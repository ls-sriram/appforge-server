CREATE TABLE IF NOT EXISTS reviewer_entity_shares (
    id VARCHAR(255) PRIMARY KEY,
    owner_uid VARCHAR(255) NOT NULL REFERENCES app_users(uid) ON DELETE CASCADE,
    entity_type VARCHAR(100) NOT NULL,
    entity_id VARCHAR(255) NOT NULL,
    reviewer_email VARCHAR(255) NOT NULL,
    reviewer_email_normalized VARCHAR(255) NOT NULL,
    created_by VARCHAR(255) NOT NULL REFERENCES app_users(uid) ON DELETE CASCADE,
    expires_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    revoked_at TIMESTAMPTZ NULL,
    revoked_by VARCHAR(255) NULL REFERENCES app_users(uid) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_reviewer_entity_shares_owner_entity
    ON reviewer_entity_shares(owner_uid, entity_type, entity_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_reviewer_entity_shares_reviewer_email
    ON reviewer_entity_shares(reviewer_email_normalized, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_reviewer_entity_shares_active
    ON reviewer_entity_shares(reviewer_email_normalized, expires_at, revoked_at, created_at DESC);
