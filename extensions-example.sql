-- Example Extension Table Definition
-- Extensions can define their own tables using this pattern
CREATE TABLE IF NOT EXISTS extension_entities (
    id VARCHAR(255) PRIMARY KEY,
    name VARCHAR(255)
);
