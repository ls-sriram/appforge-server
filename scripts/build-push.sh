#!/usr/bin/env bash
# ──────────────────────────────────────────────────────────────────────────
# Build and push Docker image to Artifact Registry
# ──────────────────────────────────────────────────────────────────────────
# Prerequisites:
#   - gcloud authenticated (gcloud auth login)
#   - Artifact Registry repo exists: appforge
#   - ENV set to staging or dev
#
# Usage:
#   ENV=dev    ./scripts/build-push.sh
#   ENV=staging ./scripts/build-push.sh
# ──────────────────────────────────────────────────────────────────────────

set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

cd "$PROJECT_DIR"
source "$SCRIPT_DIR/../deploy.env.sh"

if [ "$ENV" = "local" ]; then
    echo "ERROR: build-push is for staging or dev only. Use run-local.sh for local."
    exit 1
fi

# ─── Verify gcloud auth ──────────────────────────────────────────────────
if ! gcloud auth list --filter=status:ACTIVE --format="value(account)" 2>/dev/null | grep -q .; then
    echo "ERROR: No active gcloud account. Run: gcloud auth login"
    exit 1
fi

# ─── Set project ─────────────────────────────────────────────────────────
echo "Using GCP project: $GCP_PROJECT"
gcloud config set project "$GCP_PROJECT"

# ─── Ensure Artifact Registry exists (idempotent) ────────────────────────
echo "Checking Artifact Registry repository: appforge"
gcloud artifacts repositories describe appforge \
    --location="$GCP_REGION" \
    --format="value(name)" 2>/dev/null || {
    echo "Creating Artifact Registry repository: appforge"
    gcloud artifacts repositories create appforge \
        --repository-format=docker \
        --location="$GCP_REGION" \
        --description="AppForge backend container images"
}

# ─── Configure Docker auth ───────────────────────────────────────────────
echo "Configuring Docker authentication for $GCP_REGION ..."
gcloud auth configure-docker "${GCP_REGION}-docker.pkg.dev" --quiet

# ─── Build image ─────────────────────────────────────────────────────────
echo "Building Docker image: $CONTAINER_IMAGE"
docker build -t "$CONTAINER_IMAGE" .

# ─── Push image ──────────────────────────────────────────────────────────
echo "Pushing image: $CONTAINER_IMAGE"
docker push "$CONTAINER_IMAGE"

echo ""
echo "✅ Image pushed: $CONTAINER_IMAGE"
