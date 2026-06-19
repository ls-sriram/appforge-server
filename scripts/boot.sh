#!/usr/bin/env bash
# ──────────────────────────────────────────────────────────────────────────
# AppForge Backend — Local Boot Script
#
# Usage:
#   ./scripts/boot.sh            # full boot (db + server)
#   ./scripts/boot.sh --no-db    # skip Docker (db already running)
#   ./scripts/boot.sh --server-only  # just start the Ktor server
# ──────────────────────────────────────────────────────────────────────────

set -euo pipefail

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"

log()   { echo -e "${BLUE}[boot]${NC} $*"; }
ok()    { echo -e "${GREEN}  ✓${NC} $*"; }
warn()  { echo -e "${YELLOW}  ⚠${NC} $*"; }
fail()  { echo -e "${RED}  ✗${NC} $*"; }

# ─── Flags ────────────────────────────────────────────────────────────────
SKIP_DB=false
SERVER_ONLY=false

for arg in "$@"; do
  case "$arg" in
    --no-db)       SKIP_DB=true ;;
    --server-only) SERVER_ONLY=true; SKIP_DB=true ;;
    --help|-h)
      echo "Usage: ./scripts/boot.sh [OPTIONS]"
      echo ""
      echo "Options:"
      echo "  --no-db         Skip Docker PostgreSQL (use existing DB)"
      echo "  --server-only   Only start the Ktor server"
      echo "  --help, -h      Show this help"
      exit 0
      ;;
    *) fail "Unknown option: $arg"; exit 1 ;;
  esac
done

# ─── Health Check ─────────────────────────────────────────────────────────
wait_for_port() {
  local port=$1 name=$2 timeout=${3:-30}
  log "Waiting for $name on port $port..."
  local count=0
  while ! (echo > /dev/tcp/localhost/$port) 2>/dev/null; do
    sleep 1
    count=$((count + 1))
    if [ $count -ge $timeout ]; then
      fail "$name did not start within ${timeout}s"
      return 1
    fi
  done
  ok "$name is ready on port $port"
}

# ─── Step 1: PostgreSQL ──────────────────────────────────────────────────
if [ "$SKIP_DB" = false ]; then
  log "Starting PostgreSQL..."

  if docker ps --format '{{.Names}}' | grep -q '^appforge-db$' 2>/dev/null; then
    ok "PostgreSQL container 'appforge-db' is already running"
  else
    if docker rm -f appforge-db 2>/dev/null; then
      warn "Removed stale appforge-db container"
    fi

    docker run -d \
      --name appforge-db \
      -e POSTGRES_USER=appforge \
      -e POSTGRES_PASSWORD=appforge_dev_password \
      -e POSTGRES_DB=appforge \
      -p 5432:5432 \
      postgres:16 \
      > /dev/null 2>&1
    ok "PostgreSQL container started"
  fi

  wait_for_port 5432 "PostgreSQL" 30
fi

# ─── Step 2: Environment ──────────────────────────────────────────────────
log "Configuring environment..."

# Export all required vars for local dev
export PORT=8080
export HOST=0.0.0.0
export NODE_ENV=development
export APP_PUBLIC_URL=http://localhost:8080
export CORS_ALLOWED_ORIGINS='["http://localhost:8081","http://localhost:8082","http://localhost:3000","http://localhost:19006"]'
export INTERNAL_SECRET=dev-secret
export EARLY_ACCESS_ENABLED=false
export TRIAL_DURATION_DAYS=7
export COOKIE_SECURE=false
export SESSION_COOKIE_NAME=appforge-session
export SESSION_EXPIRY_DAYS=14

# Database
export DATABASE_PRIMARY=sql
export DATABASE_SQL_URL=jdbc:postgresql://localhost:5432/appforge
export DATABASE_SQL_USER=appforge
export DATABASE_SQL_PASSWORD=appforge_dev_password
export DATABASE_SQL_POOL_SIZE=5

# Firebase — local dev works without it (session cookie auth only)
export FIREBASE_PROJECT_ID=${FIREBASE_PROJECT_ID:-}

# Dodo (test mode)
export DODO_PAYMENTS_BASE_URL=${DODO_PAYMENTS_BASE_URL:-https://test.dodopayments.com}
export DODO_PAYMENTS_API_KEY=${DODO_PAYMENTS_API_KEY:-}
export DODO_WEBHOOK_KEY=${DODO_WEBHOOK_KEY:-}

# Uploads
export UPLOADS_BUCKET=${UPLOADS_BUCKET:-local-uploads}
export UPLOAD_MAX_BYTES=10485760

# OpenAI (optional)
export OPENAI_API_KEY=${OPENAI_API_KEY:-}
export OPENAI_MODEL=${OPENAI_MODEL:-gpt-4o-mini}

ok "Environment configured"

# ─── Step 3: Build ────────────────────────────────────────────────────────
log "Building backend..."

cd "$ROOT_DIR"

if [ "$SERVER_ONLY" = false ]; then
  ./gradlew classes --quiet 2>&1 || {
    fail "Build failed. Fix compilation errors and retry."
    exit 1
  }
  ok "Build successful"
fi

# ─── Step 4: Start Server ─────────────────────────────────────────────────
log "Starting Ktor server on :$PORT..."
echo ""
echo -e "${GREEN}─────────────────────────────────────────────────${NC}"
echo -e "${GREEN} AppForge Backend — Local Dev${NC}"
echo -e "${GREEN}  API:      http://localhost:$PORT${NC}"
echo -e "${GREEN}  DB:       PostgreSQL (appforge)${NC}"
echo -e "${GREEN}  Auth:     Session cookie / Firebase${NC}"
echo -e "${GREEN}─────────────────────────────────────────────────${NC}"
echo ""

./gradlew run 2>&1
