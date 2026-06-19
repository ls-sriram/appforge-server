BEGIN;

CREATE TABLE IF NOT EXISTS documents (
    id             VARCHAR(255) PRIMARY KEY,
    owner_uid      VARCHAR(255) NOT NULL REFERENCES app_users(uid) ON DELETE CASCADE,
    title          VARCHAR(160) NOT NULL,
    tag            VARCHAR(64) NOT NULL,
    version        VARCHAR(32) NOT NULL,
    content        TEXT NOT NULL,
    content_length INTEGER NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_documents_content_length_nonnegative CHECK (content_length >= 0),
    -- Keep DB hard-limit aligned with RuntimeOptions.documentMaxContentChars default.
    CONSTRAINT chk_documents_content_length_max CHECK (content_length <= 20000)
);

CREATE INDEX IF NOT EXISTS idx_documents_owner_created
    ON documents(owner_uid, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_documents_owner_tag
    ON documents(owner_uid, tag);

ALTER TABLE documents ENABLE ROW LEVEL SECURITY;

CREATE POLICY documents_user_access ON documents
    USING (owner_uid = current_setting('app.user_id', true))
    WITH CHECK (owner_uid = current_setting('app.user_id', true));

COMMIT;
