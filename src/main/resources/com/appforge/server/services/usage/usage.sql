-- name: usage.count_entities_buckets
SELECT bucket_start, SUM(count_value)::bigint AS count_value
FROM (
  SELECT date_trunc(?::text, created_at) AS bucket_start, COUNT(*)::bigint AS count_value
  FROM recordings
  WHERE uid = ?
    AND (?::timestamptz IS NULL OR created_at >= ?::timestamptz)
    AND (?::timestamptz IS NULL OR created_at < ?::timestamptz)
  GROUP BY 1
  UNION ALL
  SELECT date_trunc(?::text, created_at) AS bucket_start, COUNT(*)::bigint AS count_value
  FROM documents
  WHERE owner_uid = ?
    AND (?::timestamptz IS NULL OR created_at >= ?::timestamptz)
    AND (?::timestamptz IS NULL OR created_at < ?::timestamptz)
  GROUP BY 1
) counts
GROUP BY bucket_start
ORDER BY 1 ASC;

-- name: usage.count_entities_total
SELECT COALESCE(SUM(count_value), 0)::bigint AS count_value
FROM (
  SELECT COUNT(*)::bigint AS count_value
  FROM recordings
  WHERE uid = ?
    AND (?::timestamptz IS NULL OR created_at >= ?::timestamptz)
    AND (?::timestamptz IS NULL OR created_at < ?::timestamptz)
  UNION ALL
  SELECT COUNT(*)::bigint AS count_value
  FROM documents
  WHERE owner_uid = ?
    AND (?::timestamptz IS NULL OR created_at >= ?::timestamptz)
    AND (?::timestamptz IS NULL OR created_at < ?::timestamptz)
) counts;

-- name: usage.count_audios_buckets
SELECT date_trunc(?::text, created_at) AS bucket_start, COUNT(*)::bigint AS count_value
FROM upload_records
WHERE uid = ?
  AND type = 'audio'
  AND (?::timestamptz IS NULL OR created_at >= ?::timestamptz)
  AND (?::timestamptz IS NULL OR created_at < ?::timestamptz)
GROUP BY 1
ORDER BY 1 ASC;

-- name: usage.count_audios_total
SELECT COUNT(*)::bigint AS count_value
FROM upload_records
WHERE uid = ?
  AND type = 'audio'
  AND (?::timestamptz IS NULL OR created_at >= ?::timestamptz)
  AND (?::timestamptz IS NULL OR created_at < ?::timestamptz);

-- name: usage.count_reviews_buckets
SELECT date_trunc(?::text, created_at) AS bucket_start, COUNT(*)::bigint AS count_value
FROM reviews
WHERE owner_uid = ?
  AND (?::timestamptz IS NULL OR created_at >= ?::timestamptz)
  AND (?::timestamptz IS NULL OR created_at < ?::timestamptz)
GROUP BY 1
ORDER BY 1 ASC;

-- name: usage.count_reviews_total
SELECT COUNT(*)::bigint AS count_value
FROM reviews
WHERE owner_uid = ?
  AND (?::timestamptz IS NULL OR created_at >= ?::timestamptz)
  AND (?::timestamptz IS NULL OR created_at < ?::timestamptz);

-- name: usage.count_shares_buckets
SELECT date_trunc(?::text, created_at) AS bucket_start, COUNT(*)::bigint AS count_value
FROM shares
WHERE owner_uid = ?
  AND (?::timestamptz IS NULL OR created_at >= ?::timestamptz)
  AND (?::timestamptz IS NULL OR created_at < ?::timestamptz)
GROUP BY 1
ORDER BY 1 ASC;

-- name: usage.count_shares_total
SELECT COUNT(*)::bigint AS count_value
FROM shares
WHERE owner_uid = ?
  AND (?::timestamptz IS NULL OR created_at >= ?::timestamptz)
  AND (?::timestamptz IS NULL OR created_at < ?::timestamptz);
