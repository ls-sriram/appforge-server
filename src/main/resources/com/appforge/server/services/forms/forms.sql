-- name: forms.select_active_form_by_kind_entity
SELECT id, version, form_kind, entity_type, name
FROM forms
WHERE form_kind = ?
  AND entity_type = ?
  AND status = 'active'
ORDER BY version DESC
LIMIT 1;

-- name: forms.select_fields_by_form_id
SELECT id, label, field_type, required, display_order
FROM form_fields
WHERE form_id = ?
ORDER BY display_order ASC, id ASC;

-- name: forms.select_options_by_field_id
SELECT id, label, display_order
FROM form_field_options
WHERE form_id = ?
  AND form_field_id = ?
ORDER BY display_order ASC, id ASC;
