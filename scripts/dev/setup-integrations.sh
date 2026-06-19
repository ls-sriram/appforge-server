#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
DEFAULT_APP_ID="$(node "$PROJECT_ROOT/scripts/app-registry.mjs" --default-app-id)"
APP_ID="${APP_ID:-$DEFAULT_APP_ID}"
CONFIG_PROJECT_ID="${CONFIG_PROJECT_ID:-$APP_ID}"
CONFIG_ENVIRONMENT="${CONFIG_ENVIRONMENT:-dev}"
CONFIG_SCOPE="${CONFIG_SCOPE:-backend}"
CONFIG_DB="${CONFIG_DB:-$(node "$PROJECT_ROOT/tools/config-manager/db-path.mjs" "$CONFIG_PROJECT_ID")}"

usage() {
  cat <<'EOF'
Usage:
  ./scripts/dev/setup-integrations.sh \
    --firebase-json /tmp/firebase-sa.json \
    --dodo-api-key <key> \
    --dodo-webhook-key <key> \
    --dodo-product-monthly <id> \
    --dodo-product-annual <id> \
    [--dodo-base-url https://test.dodopayments.com]
EOF
}

FIREBASE_JSON=""
DODO_API_KEY=""
DODO_WEBHOOK_KEY=""
DODO_PRODUCT_MONTHLY=""
DODO_PRODUCT_ANNUAL=""
DODO_BASE_URL="https://test.dodopayments.com"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --firebase-json) FIREBASE_JSON="${2:-}"; shift 2 ;;
    --dodo-api-key) DODO_API_KEY="${2:-}"; shift 2 ;;
    --dodo-webhook-key) DODO_WEBHOOK_KEY="${2:-}"; shift 2 ;;
    --dodo-product-monthly) DODO_PRODUCT_MONTHLY="${2:-}"; shift 2 ;;
    --dodo-product-annual) DODO_PRODUCT_ANNUAL="${2:-}"; shift 2 ;;
    --dodo-base-url) DODO_BASE_URL="${2:-}"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) echo "[setup] unknown arg: $1"; usage; exit 1 ;;
  esac
done

if [[ -z "$FIREBASE_JSON" || -z "$DODO_API_KEY" || -z "$DODO_WEBHOOK_KEY" || -z "$DODO_PRODUCT_MONTHLY" || -z "$DODO_PRODUCT_ANNUAL" ]]; then
  usage
  exit 1
fi

if [[ ! -f "$FIREBASE_JSON" ]]; then
  echo "[setup] firebase json not found: $FIREBASE_JSON"
  exit 1
fi

FIREBASE_PROJECT_ID="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["project_id"])' "$FIREBASE_JSON")"
FIREBASE_SERVICE_ACCOUNT_JSON="$(python3 -c 'import json,sys; print(json.dumps(json.load(open(sys.argv[1]))))' "$FIREBASE_JSON")"

CONFIG_DB="$CONFIG_DB" npm run config:set -- "$CONFIG_PROJECT_ID" "$CONFIG_ENVIRONMENT" "$CONFIG_SCOPE" firebase setting FIREBASE_PROJECT_ID "$FIREBASE_PROJECT_ID"
CONFIG_DB="$CONFIG_DB" npm run config:set -- "$CONFIG_PROJECT_ID" "$CONFIG_ENVIRONMENT" "$CONFIG_SCOPE" firebase secret FIREBASE_SERVICE_ACCOUNT_JSON "$FIREBASE_SERVICE_ACCOUNT_JSON"
CONFIG_DB="$CONFIG_DB" npm run config:set -- "$CONFIG_PROJECT_ID" "$CONFIG_ENVIRONMENT" "$CONFIG_SCOPE" firebase setting FIREBASE_ENABLED "true"
CONFIG_DB="$CONFIG_DB" npm run config:set -- "$CONFIG_PROJECT_ID" "$CONFIG_ENVIRONMENT" "$CONFIG_SCOPE" dodo secret DODO_PAYMENTS_API_KEY "$DODO_API_KEY"
CONFIG_DB="$CONFIG_DB" npm run config:set -- "$CONFIG_PROJECT_ID" "$CONFIG_ENVIRONMENT" "$CONFIG_SCOPE" dodo secret DODO_PAYMENTS_WEBHOOK_KEY "$DODO_WEBHOOK_KEY"
CONFIG_DB="$CONFIG_DB" npm run config:set -- "$CONFIG_PROJECT_ID" "$CONFIG_ENVIRONMENT" "$CONFIG_SCOPE" dodo setting DODO_PAYMENTS_BASE_URL "$DODO_BASE_URL"
CONFIG_DB="$CONFIG_DB" npm run config:set -- "$CONFIG_PROJECT_ID" "$CONFIG_ENVIRONMENT" "$CONFIG_SCOPE" dodo setting DODO_PRODUCT_ID_PRO_MONTHLY "$DODO_PRODUCT_MONTHLY"
CONFIG_DB="$CONFIG_DB" npm run config:set -- "$CONFIG_PROJECT_ID" "$CONFIG_ENVIRONMENT" "$CONFIG_SCOPE" dodo setting DODO_PRODUCT_ID_PRO_ANNUAL "$DODO_PRODUCT_ANNUAL"

echo "[setup] updated config table: $CONFIG_DB"
echo "[setup] FIREBASE_PROJECT_ID=$FIREBASE_PROJECT_ID"
echo "[setup] done"
