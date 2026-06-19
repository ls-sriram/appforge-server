-- Platform Records Table
CREATE TABLE IF NOT EXISTS platform_records (
    id         VARCHAR(255) NOT NULL,
    collection VARCHAR(255) NOT NULL,
    data       JSONB        NOT NULL DEFAULT '{}',
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    PRIMARY KEY (id, collection)
);

-- Indexes
CREATE INDEX IF NOT EXISTS idx_platform_records_collection
ON platform_records(collection);

CREATE INDEX IF NOT EXISTS idx_platform_records_created
ON platform_records(collection, created_at DESC);

-- ─── Analytics ────────────────────────────────────────────────────────────

-- API Calls Table — stores every HTTP request for analytics
CREATE TABLE IF NOT EXISTS api_calls (
    id         VARCHAR(255) NOT NULL,
    collection VARCHAR(255) NOT NULL DEFAULT 'api_calls',
    data       JSONB        NOT NULL DEFAULT '{}',
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    PRIMARY KEY (id, collection)
);

-- Indexes for analytics queries
CREATE INDEX IF NOT EXISTS idx_api_calls_collection
ON api_calls(collection);

CREATE INDEX IF NOT EXISTS idx_api_calls_created
ON api_calls(collection, created_at DESC);
