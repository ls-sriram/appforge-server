ALTER TABLE entity_shares
    ADD CONSTRAINT chk_entity_shares_access_mode
    CHECK (access_mode IN ('public_link', 'private_invite'));

ALTER TABLE entity_shares
    ADD CONSTRAINT chk_entity_shares_revocation_consistency
    CHECK (
        (revoked_at IS NULL AND revoked_by IS NULL)
        OR (revoked_at IS NOT NULL AND revoked_by IS NOT NULL)
    );

ALTER TABLE entity_shares
    ADD CONSTRAINT chk_entity_shares_public_created_by_owner
    CHECK (
        access_mode <> 'public_link'
        OR created_by = owner_uid
    );

DROP TABLE IF EXISTS entity_share_references;

