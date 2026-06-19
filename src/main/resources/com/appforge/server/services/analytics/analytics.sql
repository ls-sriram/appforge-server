-- name: analytics.insert_api_call
INSERT INTO platform_api_calls (id, app_id, request_id, timestamp, method, path, raw_path, status, duration_ms, user_id, team_id, user_agent, error)
VALUES (?, ?, ?, to_timestamp(? / 1000.0), ?, ?, ?, ?, ?, ?, ?, ?, ?);

-- name: analytics.select_api_calls_since
SELECT request_id, EXTRACT(EPOCH FROM timestamp) AS ts, method, path, raw_path, status, duration_ms, user_id, app_id, team_id, user_agent, error
FROM platform_api_calls
WHERE EXTRACT(EPOCH FROM timestamp) >= ?
ORDER BY timestamp ASC;
