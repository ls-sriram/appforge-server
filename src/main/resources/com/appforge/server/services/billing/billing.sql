-- name: billing.upsert_entitlement
INSERT INTO billing_entitlements (
  user_id, plan, status, expires_at, started_at, features, entitlement_source,
  external_customer_id, external_reference_id, billing_type,
  last_payment_amount_cents, last_payment_currency, created_at, updated_at
)
VALUES (?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?, ?, ?)
ON CONFLICT (user_id) DO UPDATE SET
  plan = EXCLUDED.plan,
  status = EXCLUDED.status,
  expires_at = EXCLUDED.expires_at,
  started_at = EXCLUDED.started_at,
  features = EXCLUDED.features,
  entitlement_source = EXCLUDED.entitlement_source,
  external_customer_id = EXCLUDED.external_customer_id,
  external_reference_id = EXCLUDED.external_reference_id,
  billing_type = EXCLUDED.billing_type,
  last_payment_amount_cents = EXCLUDED.last_payment_amount_cents,
  last_payment_currency = EXCLUDED.last_payment_currency,
  updated_at = EXCLUDED.updated_at;

-- name: billing.select_entitlement_by_user
SELECT plan, status, expires_at, started_at, features, entitlement_source,
       external_customer_id, external_reference_id, billing_type,
       last_payment_amount_cents, last_payment_currency, created_at, updated_at
FROM billing_entitlements
WHERE user_id = ?;

-- name: billing.select_payment_by_id
SELECT date, amount_cents, currency, plan_id, email_sent_at
FROM billing_payments
WHERE id = ? AND user_id = ?;

-- name: billing.upsert_payment
INSERT INTO billing_payments (id, user_id, date, amount_cents, currency, plan_id, email_sent_at, created_at)
VALUES (?, ?, ?, ?, ?, ?, ?, NOW())
ON CONFLICT (id) DO UPDATE SET
  user_id = EXCLUDED.user_id,
  date = EXCLUDED.date,
  amount_cents = EXCLUDED.amount_cents,
  currency = EXCLUDED.currency,
  plan_id = EXCLUDED.plan_id,
  email_sent_at = EXCLUDED.email_sent_at;

-- name: billing.list_payments_by_user
SELECT date, amount_cents, currency, plan_id, email_sent_at
FROM billing_payments
WHERE user_id = ?
ORDER BY date DESC;

-- name: billing.upsert_audit_record
INSERT INTO billing_audit_records (id, payload, timestamp, webhook_id, audit_source)
VALUES (?, ?, ?, ?, ?)
ON CONFLICT (id) DO UPDATE SET
  payload = EXCLUDED.payload,
  timestamp = EXCLUDED.timestamp,
  webhook_id = EXCLUDED.webhook_id,
  audit_source = EXCLUDED.audit_source;

-- name: billing.insert_audit_record_once
INSERT INTO billing_audit_records (id, payload, timestamp, webhook_id, audit_source)
VALUES (?, ?, ?, ?, ?)
ON CONFLICT (id) DO NOTHING;
