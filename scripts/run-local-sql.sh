#!/usr/bin/env bash
# ──────────────────────────────────────────────────────────────────────────
# Start local PostgreSQL + AppForge Backend for local development
# ──────────────────────────────────────────────────────────────────────────
# Usage:
#   ./scripts/run-local-sql.sh          # Start everything
#   ./scripts/run-local-sql.sh --db-only # Start only PostgreSQL
#   ./scripts/run-local-sql.sh --app-only # Start only the app (assumes DB running)
# ──────────────────────────────────────────────────────────────────────────

set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
cd "$PROJECT_DIR"

DEFAULT_APP_ID="$(node "$PROJECT_DIR/../scripts/app-registry.mjs" --default-app-id)"
APP_ID="${APP_ID:-${CONFIG_PROJECT_ID:-$DEFAULT_APP_ID}}"
normalize_app_id() {
    printf '%s' "$1" | tr '[:upper:]' '[:lower:]' | tr -cs 'a-z0-9' '_'
}

APP_SLUG="$(normalize_app_id "$APP_ID")"
APP_DB_NAME="appforge_${APP_SLUG}"
APP_BACKEND_CONTAINER_NAME="appforge-${APP_SLUG}-backend"

CONFIG_PROJECT_ID="${CONFIG_PROJECT_ID:-$APP_ID}"
CONFIG_ENVIRONMENT="${CONFIG_ENVIRONMENT:-dev}"
CONFIG_SCOPE="${CONFIG_SCOPE:-backend}"
CONFIG_DB="$(CONFIG_DB= node "$PROJECT_DIR/../tools/config-manager/db-path.mjs" "$CONFIG_PROJECT_ID")"
CONFIG_EXPORT_SCRIPT="$PROJECT_DIR/../tools/config-manager/export-env.mjs"

if [[ -f "$CONFIG_DB" && -f "$CONFIG_EXPORT_SCRIPT" ]]; then
    echo "Loading docker env from config table (project=$CONFIG_PROJECT_ID env=$CONFIG_ENVIRONMENT scope=$CONFIG_SCOPE)"
    # shellcheck disable=SC2046
    eval "$(
        CONFIG_DB="$CONFIG_DB" \
            node "$CONFIG_EXPORT_SCRIPT" "$CONFIG_PROJECT_ID" "$CONFIG_ENVIRONMENT" "$CONFIG_SCOPE"
    )"
else
    echo "Config DB/export script not found; using built-in defaults fallback."
fi

# Apply the shared local Postgres topology explicitly.
# The container, port, user, password, and bootstrap DB are shared across apps;
# only the app database name varies.
POSTGRES_HOST="localhost"
POSTGRES_PORT="5432"
POSTGRES_DB="$APP_DB_NAME"
POSTGRES_USER="appforge"
POSTGRES_PASSWORD="appforge_dev_password"
: "${POSTGRES_POOL_SIZE:=5}"
: "${PORT:=8080}"
: "${HOST:=0.0.0.0}"
: "${NODE_ENV:=development}"
: "${INTERNAL_SECRET:=${APP_SLUG}_dev_secret}"
: "${UPLOAD_EVENT_SHARED_SECRET:=${APP_SLUG}_upload_secret}"
: "${EARLY_ACCESS_ENABLED:=true}"
: "${CORS_ALLOWED_ORIGINS:='[\"http://localhost:3000\",\"http://localhost:3001\"]'}"
POSTGRES_CONTAINER_NAME="appforge-postgres"
BACKEND_CONTAINER_NAME="$APP_BACKEND_CONTAINER_NAME"
POSTGRES_VOLUME_NAME="appforge-postgres-data"
POSTGRES_BOOT_DB="appforge"
: "${FIREBASE_PROJECT_ID:=${APP_ID}-dev}"
: "${FIREBASE_ENABLED:=false}"
: "${UPLOADS_BUCKET:=${APP_SLUG}-dev-uploads}"
: "${DODO_PAYMENTS_BASE_URL:=https://test.dodopayments.com}"
: "${DODO_PAYMENTS_ENABLED:=false}"
: "${OPENAI_ENABLED:=false}"
: "${EMAIL_ENABLED:=false}"

export POSTGRES_HOST POSTGRES_PORT POSTGRES_DB POSTGRES_USER POSTGRES_PASSWORD POSTGRES_POOL_SIZE
export PORT HOST NODE_ENV INTERNAL_SECRET UPLOAD_EVENT_SHARED_SECRET
export EARLY_ACCESS_ENABLED CORS_ALLOWED_ORIGINS POSTGRES_CONTAINER_NAME BACKEND_CONTAINER_NAME POSTGRES_VOLUME_NAME
export FIREBASE_PROJECT_ID FIREBASE_ENABLED UPLOADS_BUCKET DODO_PAYMENTS_BASE_URL DODO_PAYMENTS_ENABLED OPENAI_ENABLED EMAIL_ENABLED POSTGRES_BOOT_DB
export APP_ID CONFIG_PROJECT_ID CONFIG_ENVIRONMENT CONFIG_SCOPE CONFIG_DB

POSTGRES_CONTAINER_NAME="${POSTGRES_CONTAINER_NAME:-}"
BACKEND_CONTAINER_NAME="${BACKEND_CONTAINER_NAME:-}"

if [[ -z "$POSTGRES_CONTAINER_NAME" || -z "$BACKEND_CONTAINER_NAME" ]]; then
    echo "ERROR: BACKEND_CONTAINER_NAME and POSTGRES_CONTAINER_NAME must be set in env."
    exit 1
fi

assert_not_running() {
    local name="$1"
    if docker ps --format '{{.Names}}' | grep -q "^${name}$"; then
        echo "ERROR: container '${name}' is already running."
        echo "Refusing to reuse an existing container. Stop/remove it first, or change container name in env."
        exit 1
    fi
}

start_db() {
    if docker ps --format '{{.Names}}' | grep -q "^${POSTGRES_CONTAINER_NAME}$"; then
        echo "PostgreSQL container '${POSTGRES_CONTAINER_NAME}' is already running"
    else
        echo "Starting PostgreSQL..."
        docker compose up -d postgres
    fi
    echo "Waiting for PostgreSQL to be ready..."
    local ready=0
    for i in $(seq 1 15); do
        if docker exec "$POSTGRES_CONTAINER_NAME" pg_isready -U "$POSTGRES_USER" -d "$POSTGRES_BOOT_DB" > /dev/null 2>&1; then
            echo "PostgreSQL is ready"
            ready=1
            break
        fi
        sleep 2
    done
    if [[ "$ready" -ne 1 ]]; then
        echo "ERROR: PostgreSQL did not become ready in time"
        exit 1
    fi

    if ! docker exec "$POSTGRES_CONTAINER_NAME" psql -U "$POSTGRES_USER" -d "$POSTGRES_BOOT_DB" -tAc \
      "SELECT 1 FROM pg_database WHERE datname = '$POSTGRES_DB'" | grep -q 1; then
        echo "Creating app database $POSTGRES_DB..."
        docker exec "$POSTGRES_CONTAINER_NAME" createdb -U "$POSTGRES_USER" "$POSTGRES_DB"
    else
        echo "App database $POSTGRES_DB already exists"
    fi
}

start_app() {
    assert_not_running "$BACKEND_CONTAINER_NAME"
    echo "Building and starting AppForge Backend..."
    docker compose up --build app
}

case "${1:-}" in
    --db-only)
        start_db
        ;;
    --app-only)
        start_app
        ;;
    *)
        start_db
        start_app
        ;;
esac
