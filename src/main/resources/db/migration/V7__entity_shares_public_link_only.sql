ALTER TABLE entity_shares
    DROP CONSTRAINT IF EXISTS chk_entity_shares_access_mode;

ALTER TABLE entity_shares
    ADD CONSTRAINT chk_entity_shares_access_mode
    CHECK (access_mode = 'public_link');

