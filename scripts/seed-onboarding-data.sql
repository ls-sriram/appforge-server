-- Seed global onboarding questions/options (idempotent)

BEGIN;

INSERT INTO onboarding_questions (id, step_type, field_key, prompt, question_type, display_order, is_active, updated_at)
VALUES
  ('q_goal', 'personalization', 'goal', 'What are you trying to improve?', 'single_select', 10, TRUE, NOW()),
  ('q_timeline', 'personalization', 'timeline', 'When do you want results?', 'single_select', 20, TRUE, NOW())
ON CONFLICT (id) DO UPDATE SET
  step_type = EXCLUDED.step_type,
  field_key = EXCLUDED.field_key,
  prompt = EXCLUDED.prompt,
  question_type = EXCLUDED.question_type,
  display_order = EXCLUDED.display_order,
  is_active = EXCLUDED.is_active,
  updated_at = NOW();

INSERT INTO onboarding_question_options (id, question_id, value_key, label, display_order, is_active, updated_at)
VALUES
  ('q_goal_speed', 'q_goal', 'speed', 'Move faster', 10, TRUE, NOW()),
  ('q_goal_clarity', 'q_goal', 'clarity', 'Get more clarity', 20, TRUE, NOW()),
  ('q_goal_consistency', 'q_goal', 'consistency', 'Stay consistent', 30, TRUE, NOW()),
  ('q_goal_revenue', 'q_goal', 'revenue', 'Increase revenue', 40, TRUE, NOW()),
  ('q_timeline_week', 'q_timeline', 'week', 'This week', 10, TRUE, NOW()),
  ('q_timeline_month', 'q_timeline', 'month', 'This month', 20, TRUE, NOW()),
  ('q_timeline_quarter', 'q_timeline', 'quarter', 'This quarter', 30, TRUE, NOW())
ON CONFLICT (id) DO UPDATE SET
  question_id = EXCLUDED.question_id,
  value_key = EXCLUDED.value_key,
  label = EXCLUDED.label,
  display_order = EXCLUDED.display_order,
  is_active = EXCLUDED.is_active,
  updated_at = NOW();

COMMIT;
