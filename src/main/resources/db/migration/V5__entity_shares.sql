-- Canonical share model (public-link first).
CREATE TABLE IF NOT EXISTS entity_shares (
    id           VARCHAR(255) PRIMARY KEY,
    owner_uid    VARCHAR(255) NOT NULL REFERENCES app_users(uid) ON DELETE CASCADE,
    entity_type  VARCHAR(100) NOT NULL,
    entity_id    VARCHAR(255) NOT NULL,
    access_mode  VARCHAR(50) NOT NULL,
    token_hash   VARCHAR(128) NOT NULL,
    expires_at   TIMESTAMPTZ,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by   VARCHAR(255) NOT NULL,
    revoked_at   TIMESTAMPTZ,
    revoked_by   VARCHAR(255)
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_entity_shares_token_hash
    ON entity_shares(token_hash);

CREATE UNIQUE INDEX IF NOT EXISTS idx_entity_shares_active_public_per_entity
    ON entity_shares(owner_uid, entity_type, entity_id)
    WHERE access_mode = 'public_link' AND revoked_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_entity_shares_owner_created
    ON entity_shares(owner_uid, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_entity_shares_entity_lookup
    ON entity_shares(owner_uid, entity_type, entity_id, created_at DESC);

