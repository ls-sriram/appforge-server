#!/bin/bash
set -e

# infra.sh
# Lightweight infrastructure setup for the 'dev' environment.
# Sets up cloud storage buckets and CORS without the full deployment pipeline.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"
DEFAULT_APP_ID="$(node "$PROJECT_ROOT/scripts/app-registry.mjs" --default-app-id)"
APP_ID="${APP_ID:-$DEFAULT_APP_ID}"
CONFIG_PROJECT_ID="${CONFIG_PROJECT_ID:-$APP_ID}"
CONFIG_ENVIRONMENT="${CONFIG_ENVIRONMENT:-dev}"
CONFIG_SCOPE="${CONFIG_SCOPE:-backend}"
CONFIG_DB="${CONFIG_DB:-$(node "$PROJECT_ROOT/tools/config-manager/db-path.mjs" "$CONFIG_PROJECT_ID")}"
CONFIG_EXPORT_SCRIPT="$PROJECT_ROOT/tools/config-manager/export-env.mjs"

if [[ -f "$CONFIG_DB" && -f "$CONFIG_EXPORT_SCRIPT" ]]; then
    # shellcheck disable=SC2046
    eval "$(
      CONFIG_DB="$CONFIG_DB" \
        node "$CONFIG_EXPORT_SCRIPT" "$CONFIG_PROJECT_ID" "$CONFIG_ENVIRONMENT" "$CONFIG_SCOPE"
    )"
fi

# Load Secrets (favoring environment variables if already set)
echo "Loading dev configuration..."
[[ -z "${PROJECT_ID:-}" ]] && PROJECT_ID="${FIREBASE_PROJECT_ID:-}"
[[ -z "${UPLOADS_BUCKET:-}" ]] && UPLOADS_BUCKET="${UPLOADS_BUCKET:-}"

if [[ -z "$PROJECT_ID" ]]; then
    echo "Error: FIREBASE_PROJECT_ID not found in runtime env/config table"
    exit 1
fi

if [[ -z "$UPLOADS_BUCKET" ]]; then
    echo "Error: UPLOADS_BUCKET not found in runtime env/config table"
    exit 1
fi

echo "----------------------------------------------------"
echo "Bootstrapping Dev Infrastructure for $PROJECT_ID"
echo "Bucket: $UPLOADS_BUCKET"
echo "----------------------------------------------------"

# 1. Project Check & Creation
echo "Step 1: Checking project $PROJECT_ID..."
if ! gcloud projects describe "$PROJECT_ID" >/dev/null 2>&1; then
    echo "Project $PROJECT_ID not found."
    read -p "Would you like to create it now? (y/n) " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        gcloud projects create "$PROJECT_ID" --name="Dental Application Dev $PROJECT_ID"
        echo "Project created. Please ensure billing is linked in the GCP Console if needed."
    else
        echo "Aborting. Please set a valid FIREBASE_PROJECT_ID in $BACKEND_SECRETS"
        exit 1
    fi
fi

# 2. Link Billing
echo "Step 2: Checking billing..."
if [[ -z "$BILLING_ACCOUNT_ID" ]]; then
    echo "Warning: BILLING_ACCOUNT_ID not provided (neither in environment nor $BACKEND_SECRETS)"
    echo "If the next steps fail, please link billing manually or provide it via environment variable."
else
    echo "Linking project $PROJECT_ID to billing account $BILLING_ACCOUNT_ID..."
    gcloud billing projects link "$PROJECT_ID" --billing-account="$BILLING_ACCOUNT_ID" || echo "Billing already linked or failed."
fi

# 3. Enable Essential APIs
echo "Step 3: Enabling required APIs (Auth, Storage, IAM)..."
gcloud services enable \
  identitytoolkit.googleapis.com \
  storage-api.googleapis.com \
  storage-component.googleapis.com \
  iam.googleapis.com \
  --project="$PROJECT_ID"

# 4. Ensure the bucket exists
echo "Step 4: Ensuring bucket gs://$UPLOADS_BUCKET exists..."
if gsutil ls -p "$PROJECT_ID" "gs://$UPLOADS_BUCKET" >/dev/null 2>&1; then
    echo "Bucket already exists."
else
    echo "Creating bucket..."
    gsutil mb -p "$PROJECT_ID" -l us-central1 "gs://$UPLOADS_BUCKET"
fi

# 6. Configure CORS
echo "Step 5: Configuring CORS for gs://$UPLOADS_BUCKET..."

# Get CORS origins from env or default
CORS_ORIGINS_STR="${CORS_ALLOWED_ORIGINS:-}"
if [[ -z "$CORS_ORIGINS_STR" ]]; then
    CORS_ORIGINS_STR="http://localhost:3000,http://localhost:3001"
fi

# Convert comma-separated string to JSON array
# e.g. "a,b" -> ["a", "b"]
IFS=',' read -ra ADDR <<< "$CORS_ORIGINS_STR"
JSON_ORIGINS="["
FIRST_ORIGIN=true
for i in "${ADDR[@]}"; do
    if [ "$FIRST_ORIGIN" = true ]; then
        JSON_ORIGINS+="\"$i\""
        FIRST_ORIGIN=false
    else
        JSON_ORIGINS+=", \"$i\""
    fi
done
JSON_ORIGINS+="]"

CORS_JSON=$(cat <<EOF
[
  {
    "origin": $JSON_ORIGINS,
    "method": ["GET", "PUT", "POST", "DELETE", "HEAD"],
    "responseHeader": ["Content-Type", "x-goog-resumable"],
    "maxAgeSeconds": 3600
  }
]
EOF
)

echo "$CORS_JSON" > /tmp/cors-config.json
gsutil cors set /tmp/cors-config.json "gs://$UPLOADS_BUCKET"
rm /tmp/cors-config.json

# 7. Link Firebase
echo "Step 6: Linking GCP project to Firebase..."
if command -v firebase >/dev/null 2>&1; then
    # firebase projects:list returns a table, we use grep on the whole output
    if firebase projects:list | grep -q "$PROJECT_ID" >/dev/null 2>&1; then
        echo "Project $PROJECT_ID is already a Firebase project."
    else
        echo "Adding Firebase resources to $PROJECT_ID..."
        if ! firebase projects:addfirebase "$PROJECT_ID"; then
            echo "----------------------------------------------------"
            echo "⚠️  Automatic Firebase linking failed."
            echo "This usually happens if the project is already linked or there's a permission sync delay."
            echo "Please verify/add it manually here: https://console.firebase.google.com/project/$PROJECT_ID/overview"
            echo "----------------------------------------------------"
        fi
    fi
else
    echo "Warning: 'firebase' CLI not found. Skipping Firebase linking."
    echo "You must manually add Firebase at: https://console.firebase.google.com/"
fi

echo "----------------------------------------------------"
echo "✅ Dev Infrastructure bootstrap complete!"
echo "Your local instance is ready to use $PROJECT_ID"
echo "----------------------------------------------------"
