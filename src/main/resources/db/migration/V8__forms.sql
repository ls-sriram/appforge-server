CREATE TABLE IF NOT EXISTS forms (
    id TEXT PRIMARY KEY,
    version INTEGER NOT NULL,
    form_kind TEXT NOT NULL,
    entity_type TEXT NULL,
    name TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'active',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_forms_kind_entity_version
    ON forms(form_kind, COALESCE(entity_type, ''), version);

CREATE INDEX IF NOT EXISTS idx_forms_kind_entity_status
    ON forms(form_kind, entity_type, status, version DESC);

CREATE TABLE IF NOT EXISTS form_fields (
    id TEXT NOT NULL,
    form_id TEXT NOT NULL REFERENCES forms(id) ON DELETE CASCADE,
    label TEXT NOT NULL,
    field_type TEXT NOT NULL,
    required BOOLEAN NOT NULL DEFAULT FALSE,
    display_order INTEGER NOT NULL,
    PRIMARY KEY (form_id, id)
);

CREATE INDEX IF NOT EXISTS idx_form_fields_form_order
    ON form_fields(form_id, display_order);

CREATE TABLE IF NOT EXISTS form_field_options (
    id TEXT NOT NULL,
    form_id TEXT NOT NULL,
    form_field_id TEXT NOT NULL,
    label TEXT NOT NULL,
    display_order INTEGER NOT NULL,
    PRIMARY KEY (form_id, form_field_id, id),
    FOREIGN KEY (form_id, form_field_id) REFERENCES form_fields(form_id, id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_form_field_options_field_order
    ON form_field_options(form_id, form_field_id, display_order);

INSERT INTO forms (id, version, form_kind, entity_type, name, status)
VALUES ('default_recording_review_form_v1', 1, 'review', 'recording', 'Default Recording Review Form', 'active')
ON CONFLICT (id) DO NOTHING;

INSERT INTO form_fields (id, form_id, label, field_type, required, display_order)
VALUES
    ('overall_rating', 'default_recording_review_form_v1', 'Overall rating', 'single_select', TRUE, 10),
    ('clarity', 'default_recording_review_form_v1', 'Clarity', 'single_select', TRUE, 20),
    ('comments', 'default_recording_review_form_v1', 'Comments', 'text', FALSE, 30),
    ('recommendation', 'default_recording_review_form_v1', 'Would you recommend this recording?', 'single_select', TRUE, 40)
ON CONFLICT (form_id, id) DO NOTHING;

INSERT INTO form_field_options (id, form_id, form_field_id, label, display_order)
VALUES
    ('1', 'default_recording_review_form_v1', 'overall_rating', '1', 10),
    ('2', 'default_recording_review_form_v1', 'overall_rating', '2', 20),
    ('3', 'default_recording_review_form_v1', 'overall_rating', '3', 30),
    ('4', 'default_recording_review_form_v1', 'overall_rating', '4', 40),
    ('5', 'default_recording_review_form_v1', 'overall_rating', '5', 50),
    ('poor', 'default_recording_review_form_v1', 'clarity', 'Poor', 10),
    ('fair', 'default_recording_review_form_v1', 'clarity', 'Fair', 20),
    ('good', 'default_recording_review_form_v1', 'clarity', 'Good', 30),
    ('great', 'default_recording_review_form_v1', 'clarity', 'Great', 40),
    ('yes', 'default_recording_review_form_v1', 'recommendation', 'Yes', 10),
    ('no', 'default_recording_review_form_v1', 'recommendation', 'No', 20)
ON CONFLICT (form_id, form_field_id, id) DO NOTHING;
