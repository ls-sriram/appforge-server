#!/usr/bin/env bash
# ──────────────────────────────────────────────────────────────────────────
# Run AppForge Backend locally with Docker
# ──────────────────────────────────────────────────────────────────────────
# Prerequisites:
#   - Docker installed
#   - .secrets.conf in project root (or env vars set)
#
# Usage:
#   ./scripts/run-local.sh
#   SECRETS=/path/to/.secrets.conf ./scripts/run-local.sh
# ──────────────────────────────────────────────────────────────────────────

set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

cd "$PROJECT_DIR"
source "$SCRIPT_DIR/../deploy.env.sh"

# ─── Build image ─────────────────────────────────────────────────────────
echo "Building Docker image: $CONTAINER_IMAGE"
docker build -t "$CONTAINER_IMAGE" .

# ─── Secrets file ────────────────────────────────────────────────────────
SECRETS_FILE="${SECRETS:-$PROJECT_DIR/.secrets.conf}"
SECRETS_MOUNT=""
if [ -f "$SECRETS_FILE" ]; then
    SECRETS_MOUNT="-v ${SECRETS_FILE}:/app/.secrets.conf:ro"
    echo "Mounting secrets: $SECRETS_FILE"
else
    echo "WARNING: No .secrets.conf found. Set env vars manually or create $PROJECT_DIR/.secrets.conf"
fi

# ─── Run container ───────────────────────────────────────────────────────
echo "Starting $SERVICE_NAME on port $PORT ..."
docker run --rm \
    --name "$SERVICE_NAME" \
    -p "${PORT}:${PORT}" \
    -e PORT="$PORT" \
    -e HOST="$HOST" \
    -e NODE_ENV="$NODE_ENV" \
    -e CORS_ALLOWED_ORIGINS="$CORS_ALLOWED_ORIGINS" \
    -e TRIAL_DURATION_DAYS="$TRIAL_DURATION_DAYS" \
    -e EARLY_ACCESS_ENABLED="$EARLY_ACCESS_ENABLED" \
    -e COOKIE_SECURE="$COOKIE_SECURE" \
    -e SESSION_COOKIE_NAME="$SESSION_COOKIE_NAME" \
    -e SESSION_EXPIRY_DAYS="$SESSION_EXPIRY_DAYS" \
    -e FIREBASE_PROJECT_ID="${FIREBASE_PROJECT_ID:-appforge-dev}" \
    -e UPLOADS_BUCKET="${UPLOADS_BUCKET:-appforge-dev-uploads}" \
    -e UPLOAD_MAX_BYTES="${UPLOAD_MAX_BYTES:-10485760}" \
    -e INTERNAL_SECRET="${INTERNAL_SECRET:-dev-secret}" \
    -e DODO_PAYMENTS_BASE_URL="${DODO_PAYMENTS_BASE_URL:-https://test.dodopayments.com}" \
    $SECRETS_MOUNT \
    "$CONTAINER_IMAGE"
