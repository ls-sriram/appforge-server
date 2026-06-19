-- V1__current_schema.sql
-- Pre-prod baseline: single current schema migration.
-- Migrate from single JSONB document store to normalized relational schema
-- with proper row-level security.
--
-- Ownership model (database-per-app):
--   The platform provides the schema + code.
--   Each application has its own database.
--   The database connection IS the security boundary.
--
-- Platform database (shared, 2 tables):
--   applications, api_calls
--
-- Per-app database (everything else):
--   app_users, billing_*, reviews, entities, shares, share_slots,
--   upload_records, profiles, early_access, billing_audit

BEGIN;

-- ─────────────────────────────────────────────────────────────────
-- 1. APP_USERS — User identity for this application.
--    uid = Firebase Auth UID (unique within this app's database).
-- ─────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS app_users (
    uid            VARCHAR(255) PRIMARY KEY,
    email          VARCHAR(255) NOT NULL,
    email_normalized VARCHAR(255) NOT NULL,
    display_name   VARCHAR(255),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_login_at  TIMESTAMPTZ,
    CONSTRAINT uq_app_users_email UNIQUE (email_normalized)
);

CREATE INDEX idx_app_users_created ON app_users(created_at DESC);

-- ─────────────────────────────────────────────────────────────────
-- 2. PROFILES — App-level user profiles (email → identity).
--    Not global — each app manages its own profiles.
-- ─────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS profiles (
    user_id          VARCHAR(255) PRIMARY KEY REFERENCES app_users(uid) ON DELETE CASCADE,
    display_name     VARCHAR(255),
    email            VARCHAR(255) NOT NULL,
    email_normalized VARCHAR(255) NOT NULL,
    last_seen_at     TIMESTAMPTZ,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_profiles_email UNIQUE (email_normalized)
);

CREATE INDEX idx_profiles_email_normalized ON profiles(email_normalized);

-- ─────────────────────────────────────────────────────────────────
-- 3. BILLING_ENTITLEMENTS — Per-user plan/subscription.
-- ─────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS billing_entitlements (
    user_id                VARCHAR(255) PRIMARY KEY REFERENCES app_users(uid) ON DELETE CASCADE,
    plan                   VARCHAR(50) NOT NULL DEFAULT 'free',
    status                 VARCHAR(50) NOT NULL DEFAULT 'active',
    expires_at             TIMESTAMPTZ,
    started_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    features               JSONB NOT NULL DEFAULT '{}',
    entitlement_source     VARCHAR(50) NOT NULL DEFAULT 'manual',
    external_customer_id   VARCHAR(255),
    external_reference_id  VARCHAR(255),
    billing_type           VARCHAR(50),
    last_payment_amount_cents BIGINT,
    last_payment_currency    VARCHAR(10),
    created_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_billing_entitlements_plan
        CHECK (plan IN ('free', 'trial', 'pro')),
    CONSTRAINT chk_billing_entitlements_status
        CHECK (status IN ('active', 'trialing', 'cancel_pending', 'past_due', 'canceled')),
    CONSTRAINT chk_billing_entitlements_source
        CHECK (entitlement_source IN ('manual', 'trial', 'dodo_payments')),
    CONSTRAINT chk_billing_entitlements_type
        CHECK (billing_type IS NULL OR billing_type IN ('subscription', 'one_time'))
);

CREATE INDEX idx_billing_entitlements_status ON billing_entitlements(status);
CREATE INDEX idx_billing_entitlements_expires ON billing_entitlements(expires_at) WHERE expires_at IS NOT NULL;

-- ─────────────────────────────────────────────────────────────────
-- 4. BILLING_PAYMENTS — Per-user payment history.
-- ─────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS billing_payments (
    id              VARCHAR(255) PRIMARY KEY,
    user_id         VARCHAR(255) NOT NULL REFERENCES app_users(uid) ON DELETE CASCADE,
    date            TIMESTAMPTZ NOT NULL,
    amount_cents    BIGINT NOT NULL,
    currency        VARCHAR(10) NOT NULL,
    plan_id         VARCHAR(100) NOT NULL,
    email_sent_at   TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_billing_payments_user ON billing_payments(user_id);
CREATE INDEX idx_billing_payments_user_date ON billing_payments(user_id, date DESC);

-- ─────────────────────────────────────────────────────────────────
-- 5. BILLING_AUDIT_RECORDS — App-level webhook audit log.
--    Each app tracks its own payment events.
-- ─────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS billing_audit_records (
    id         VARCHAR(255) PRIMARY KEY,
    payload    TEXT NOT NULL,
    timestamp  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    webhook_id VARCHAR(255),
    audit_source     VARCHAR(100) NOT NULL
);

CREATE INDEX idx_billing_audit_webhook ON billing_audit_records(webhook_id) WHERE webhook_id IS NOT NULL;
CREATE INDEX idx_billing_audit_timestamp ON billing_audit_records(timestamp DESC);

-- ─────────────────────────────────────────────────────────────────
-- 6. REVIEWS — Per-user reviews with RLS.
-- ─────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS reviews (
    id              VARCHAR(255) NOT NULL,
    owner_uid       VARCHAR(255) NOT NULL REFERENCES app_users(uid) ON DELETE CASCADE,
    entity_id       VARCHAR(255) NOT NULL,
    entity_category VARCHAR(100) NOT NULL,
    entity_type     VARCHAR(100) NOT NULL,
    author_role     VARCHAR(50) NOT NULL,
    author_id       VARCHAR(255),
    author_name     VARCHAR(255),
    author_email    VARCHAR(255),
    content         JSONB NOT NULL DEFAULT '{}',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (id, owner_uid)
);

CREATE INDEX idx_reviews_user_entity ON reviews(owner_uid, entity_id, entity_category);
CREATE INDEX idx_reviews_user_created ON reviews(owner_uid, created_at DESC);

-- Row-level security: users can only see their own reviews
ALTER TABLE reviews ENABLE ROW LEVEL SECURITY;

CREATE POLICY reviews_user_isolation ON reviews
    USING (owner_uid = current_setting('app.user_id', true));

CREATE POLICY reviews_user_insert ON reviews
    WITH CHECK (owner_uid = current_setting('app.user_id', true));

-- ─────────────────────────────────────────────────────────────────
-- 7. SHARES — Share links within this application.
-- ─────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS shares (
    token                VARCHAR(255) PRIMARY KEY,
    entity_id            VARCHAR(255) NOT NULL,
    entity_category      VARCHAR(100) NOT NULL,
    entity_path          VARCHAR(500),
    owner_uid            VARCHAR(255) NOT NULL REFERENCES app_users(uid) ON DELETE CASCADE,
    expires_at           TIMESTAMPTZ NOT NULL,
    revoked_at           TIMESTAMPTZ,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_shares_owner ON shares(owner_uid);
CREATE INDEX idx_shares_entity ON shares(entity_id, entity_category);
CREATE INDEX idx_shares_expires ON shares(expires_at) WHERE revoked_at IS NULL;

-- ─────────────────────────────────────────────────────────────────
-- 8. SHARE_SLOTS — Per-user index of what they've shared (RLS).
-- ─────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS share_slots (
    owner_uid        VARCHAR(255) NOT NULL,
    entity_key       VARCHAR(500) NOT NULL,
    token            VARCHAR(255) NOT NULL REFERENCES shares(token) ON DELETE CASCADE,
    entity_id        VARCHAR(255) NOT NULL,
    entity_category  VARCHAR(100) NOT NULL,
    original_owner_uid VARCHAR(255) NOT NULL,
    expires_at       TIMESTAMPTZ NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (owner_uid, entity_key),
    FOREIGN KEY (owner_uid) REFERENCES app_users(uid) ON DELETE CASCADE
);

CREATE INDEX idx_share_slots_token ON share_slots(token);

-- Row-level security
ALTER TABLE share_slots ENABLE ROW LEVEL SECURITY;

CREATE POLICY share_slots_user_isolation ON share_slots
    USING (owner_uid = current_setting('app.user_id', true));

CREATE POLICY share_slots_user_insert ON share_slots
    WITH CHECK (owner_uid = current_setting('app.user_id', true));

-- ─────────────────────────────────────────────────────────────────
-- 9. ENTITIES — Per-user flexible entities (JSONB + RLS).
--     The `data` column stays as JSONB for frontend-defined schemas.
-- ─────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS entities (
    id          VARCHAR(255) NOT NULL,
    owner_uid   VARCHAR(255) NOT NULL REFERENCES app_users(uid) ON DELETE CASCADE,
    category    VARCHAR(100) NOT NULL,
    data        JSONB NOT NULL DEFAULT '{}',
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (id, owner_uid)
);

CREATE INDEX idx_entities_user ON entities(owner_uid);
CREATE INDEX idx_entities_user_category ON entities(owner_uid, category);
CREATE INDEX idx_entities_data_gin ON entities USING GIN (data);

-- Row-level security
ALTER TABLE entities ENABLE ROW LEVEL SECURITY;

CREATE POLICY entities_user_isolation ON entities
    USING (owner_uid = current_setting('app.user_id', true));

CREATE POLICY entities_user_insert ON entities
    WITH CHECK (owner_uid = current_setting('app.user_id', true));

-- ─────────────────────────────────────────────────────────────────
-- 10. EARLY_ACCESS_ENTRIES — App-level waitlist/approval.
-- ─────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS early_access_entries (
    email       VARCHAR(255) PRIMARY KEY,
    status      VARCHAR(50) NOT NULL DEFAULT 'waitlist',
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ,
    approved_at TIMESTAMPTZ
);

CREATE INDEX idx_early_access_status ON early_access_entries(status);

-- ─────────────────────────────────────────────────────────────────
-- 11. UPLOAD_RECORDS — GCS upload metadata.
-- ─────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS upload_records (
    upload_id            VARCHAR(255) PRIMARY KEY,
    uid                  VARCHAR(255) NOT NULL REFERENCES app_users(uid) ON DELETE CASCADE,
    type                 VARCHAR(100) NOT NULL,
    entity_id            VARCHAR(255) NOT NULL,
    bucket               VARCHAR(255) NOT NULL,
    object_name          VARCHAR(1000) NOT NULL,
    content_type         VARCHAR(255) NOT NULL,
    size_bytes           BIGINT NOT NULL,
    status               VARCHAR(50) NOT NULL DEFAULT 'pending',
    created_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at           TIMESTAMPTZ,
    CONSTRAINT chk_upload_records_type
        CHECK (type IN ('image', 'audio', 'video', 'document')),
    CONSTRAINT chk_upload_records_status
        CHECK (status IN ('pending', 'processing', 'completed', 'failed'))
);

CREATE INDEX idx_uploads_user ON upload_records(uid);
CREATE INDEX idx_uploads_status ON upload_records(status);
CREATE INDEX idx_uploads_expires ON upload_records(expires_at) WHERE expires_at IS NOT NULL;

-- ─────────────────────────────────────────────────────────────────
-- 12. HELPER FUNCTION: set RLS context for user
-- ─────────────────────────────────────────────────────────────────
CREATE OR REPLACE FUNCTION set_current_user_id(uid VARCHAR) RETURNS void AS $$
BEGIN
    PERFORM set_config('app.user_id', uid, false);
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- User-owned table RLS
ALTER TABLE app_users ENABLE ROW LEVEL SECURITY;
CREATE POLICY app_users_self_access ON app_users
    USING (uid = current_setting('app.user_id', true))
    WITH CHECK (uid = current_setting('app.user_id', true));

ALTER TABLE billing_entitlements ENABLE ROW LEVEL SECURITY;
CREATE POLICY billing_entitlements_user_access ON billing_entitlements
    USING (user_id = current_setting('app.user_id', true))
    WITH CHECK (user_id = current_setting('app.user_id', true));

ALTER TABLE billing_payments ENABLE ROW LEVEL SECURITY;
CREATE POLICY billing_payments_user_access ON billing_payments
    USING (user_id = current_setting('app.user_id', true))
    WITH CHECK (user_id = current_setting('app.user_id', true));

ALTER TABLE shares ENABLE ROW LEVEL SECURITY;
CREATE POLICY shares_user_access ON shares
    USING (owner_uid = current_setting('app.user_id', true))
    WITH CHECK (owner_uid = current_setting('app.user_id', true));

ALTER TABLE upload_records ENABLE ROW LEVEL SECURITY;
CREATE POLICY upload_records_user_access ON upload_records
    USING (uid = current_setting('app.user_id', true))
    WITH CHECK (uid = current_setting('app.user_id', true));

DO $$
BEGIN
    IF to_regclass('public.onboarding_state') IS NOT NULL THEN
        ALTER TABLE onboarding_state ENABLE ROW LEVEL SECURITY;
        CREATE POLICY onboarding_state_user_access ON onboarding_state
            USING (user_id = current_setting('app.user_id', true))
            WITH CHECK (user_id = current_setting('app.user_id', true));
    END IF;
END $$;

DO $$
BEGIN
    IF to_regclass('public.onboarding_responses') IS NOT NULL THEN
        ALTER TABLE onboarding_responses ENABLE ROW LEVEL SECURITY;
        CREATE POLICY onboarding_responses_user_access ON onboarding_responses
            USING (user_id = current_setting('app.user_id', true))
            WITH CHECK (user_id = current_setting('app.user_id', true));
    END IF;
END $$;

-- ─────────────────────────────────────────────────────────────────
-- 13. PLATFORM TABLES
-- ─────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS platform_applications (
    app_id       VARCHAR(100) PRIMARY KEY,
    name         VARCHAR(255) NOT NULL,
    config       TEXT NOT NULL,
    status       VARCHAR(50) NOT NULL DEFAULT 'active',
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_platform_apps_status ON platform_applications(status);

CREATE TABLE IF NOT EXISTS platform_api_calls (
    id           VARCHAR(255) PRIMARY KEY,
    app_id       VARCHAR(100),
    request_id   VARCHAR(255),
    timestamp    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    method       VARCHAR(10) NOT NULL,
    path         VARCHAR(500) NOT NULL,
    raw_path     VARCHAR(1000) NOT NULL,
    status       INTEGER NOT NULL,
    duration_ms  DOUBLE PRECISION NOT NULL,
    user_id      VARCHAR(255),
    team_id      VARCHAR(255),
    user_agent   TEXT,
    error        TEXT
);

CREATE INDEX IF NOT EXISTS idx_platform_api_timestamp ON platform_api_calls(timestamp);
CREATE INDEX IF NOT EXISTS idx_platform_api_app ON platform_api_calls(app_id);
CREATE INDEX IF NOT EXISTS idx_platform_api_user ON platform_api_calls(user_id);
CREATE INDEX IF NOT EXISTS idx_platform_api_path ON platform_api_calls(path);

-- ─────────────────────────────────────────────────────────────────
-- 14. UPLOAD PROCESSED EVENTS
-- ─────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS upload_processed_events (
    event_id                  VARCHAR(1200) PRIMARY KEY,
    bucket                    VARCHAR(255) NOT NULL,
    object_name               VARCHAR(1000) NOT NULL,
    generation                BIGINT NOT NULL,
    size_bytes                BIGINT NOT NULL,
    content_type              VARCHAR(255),
    event_time_epoch_seconds  BIGINT,
    processed_at              TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_upload_processed_at ON upload_processed_events(processed_at DESC);

-- V3__onboarding_schema.sql
-- Pre-prod onboarding baseline (normalized relational model only).
-- No compatibility path with legacy JSON/question_key rows.


-- ─────────────────────────────────────────────────────────────────
-- 1. ONBOARDING_QUESTIONS — global onboarding question catalog
-- ─────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS onboarding_questions (
    id             VARCHAR(255) PRIMARY KEY,
    step_type      VARCHAR(100) NOT NULL,
    field_key      VARCHAR(100) NOT NULL,
    prompt         TEXT NOT NULL,
    question_type  VARCHAR(50) NOT NULL,
    display_order  INTEGER NOT NULL,
    is_active      BOOLEAN NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_onboarding_questions_field UNIQUE (step_type, field_key),
    CONSTRAINT chk_onboarding_questions_type
        CHECK (question_type IN ('single_select', 'multi_select', 'text'))
);

CREATE INDEX IF NOT EXISTS idx_onboarding_questions_active_order
    ON onboarding_questions(is_active, display_order);

-- ─────────────────────────────────────────────────────────────────
-- 2. ONBOARDING_QUESTION_OPTIONS — selectable answer options
-- ─────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS onboarding_question_options (
    id             VARCHAR(255) PRIMARY KEY,
    question_id    VARCHAR(255) NOT NULL REFERENCES onboarding_questions(id) ON DELETE CASCADE,
    value_key      VARCHAR(100) NOT NULL,
    label          TEXT NOT NULL,
    display_order  INTEGER NOT NULL,
    is_active      BOOLEAN NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_onboarding_question_option_value UNIQUE (question_id, value_key)
);

CREATE INDEX IF NOT EXISTS idx_onboarding_question_options_active_order
    ON onboarding_question_options(question_id, is_active, display_order);

-- ─────────────────────────────────────────────────────────────────
-- 3. ONBOARDING_STATE — current progress pointer/state
-- ─────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS onboarding_state (
    user_id       VARCHAR(255) PRIMARY KEY REFERENCES app_users(uid) ON DELETE CASCADE,
    version       INTEGER NOT NULL DEFAULT 1,
    current_step  INTEGER NOT NULL DEFAULT 1,
    completed     BOOLEAN NOT NULL DEFAULT FALSE,
    completed_at  TIMESTAMPTZ,
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_onboarding_state_completed
    ON onboarding_state(completed, updated_at DESC);

-- ─────────────────────────────────────────────────────────────────
-- 4. ONBOARDING_RESPONSES — normalized answers per question
-- ─────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS onboarding_responses (
    response_id   BIGSERIAL PRIMARY KEY,
    user_id       VARCHAR(255) NOT NULL REFERENCES app_users(uid) ON DELETE CASCADE,
    question_id   VARCHAR(255) NOT NULL REFERENCES onboarding_questions(id) ON DELETE CASCADE,
    option_id     VARCHAR(255) REFERENCES onboarding_question_options(id) ON DELETE SET NULL,
    text_value    TEXT,
    answered_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_onboarding_responses_answered
    ON onboarding_responses(user_id, answered_at DESC);


COMMIT;
