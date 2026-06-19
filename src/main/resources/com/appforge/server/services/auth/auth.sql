-- name: user.select_uid_by_email_normalized
SELECT uid
FROM app_users
WHERE email_normalized = ?
LIMIT 1;

-- name: user.upsert_app_user
INSERT INTO app_users (uid, email, email_normalized, display_name, created_at, last_login_at)
VALUES (?, ?, ?, ?, ?, ?)
ON CONFLICT (uid) DO UPDATE SET
  email = EXCLUDED.email,
  email_normalized = EXCLUDED.email_normalized,
  display_name = EXCLUDED.display_name,
  last_login_at = EXCLUDED.last_login_at;

-- name: user.upsert_profile
INSERT INTO profiles (user_id, display_name, email, email_normalized, last_seen_at, created_at, updated_at)
VALUES (?, ?, ?, ?, ?, ?, ?)
ON CONFLICT (user_id) DO UPDATE SET
  display_name = EXCLUDED.display_name,
  email = EXCLUDED.email,
  email_normalized = EXCLUDED.email_normalized,
  last_seen_at = EXCLUDED.last_seen_at,
  updated_at = EXCLUDED.updated_at;

-- name: user.select_user_by_uid
SELECT uid, email, display_name, created_at, last_login_at
FROM app_users
WHERE uid = ?;

-- name: user.update_app_user_display_name
UPDATE app_users
SET display_name = ?, last_login_at = ?
WHERE uid = ?;

-- name: user.update_profile_display_name
UPDATE profiles
SET display_name = ?, updated_at = ?, last_seen_at = ?
WHERE user_id = ?;

-- name: user.delete_app_user
DELETE FROM app_users
WHERE uid = ?;

-- name: user.delete_profile
DELETE FROM profiles
WHERE user_id = ?;

-- name: early_access.select_status_by_email
SELECT status
FROM early_access_entries
WHERE email = ?;

-- name: early_access.exists_email
SELECT 1
FROM early_access_entries
WHERE email = ?;

-- name: early_access.insert_waitlist
INSERT INTO early_access_entries (email, status, created_at, updated_at, approved_at)
VALUES (?, 'waitlist', NOW(), NOW(), NULL);

-- name: early_access.upsert_approval
INSERT INTO early_access_entries (email, status, created_at, updated_at, approved_at)
VALUES (?, 'approved', NOW(), NOW(), ?)
ON CONFLICT (email)
DO UPDATE SET
  status = 'approved',
  updated_at = NOW(),
  approved_at = COALESCE(early_access_entries.approved_at, EXCLUDED.approved_at);
