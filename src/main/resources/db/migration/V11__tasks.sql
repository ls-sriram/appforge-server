CREATE TABLE IF NOT EXISTS tasks (
  id TEXT PRIMARY KEY,
  owner_uid TEXT NOT NULL,
  type TEXT NOT NULL,
  title VARCHAR(200) NOT NULL,
  status TEXT NOT NULL DEFAULT 'open' CHECK (status IN ('open', 'completed', 'archived')),
  tag VARCHAR(50),
  description TEXT,
  priority TEXT CHECK (priority IN ('low', 'medium', 'high')),
  assignee TEXT,
  due_at TIMESTAMPTZ,
  completed_at TIMESTAMPTZ,
  metadata_json TEXT NOT NULL DEFAULT '{}',
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT tasks_completed_consistency CHECK (
    (status = 'completed' AND completed_at IS NOT NULL) OR
    (status <> 'completed' AND completed_at IS NULL)
  )
);

CREATE INDEX IF NOT EXISTS tasks_owner_uid_idx ON tasks(owner_uid);
CREATE INDEX IF NOT EXISTS tasks_status_idx ON tasks(status);
CREATE INDEX IF NOT EXISTS tasks_type_idx ON tasks(type);
CREATE INDEX IF NOT EXISTS tasks_tag_idx ON tasks(tag);
CREATE INDEX IF NOT EXISTS tasks_assignee_idx ON tasks(assignee);
CREATE INDEX IF NOT EXISTS tasks_due_at_idx ON tasks(due_at);
CREATE INDEX IF NOT EXISTS tasks_created_at_idx ON tasks(created_at DESC);
