-- name: documents.upsert
INSERT INTO documents (
  id,
  owner_uid,
  title,
  tag,
  version,
  content,
  content_length,
  created_at,
  updated_at
)
VALUES (?, ?, ?, ?, ?, ?, ?, NOW(), NOW())
ON CONFLICT (id)
DO UPDATE SET
  title = EXCLUDED.title,
  tag = EXCLUDED.tag,
  version = EXCLUDED.version,
  content = EXCLUDED.content,
  content_length = EXCLUDED.content_length,
  updated_at = NOW();

-- name: documents.select_by_id_and_owner
SELECT id, owner_uid, title, tag, version, content, content_length, created_at, updated_at
FROM documents
WHERE id = ? AND owner_uid = ?
LIMIT 1;

-- name: documents.list_by_owner
SELECT id, owner_uid, title, tag, version, content, content_length, created_at, updated_at
FROM documents
WHERE owner_uid = ?
ORDER BY created_at DESC, id DESC
LIMIT ?;

-- name: documents.select_exists_by_id_and_owner
SELECT 1
FROM documents
WHERE id = ? AND owner_uid = ?
LIMIT 1;

-- name: documents.count_by_owner_buckets
SELECT date_trunc(?::text, created_at) AS bucket_start, COUNT(*)::bigint AS count_value
FROM documents
WHERE owner_uid = ?
  AND (?::timestamptz IS NULL OR created_at >= ?::timestamptz)
  AND (?::timestamptz IS NULL OR created_at < ?::timestamptz)
GROUP BY 1
ORDER BY 1 ASC;

-- name: documents.count_by_owner_total
SELECT COUNT(*)::bigint AS count_value
FROM documents
WHERE owner_uid = ?
  AND (?::timestamptz IS NULL OR created_at >= ?::timestamptz)
  AND (?::timestamptz IS NULL OR created_at < ?::timestamptz);
