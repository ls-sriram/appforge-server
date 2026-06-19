-- name: sharing.insert_entity_share
INSERT INTO entity_shares (
  id,
  owner_uid,
  entity_type,
  entity_id,
  access_mode,
  token_hash,
  expires_at,
  created_at,
  created_by,
  revoked_at,
  revoked_by
)
VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);

-- name: sharing.select_entity_share_by_token_hash
SELECT id, owner_uid, entity_type, entity_id, access_mode, token_hash, expires_at, created_at, created_by, revoked_at, revoked_by
FROM entity_shares
WHERE token_hash = ?
LIMIT 1;

-- name: sharing.select_active_public_by_entity
SELECT id, owner_uid, entity_type, entity_id, access_mode, token_hash, expires_at, created_at, created_by, revoked_at, revoked_by
FROM entity_shares
WHERE owner_uid = ?
  AND entity_type = ?
  AND entity_id = ?
  AND access_mode = 'public_link'
  AND revoked_at IS NULL
  AND (expires_at IS NULL OR expires_at > NOW())
ORDER BY created_at DESC
LIMIT 1;

-- name: sharing.list_active_by_owner
SELECT id, owner_uid, entity_type, entity_id, access_mode, token_hash, expires_at, created_at, created_by, revoked_at, revoked_by
FROM entity_shares
WHERE owner_uid = ?
  AND revoked_at IS NULL
  AND (expires_at IS NULL OR expires_at > NOW())
ORDER BY created_at DESC
LIMIT ?;

-- name: sharing.list_active_by_owner_entity
SELECT id, owner_uid, entity_type, entity_id, access_mode, token_hash, expires_at, created_at, created_by, revoked_at, revoked_by
FROM entity_shares
WHERE owner_uid = ?
  AND entity_type = ?
  AND entity_id = ?
  AND revoked_at IS NULL
  AND (expires_at IS NULL OR expires_at > NOW())
ORDER BY created_at DESC
LIMIT ?;

-- name: sharing.revoke_by_token_hash_and_owner
UPDATE entity_shares
SET revoked_at = ?, revoked_by = ?
WHERE token_hash = ?
  AND owner_uid = ?
  AND revoked_at IS NULL;

