-- name: account_deletion.insert_audit_record
INSERT INTO account_deletion_audit_records (id, user_id, event, status, detail, created_at)
VALUES (?, ?, ?, ?, ?, ?);
