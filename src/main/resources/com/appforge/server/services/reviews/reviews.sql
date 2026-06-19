-- name: reviews.upsert_profile
INSERT INTO profiles (user_id, display_name, email, email_normalized, last_seen_at, created_at, updated_at)
VALUES (?, ?, ?, ?, ?, NOW(), NOW())
ON CONFLICT (user_id) DO UPDATE SET
  display_name = EXCLUDED.display_name,
  email = EXCLUDED.email,
  email_normalized = EXCLUDED.email_normalized,
  last_seen_at = EXCLUDED.last_seen_at,
  updated_at = NOW();

-- name: reviews.select_profile_by_id
SELECT user_id, display_name, email, email_normalized, last_seen_at
FROM profiles
WHERE user_id = ?;

-- name: reviews.select_profile_by_email
SELECT user_id, display_name, email, email_normalized, last_seen_at
FROM profiles
WHERE email_normalized = ?
LIMIT 1;

-- name: reviews.insert_review
INSERT INTO reviews (
  id, owner_uid, entity_id, entity_category, entity_type, author_role, author_id, author_name, author_email, content, created_at
)
VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?);

-- name: reviews.select_reviews_for_entity
SELECT id, entity_id, entity_category, author_role, author_id, author_name, author_email, content, created_at
FROM reviews
WHERE owner_uid = ? AND entity_id = ? AND entity_type = ?
ORDER BY created_at ASC;

-- name: reviews.select_reviews_for_owner
SELECT id, entity_id, entity_category, author_role, author_id, author_name, author_email, content, created_at
FROM reviews
WHERE owner_uid = ?
ORDER BY created_at ASC;
