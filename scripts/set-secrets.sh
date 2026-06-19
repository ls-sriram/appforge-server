#!/usr/bin/env bash
# ──────────────────────────────────────────────────────────────────────────
# Set Cloud Run secrets (run ONCE per environment)
# ──────────────────────────────────────────────────────────────────────────
# Prerequisites:
#   - ENV set to staging or dev
#   - gcloud authenticated
#   - Secret values available (see prompts below)
#
# Usage:
#   ENV=dev    ./scripts/set-secrets.sh
#   ENV=staging ./scripts/set-secrets.sh
#
# Secrets are stored in Secret Manager and mounted as env vars on Cloud Run.
# ──────────────────────────────────────────────────────────────────────────

set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

cd "$PROJECT_DIR"
source "$SCRIPT_DIR/../deploy.env.sh"

if [ "$ENV" = "local" ]; then
    echo "ERROR: Secrets are for staging or dev only. Use .secrets.conf for local."
    exit 1
fi

SERVICE_NAME="appforge-backend"
REGION="$GCP_REGION"

echo "Setting secrets for $ENV environment (project: $GCP_PROJECT)"
echo ""

# ─── Helper ──────────────────────────────────────────────────────────────
set_secret() {
    local name="$1"
    local prompt="$2"
    local env_var="${3:-$name}"

    # Check if secret already exists in Cloud Run service
    local existing
    existing=$(gcloud run services describe "$SERVICE_NAME" \
        --region="$REGION" \
        --format="value(spec.template.spec.containers[0].env[])" 2>/dev/null | grep "$name" || true)

    if [ -n "$existing" ]; then
        echo "  ✓ $name already set — skipping"
        return
    fi

    # Read from env var or prompt
    local value="${!name:-}"
    if [ -z "$value" ]; then
        echo -n "  Enter value for $env_var ($prompt): "
        read -s value
        echo ""
    fi

    if [ -z "$value" ]; then
        echo "  ⚠ $name empty — skipping"
        return
    fi

    # Create/update in Secret Manager
    local secret_id="${SERVICE_NAME}-${ENV}-${name}"
    echo -n "$value" | gcloud secrets create "$secret_id" \
        --replication-policy="automatic" \
        --data-file=- 2>/dev/null || {
        echo -n "$value" | gcloud secrets versions add "$secret_id" --data-file=-
    }

    # Attach to Cloud Run
    gcloud run services update "$SERVICE_NAME" \
        --region="$REGION" \
        --set-secrets "${env_var}=${secret_id}:latest"

    echo "  ✓ $name set"
}

# ─── Required secrets ────────────────────────────────────────────────────
echo "── Required ──"
set_secret "INTERNAL_SECRET" "Random string for internal API auth"
set_secret "FIREBASE_SERVICE_ACCOUNT_JSON" "Firebase service account JSON (or set FIREBASE_PROJECT_ID)" "FIREBASE_PROJECT_ID"
set_secret "DODO_PAYMENTS_API_KEY" "Dodo Payments API key"
set_secret "DODO_PAYMENTS_WEBHOOK_KEY" "Dodo webhook signing key"
set_secret "ZEPTOMAIL_SEND_TOKEN" "ZeptoMail API token (for emails)"

# ─── Optional secrets ────────────────────────────────────────────────────
echo ""
echo "── Optional ──"
set_secret "OPENAI_API_KEY" "OpenAI API key (for AI reviews)" "OPENAI_API_KEY"
set_secret "ZEPTOMAIL_API_URL" "ZeptoMail API URL (default: https://api.zeptomail.in/v1.1/email)" "ZEPTOMAIL_API_URL"
set_secret "EMAIL_FROM_ADDRESS" "Sender email for transactional emails" "EMAIL_FROM_ADDRESS"
set_secret "EMAIL_FROM_NAME" "Sender name for transactional emails" "EMAIL_FROM_NAME"

# ─── SQL (if using SQL primary) ──────────────────────────────────────────
if [ "${DB_PRIMARY:-sql}" = "sql" ]; then
    echo ""
    echo "── SQL Database ──"
    set_secret "DATABASE_SQL_URL" "jdbc:postgresql://host:5432/appforge"
    set_secret "DATABASE_SQL_USER" "Database username"
    set_secret "DATABASE_SQL_PASSWORD" "Database password"
fi

echo ""
echo "✅ Secrets configured for $ENV"
echo ""
echo "Verify: gcloud run services describe $SERVICE_NAME --region=$REGION --format=\"value(spec.template.spec.containers[0].env[])\""
