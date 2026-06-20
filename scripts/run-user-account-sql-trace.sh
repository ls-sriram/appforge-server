#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
cd "$PROJECT_DIR"

TRACE_FILE="${USER_ACCOUNT_SQL_TRACE_FILE:-$PROJECT_DIR/build/reports/integration-sql/user-account-crud-trace.txt}"
mkdir -p "$(dirname "$TRACE_FILE")"

CONTAINER_NAME="appforge-it-postgres"
STARTED_CONTAINER=0

if [[ -z "${INTEGRATION_DB_URL:-}" || -z "${INTEGRATION_DB_USER:-}" || -z "${INTEGRATION_DB_PASSWORD:-}" ]]; then
  if ! docker ps --format '{{.Names}}' | grep -q "^${CONTAINER_NAME}$"; then
    docker rm -f "$CONTAINER_NAME" >/dev/null 2>&1 || true
    docker run --rm -d \
      --name "$CONTAINER_NAME" \
      -e POSTGRES_DB=appforge_it \
      -e POSTGRES_USER=appforge \
      -e POSTGRES_PASSWORD=appforge \
      -p 55432:5432 \
      postgres:16-alpine >/dev/null
    STARTED_CONTAINER=1
  fi

  for _ in $(seq 1 30); do
    if docker exec "$CONTAINER_NAME" pg_isready -U appforge -d appforge_it >/dev/null 2>&1; then
      break
    fi
    sleep 1
  done

  export INTEGRATION_DB_URL="jdbc:postgresql://localhost:55432/appforge_it"
  export INTEGRATION_DB_USER="appforge"
  export INTEGRATION_DB_PASSWORD="appforge"
fi

cleanup() {
  if [[ "$STARTED_CONTAINER" -eq 1 ]]; then
    docker stop "$CONTAINER_NAME" >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT

export USER_ACCOUNT_SQL_TRACE_FILE="$TRACE_FILE"

./gradlew integrationTest --tests "com.appforge.server.integration.UserAccountSqlTraceIntegrationTest"

printf '%s\n' "$TRACE_FILE"
