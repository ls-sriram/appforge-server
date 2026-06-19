#!/usr/bin/env bash
# ──────────────────────────────────────────────────────────────────────────
# One-time GCloud project setup (run ONCE per GCP project)
# ──────────────────────────────────────────────────────────────────────────
# Creates:
#   - Artifact Registry repository
#   - Enables required APIs
#   - Configures IAM permissions
#
# Usage:
#   GCP_PROJECT=your-project ./scripts/setup-gcloud.sh
#   ENV=dev    ./scripts/setup-gcloud.sh   # Uses dev project from deploy.env.sh
#   ENV=staging ./scripts/setup-gcloud.sh  # Uses staging project
# ──────────────────────────────────────────────────────────────────────────

set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

cd "$PROJECT_DIR"
source "$SCRIPT_DIR/../deploy.env.sh"

if [ "$ENV" = "local" ]; then
    echo "ERROR: Setup is for GCP projects only. Use run-local.sh for local."
    exit 1
fi

echo "Setting up GCP project: $GCP_PROJECT"
gcloud config set project "$GCP_PROJECT"

# ─── Enable APIs ─────────────────────────────────────────────────────────
echo ""
echo "── Enabling APIs ──"
gcloud services enable \
    run.googleapis.com \
    artifactregistry.googleapis.com \
    secretmanager.googleapis.com \
    storage.googleapis.com \
    cloudbuild.googleapis.com

# ─── Create Artifact Registry ────────────────────────────────────────────
echo ""
echo "── Creating Artifact Registry: appforge ──"
gcloud artifacts repositories describe appforge \
    --location="$GCP_REGION" \
    --format="value(name)" 2>/dev/null || {
    gcloud artifacts repositories create appforge \
        --repository-format=docker \
        --location="$GCP_REGION" \
        --description="AppForge backend container images"
    echo "  ✓ Created: appforge"
}

# ─── Create GCS bucket for uploads (if not exists) ──────────────────────
UPLOADS_BUCKET="${UPLOADS_BUCKET:-${GCP_PROJECT}-uploads}"
echo ""
echo "── Checking GCS upload bucket: $UPLOADS_BUCKET ──"
if ! gsutil ls -b "gs://$UPLOADS_BUCKET" 2>/dev/null; then
    echo "  Creating bucket: $UPLOADS_BUCKET"
    gsutil mb -l "$GCP_REGION" "gs://$UPLOADS_BUCKET"
    echo "  ✓ Created: $UPLOADS_BUCKET"
else
    echo "  ✓ Bucket already exists: $UPLOADS_BUCKET"
fi

# ─── Configure Docker auth ───────────────────────────────────────────────
echo ""
echo "── Configuring Docker auth ──"
gcloud auth configure-docker "${GCP_REGION}-docker.pkg.dev" --quiet
echo "  ✓ Docker auth configured"

echo ""
echo "✅ GCP project setup complete: $GCP_PROJECT"
echo ""
echo "Next steps:"
echo "  1. Upload Firebase service account:   gcloud secrets create ${SERVICE_NAME}-${ENV}-FIREBASE_SERVICE_ACCOUNT_JSON --data-file=path/to/service-account.json"
echo "  2. Build & push:                      ENV=$ENV ./scripts/build-push.sh"
echo "  3. Deploy:                            ENV=$ENV ./scripts/deploy.sh"
echo "  4. Set secrets:                       ENV=$ENV ./scripts/set-secrets.sh"
