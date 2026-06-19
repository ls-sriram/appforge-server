BEGIN;

CREATE TABLE IF NOT EXISTS recordings (
    id               VARCHAR(255) PRIMARY KEY,
    uid              VARCHAR(255) NOT NULL REFERENCES app_users(uid) ON DELETE CASCADE,
    audio_bytes      BYTEA NOT NULL,
    content_type     VARCHAR(255) NOT NULL,
    size_bytes       BIGINT NOT NULL,
    duration_seconds INTEGER,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_recordings_uid_created
    ON recordings(uid, created_at DESC);

ALTER TABLE recordings ENABLE ROW LEVEL SECURITY;

CREATE POLICY recordings_user_access ON recordings
    USING (uid = current_setting('app.user_id', true))
    WITH CHECK (uid = current_setting('app.user_id', true));

COMMIT;
