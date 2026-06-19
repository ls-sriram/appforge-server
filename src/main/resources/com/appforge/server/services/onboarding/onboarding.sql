-- name: onboarding.initialize_state
INSERT INTO onboarding_state (user_id, version, current_step, completed, completed_at, updated_at)
VALUES (?, ?, 1, FALSE, NULL, ?)
ON CONFLICT (user_id) DO UPDATE
SET updated_at = EXCLUDED.updated_at;

-- name: onboarding.delete_answers_for_question
DELETE FROM onboarding_responses
WHERE user_id = ? AND question_id = ?;

-- name: onboarding.insert_answer
INSERT INTO onboarding_responses (user_id, question_id, option_id, text_value, answered_at)
VALUES (?, ?, ?, ?, ?)
;

-- name: onboarding.mark_completed
INSERT INTO onboarding_state (user_id, version, current_step, completed, completed_at, updated_at)
VALUES (?, ?, 1, TRUE, ?, ?)
ON CONFLICT (user_id) DO UPDATE
SET completed = TRUE,
    completed_at = EXCLUDED.completed_at,
    updated_at = EXCLUDED.updated_at,
    version = EXCLUDED.version;

-- name: onboarding.select_completed_state
SELECT completed
FROM onboarding_state
WHERE user_id = ?
LIMIT 1;

-- name: onboarding.select_active_questions
SELECT id, step_type, field_key, prompt, question_type, display_order
FROM onboarding_questions
WHERE is_active = TRUE
ORDER BY display_order ASC;

-- name: onboarding.select_active_options
SELECT id, question_id, label, display_order
FROM onboarding_question_options
WHERE is_active = TRUE
ORDER BY question_id ASC, display_order ASC;

-- name: onboarding.question_exists
SELECT 1
FROM onboarding_questions
WHERE id = ? AND is_active = TRUE
LIMIT 1;

-- name: onboarding.option_matches_question
SELECT 1
FROM onboarding_question_options
WHERE id = ? AND question_id = ? AND is_active = TRUE
LIMIT 1;
