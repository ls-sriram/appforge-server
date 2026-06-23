-- name: collections.insert
INSERT INTO custom_collections (id, app_id, collection, owner_uid, data)
VALUES (?, ?, ?, ?, ?::jsonb);

-- name: collections.select_by_id
SELECT id, app_id, collection, owner_uid, data::text AS data_json, created_at, updated_at
FROM custom_collections
WHERE id = ? AND app_id = ? AND collection = ? AND owner_uid = ?;

-- name: collections.list
SELECT id, app_id, collection, owner_uid, data::text AS data_json, created_at, updated_at
FROM custom_collections
WHERE app_id = ? AND collection = ? AND owner_uid = ?
ORDER BY created_at DESC
LIMIT ?;

-- name: collections.update
UPDATE custom_collections
SET data = ?::jsonb, updated_at = NOW()
WHERE id = ? AND app_id = ? AND collection = ? AND owner_uid = ?;

-- name: collections.delete
DELETE FROM custom_collections
WHERE id = ? AND app_id = ? AND collection = ? AND owner_uid = ?;
