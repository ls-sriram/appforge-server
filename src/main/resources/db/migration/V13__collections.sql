BEGIN;

-- Schemaless JSONB document store for the collections API.
--
-- This is NOT the primary storage layer. Platform features (auth, billing,
-- sharing, tasks, documents) use dedicated, normalized tables with typed
-- schemas. This table exists as a convenience for client apps that need to
-- store arbitrary JSON without a server-side schema or repository.
--
-- Records are keyed by (app_id, collection, id): different apps and
-- different collection names within the same app are fully isolated.
CREATE TABLE IF NOT EXISTS custom_collections (
    id          VARCHAR(255)    NOT NULL,
    app_id      VARCHAR(255)    NOT NULL,
    collection  VARCHAR(255)    NOT NULL,
    owner_uid   VARCHAR(255)    NOT NULL REFERENCES app_users(uid) ON DELETE CASCADE,
    data        JSONB           NOT NULL DEFAULT '{}',
    created_at  TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    PRIMARY KEY (app_id, collection, id)
);

CREATE INDEX IF NOT EXISTS idx_custom_collections_owner_created
    ON custom_collections(app_id, collection, owner_uid, created_at DESC);

ALTER TABLE custom_collections ENABLE ROW LEVEL SECURITY;

CREATE POLICY custom_collections_user_access ON custom_collections
    USING  (owner_uid = current_setting('app.user_id', true))
    WITH CHECK (owner_uid = current_setting('app.user_id', true));

COMMIT;
