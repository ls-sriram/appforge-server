#!/usr/bin/env bash
# ──────────────────────────────────────────────────────────────────────────
# AppForge Backend — Environment Configuration
# ──────────────────────────────────────────────────────────────────────────
# Source this file to load environment-specific variables.
# Usage:
#   source deploy.env.sh          # Defaults to local
#   ENV=local  source deploy.env.sh
#   ENV=staging source deploy.env.sh
#   ENV=dev    source deploy.env.sh
# ──────────────────────────────────────────────────────────────────────────

set -euo pipefail

ENV="${ENV:-local}"

# ─── Common ──────────────────────────────────────────────────────────────
export APP_NAME="appforge-backend"
export APP_VERSION="${APP_VERSION:-0.1.0}"

case "$ENV" in
    local)
        export GCP_PROJECT=""
        export GCP_REGION=""
        export GCP_ZONE=""
        export SERVICE_NAME="appforge-local"
        export CONTAINER_IMAGE="$APP_NAME:$APP_VERSION"
        export DB_PRIMARY="sql"
        export PORT=8080
        export HOST="0.0.0.0"
        export CORS_ALLOWED_ORIGINS='["http://localhost:3000","http://localhost:3001","http://localhost:8081","http://localhost:8082"]'
        export TRIAL_DURATION_DAYS=7
        export EARLY_ACCESS_ENABLED=false
        export NODE_ENV="development"
        export COOKIE_SECURE=false
        export SESSION_COOKIE_NAME="appforge-session"
        export SESSION_EXPIRY_DAYS=14
        echo "[local] Environment loaded — run ./scripts/boot.sh to start"
        ;;

    staging)
        export GCP_PROJECT="${GCP_PROJECT:-appforge-staging}"       # ← change to your staging project
        export GCP_REGION="us-central1"
        export GCP_ZONE="us-central1-a"
        export SERVICE_NAME="appforge-backend"
        export AR_REPO="${GCP_REGION}-docker.pkg.dev/${GCP_PROJECT}/appforge"
        export CONTAINER_IMAGE="${AR_REPO}/${APP_NAME}:${APP_VERSION}"
        export DB_PRIMARY="sql"
        export PORT=8080
        export HOST="0.0.0.0"
        export CORS_ALLOWED_ORIGINS='["https://staging.yourdomain.com"]'
        export TRIAL_DURATION_DAYS=7
        export EARLY_ACCESS_ENABLED=false
        export NODE_ENV="staging"
        export COOKIE_SECURE=true
        export SESSION_COOKIE_NAME="__appforge-session"
        export SESSION_EXPIRY_DAYS=14
        export CLOUD_RUN_MEMORY="512Mi"
        export CLOUD_RUN_CPU="1"
        export CLOUD_RUN_MIN_INSTANCES=0
        export CLOUD_RUN_MAX_INSTANCES=5
        export CLOUD_RUN_TIMEOUT=300
        echo "[staging] Environment loaded — run ./scripts/deploy.sh to deploy"
        ;;

    dev)
        export GCP_PROJECT="${GCP_PROJECT:-appforge-dev}"           # ← change to your dev project
        export GCP_REGION="us-central1"
        export GCP_ZONE="us-central1-a"
        export SERVICE_NAME="appforge-backend"
        export AR_REPO="${GCP_REGION}-docker.pkg.dev/${GCP_PROJECT}/appforge"
        export CONTAINER_IMAGE="${AR_REPO}/${APP_NAME}:${APP_VERSION}"
        export DB_PRIMARY="sql"
        export PORT=8080
        export HOST="0.0.0.0"
        export CORS_ALLOWED_ORIGINS='["https://dev.yourdomain.com","http://localhost:3000"]'
        export TRIAL_DURATION_DAYS=7
        export EARLY_ACCESS_ENABLED=false
        export NODE_ENV="development"
        export COOKIE_SECURE=true
        export SESSION_COOKIE_NAME="__appforge-session"
        export SESSION_EXPIRY_DAYS=14
        export CLOUD_RUN_MEMORY="256Mi"
        export CLOUD_RUN_CPU="1"
        export CLOUD_RUN_MIN_INSTANCES=0
        export CLOUD_RUN_MAX_INSTANCES=2
        export CLOUD_RUN_TIMEOUT=300
        echo "[dev] Environment loaded — run ./scripts/deploy.sh to deploy"
        ;;

    *)
        echo "ERROR: Unknown environment '$ENV'. Use local, staging, or dev."
        exit 1
        ;;
esac
