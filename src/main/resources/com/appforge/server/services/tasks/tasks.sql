-- name: tasks.insert
INSERT INTO tasks (
  id, owner_uid, type, title, status, tag, description, priority, assignee, due_at, completed_at, metadata_json, created_at, updated_at
)
VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW());

-- name: tasks.select_by_id_and_owner
SELECT id, owner_uid, type, title, status, tag, description, priority, assignee, due_at, completed_at, metadata_json, created_at, updated_at
FROM tasks
WHERE id = ? AND owner_uid = ?
LIMIT 1;

-- name: tasks.list_by_owner
SELECT id, owner_uid, type, title, status, tag, description, priority, assignee, due_at, completed_at, metadata_json, created_at, updated_at
FROM tasks
WHERE owner_uid = ?
  AND (?::text IS NULL OR status = ?::text)
  AND (?::text IS NULL OR type = ?::text)
  AND (?::text IS NULL OR tag = ?::text)
ORDER BY created_at DESC, id DESC
LIMIT ?;

-- name: tasks.update_fields
UPDATE tasks
SET
  type = ?,
  title = ?,
  status = ?,
  tag = ?,
  description = ?,
  priority = ?,
  assignee = ?,
  due_at = ?,
  completed_at = ?,
  metadata_json = ?,
  updated_at = NOW()
WHERE id = ? AND owner_uid = ?;

-- name: tasks.delete_by_id_and_owner
DELETE FROM tasks
WHERE id = ? AND owner_uid = ?;
