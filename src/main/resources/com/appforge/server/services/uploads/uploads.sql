-- name: uploads.upsert_pending_upload
INSERT INTO upload_records (
  upload_id, uid, type, entity_id, bucket, object_name, content_type, size_bytes, status, created_at, expires_at
)
VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
ON CONFLICT (upload_id) DO UPDATE SET
  uid = EXCLUDED.uid,
  type = EXCLUDED.type,
  entity_id = EXCLUDED.entity_id,
  bucket = EXCLUDED.bucket,
  object_name = EXCLUDED.object_name,
  content_type = EXCLUDED.content_type,
  size_bytes = EXCLUDED.size_bytes,
  status = EXCLUDED.status,
  expires_at = EXCLUDED.expires_at;

-- name: uploads.select_upload_by_id
SELECT upload_id, uid, type, entity_id, bucket, object_name, content_type, size_bytes, status, created_at, expires_at
FROM upload_records
WHERE upload_id = ?;

-- name: uploads.select_upload_by_object_name
SELECT upload_id, uid, type, entity_id, bucket, object_name, content_type, size_bytes, status, created_at, expires_at
FROM upload_records
WHERE object_name = ?
ORDER BY created_at DESC, upload_id DESC
LIMIT 1;

-- name: uploads.mark_upload_completed
UPDATE upload_records
SET status = 'completed',
    size_bytes = ?,
    content_type = COALESCE(?, content_type)
WHERE upload_id = ?;

-- name: uploads.insert_processed_event_once
INSERT INTO upload_processed_events (event_id, bucket, object_name, generation, size_bytes, content_type, event_time_epoch_seconds, processed_at)
VALUES (?, ?, ?, ?, ?, ?, ?, NOW())
ON CONFLICT (event_id) DO NOTHING;
