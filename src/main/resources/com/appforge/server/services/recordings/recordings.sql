-- name: recordings.insert
INSERT INTO recordings (
  id, uid, audio_bytes, content_type, size_bytes, duration_seconds, created_at, updated_at
)
VALUES (?, ?, ?, ?, ?, ?, NOW(), NOW());

-- name: recordings.list_by_uid
SELECT id, uid, content_type, size_bytes, duration_seconds, created_at
FROM recordings
WHERE uid = ?
ORDER BY created_at DESC, id DESC
LIMIT ?;

-- name: recordings.select_content_by_id_and_uid
SELECT id, uid, audio_bytes, content_type, size_bytes, duration_seconds, created_at
FROM recordings
WHERE id = ? AND uid = ?
LIMIT 1;

-- name: recordings.select_exists_by_id_and_uid
SELECT 1
FROM recordings
WHERE id = ? AND uid = ?
LIMIT 1;
