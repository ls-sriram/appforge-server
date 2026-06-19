CREATE TABLE IF NOT EXISTS platform_records (
    id         VARCHAR(255) NOT NULL,
    collection VARCHAR(255) NOT NULL,
    data       JSONB        NOT NULL DEFAULT '{}',
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    PRIMARY KEY (id, collection)
);

CREATE INDEX IF NOT EXISTS idx_platform_records_collection
ON platform_records(collection);

CREATE INDEX IF NOT EXISTS idx_platform_records_created
ON platform_records(collection, created_at DESC);
