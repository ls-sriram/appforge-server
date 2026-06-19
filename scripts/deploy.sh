#!/usr/bin/env bash
# ──────────────────────────────────────────────────────────────────────────
# Deploy AppForge Backend to Google Cloud Run
# ──────────────────────────────────────────────────────────────────────────
# Prerequisites:
#   - Image already pushed (run build-push.sh first)
#   - ENV set to staging or dev
#   - gcloud authenticated
#
# Usage:
#   ENV=dev    ./scripts/deploy.sh
#   ENV=staging ./scripts/deploy.sh
#
# Secrets (Dodo, OpenAI, etc.) are set as Cloud Run secrets.
# Run ./scripts/set-secrets.sh ONCE per environment to configure them.
# ──────────────────────────────────────────────────────────────────────────

set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

cd "$PROJECT_DIR"
source "$SCRIPT_DIR/../deploy.env.sh"

if [ "$ENV" = "local" ]; then
    echo "ERROR: deploy is for staging or dev only. Use run-local.sh for local."
    exit 1
fi

# ─── Verify image exists ─────────────────────────────────────────────────
if ! docker manifest inspect "$CONTAINER_IMAGE" >/dev/null 2>&1; then
    echo "ERROR: Image not found: $CONTAINER_IMAGE"
    echo "Run: ENV=$ENV ./scripts/build-push.sh first"
    exit 1
fi

# ─── Set project ─────────────────────────────────────────────────────────
echo "Using GCP project: $GCP_PROJECT"
gcloud config set project "$GCP_PROJECT"

# ─── Deploy to Cloud Run ─────────────────────────────────────────────────
echo "Deploying $SERVICE_NAME to Cloud Run ..."

gcloud run deploy "$SERVICE_NAME" \
    --image="$CONTAINER_IMAGE" \
    --region="$GCP_REGION" \
    --platform=managed \
    --memory="$CLOUD_RUN_MEMORY" \
    --cpu="$CLOUD_RUN_CPU" \
    --min-instances="$CLOUD_RUN_MIN_INSTANCES" \
    --max-instances="$CLOUD_RUN_MAX_INSTANCES" \
    --timeout="$CLOUD_RUN_TIMEOUT" \
    --port=8080 \
    --allow-unauthenticated \
    --set-env-vars="APP_ENV=$ENV" \
    --set-env-vars="PORT=$PORT" \
    --set-env-vars="CORS_ALLOWED_ORIGINS=$CORS_ALLOWED_ORIGINS" \
    --set-env-vars="TRIAL_DURATION_DAYS=$TRIAL_DURATION_DAYS" \
    --set-env-vars="EARLY_ACCESS_ENABLED=$EARLY_ACCESS_ENABLED" \
    --set-env-vars="NODE_ENV=$NODE_ENV" \
    --set-env-vars="COOKIE_SECURE=$COOKIE_SECURE" \
    --set-env-vars="SESSION_COOKIE_NAME=$SESSION_COOKIE_NAME" \
    --set-env-vars="SESSION_EXPIRY_DAYS=$SESSION_EXPIRY_DAYS" \
    --set-env-vars="FIREBASE_PROJECT_ID=${FIREBASE_PROJECT_ID:-$GCP_PROJECT}" \
    --set-env-vars="UPLOADS_BUCKET=${UPLOADS_BUCKET:-${GCP_PROJECT}-uploads}" \
    --set-env-vars="UPLOAD_MAX_BYTES=${UPLOAD_MAX_BYTES:-10485760}" \
    --set-env-vars="DODO_PAYMENTS_BASE_URL=${DODO_PAYMENTS_BASE_URL:-https://test.dodopayments.com}" \
    --no-cpu-throttling

# ─── Get URL ─────────────────────────────────────────────────────────────
SERVICE_URL=$(gcloud run services describe "$SERVICE_NAME" \
    --region="$GCP_REGION" \
    --format="value(status.url)")

echo ""
echo "✅ Deployed: $SERVICE_NAME"
echo "   URL: $SERVICE_URL"
echo "   Health: $SERVICE_URL/health"
echo ""
echo "Next steps:"
echo "  1. Set secrets:     ENV=$ENV ./scripts/set-secrets.sh"
echo "  2. Test health:     curl $SERVICE_URL/health"
echo "  3. Configure DNS:   Point your domain to $SERVICE_URL"
