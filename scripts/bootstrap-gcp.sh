#!/bin/bash
set -e

# Usage: ./bootstrap-gcp.sh <base_project_id> <env> [step]
# Examples:
#   ./bootstrap-gcp.sh appforge staging all        (Run everything)
#   ./bootstrap-gcp.sh appforge staging project    (Only create project)
#   ./bootstrap-gcp.sh appforge staging iam        (Only configure IAM)

BASE_ID=$1
ENV=$2
STEP=${3:-all}

if [[ -z "$BASE_ID" || -z "$ENV" ]]; then
    echo "Usage: $0 <base_project_id> <env> [step]"
    echo "Steps: project, iam, billing, apis, registry, cors, all"
    exit 1
fi

if [[ "$ENV" != "staging" && "$ENV" != "prod" ]]; then
    echo "Invalid environment. Use 'staging' or 'prod'."
    exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"

PROJECT_ID="${BASE_ID}-${ENV}"
REGION="us-central1"

echo "----------------------------------------------------"
echo "Bootstrapping MyCaapid GCP Project"
echo "Project ID:   $PROJECT_ID"
echo "Environment:  $ENV"
echo "Step:         $STEP"
echo "----------------------------------------------------"

# Helper to check if a step should run
run_step() {
    [[ "$STEP" == "all" || "$STEP" == "$1" ]]
}

# 1. Create Project
if run_step "project"; then
    echo "[Step: project] Creating project $PROJECT_ID..."
    if gcloud projects describe "$PROJECT_ID" &>/dev/null; then
        echo "Project already exists."
    else
        gcloud projects create "$PROJECT_ID" --name="Dental Application $PROJECT_ID"
    fi
fi

# 2. IAM Roles
if run_step "iam"; then
    echo "[Step: iam] Configuring IAM roles for backend service account..."
    BACKEND_SA="appforge-backend@$PROJECT_ID.iam.gserviceaccount.com"
    
    # Check if SA exists, create if not
    if ! gcloud iam service-accounts describe "$BACKEND_SA" --project="$PROJECT_ID" &>/dev/null; then
        echo "Creating service account $BACKEND_SA..."
        gcloud iam service-accounts create appforge-backend \
            --display-name="Dental Application Backend Service Account" \
            --project="$PROJECT_ID"
    fi

    echo "Binding roles to $BACKEND_SA..."
    gcloud projects add-iam-policy-binding "$PROJECT_ID" --member="serviceAccount:$BACKEND_SA" --role="roles/logging.logWriter" --quiet
    gcloud projects add-iam-policy-binding "$PROJECT_ID" --member="serviceAccount:$BACKEND_SA" --role="roles/storage.objectViewer" --quiet
    gcloud projects add-iam-policy-binding "$PROJECT_ID" --member="serviceAccount:$BACKEND_SA" --role="roles/storage.objectAdmin" --quiet
    gcloud projects add-iam-policy-binding "$PROJECT_ID" --member="serviceAccount:$BACKEND_SA" --role="roles/bigquery.dataEditor" --quiet
    gcloud projects add-iam-policy-binding "$PROJECT_ID" --member="serviceAccount:$BACKEND_SA" --role="roles/bigquery.jobUser" --quiet
fi

# 3. Link Billing
if run_step "billing"; then
    echo "[Step: billing] Linking billing account..."
    if [[ -z "$BILLING_ACCOUNT_ID" ]]; then
        echo "Error: BILLING_ACCOUNT_ID is not set."
        echo "Please find it with: gcloud billing accounts list"
        echo "Then run: export BILLING_ACCOUNT_ID=XXXXXX-XXXXXX-XXXXXX"
        exit 1
    fi
    gcloud billing projects link "$PROJECT_ID" --billing-account="$BILLING_ACCOUNT_ID"
fi

# 4. Enable APIs
if run_step "apis"; then
    echo "[Step: apis] Enabling required APIs..."
    gcloud services enable \
      run.googleapis.com \
      artifactregistry.googleapis.com \
      iam.googleapis.com \
      cloudbuild.googleapis.com \
      compute.googleapis.com \
      pubsub.googleapis.com \
      --project="$PROJECT_ID"
fi

# 5. Create Artifact Registry
if run_step "registry"; then
    echo "[Step: registry] Creating Artifact Registry 'appforge'..."
    if gcloud artifacts repositories describe appforge --location="$REGION" --project="$PROJECT_ID" &>/dev/null; then
        echo "Repository already exists."
    else
        gcloud artifacts repositories create appforge \
            --repository-format=docker \
            --location="$REGION" \
            --description="Dental Application Container Registry" \
            --project="$PROJECT_ID"
    fi
fi

# 6. Configure CORS for uploads bucket
if run_step "cors"; then
    echo "[Step: cors] Configuring CORS for uploads bucket..."
    UPLOADS_BUCKET="${UPLOADS_BUCKET:-${PROJECT_ID}-uploads}"
    if ! gsutil ls -p "$PROJECT_ID" "gs://$UPLOADS_BUCKET" &>/dev/null; then
        echo "Bucket gs://$UPLOADS_BUCKET not found. Create it first (step: bucket)."
    else
        if [[ -z "$CORS_ALLOWED_ORIGINS" ]]; then
            echo "Error: CORS_ALLOWED_ORIGINS is not set. Provide a comma-separated list of allowed origins."
            echo "Example: CORS_ALLOWED_ORIGINS=\"https://appforge-staging-frontend-xxxx.a.run.app\""
            exit 1
        fi

        IFS=',' read -ra ADDR <<< "$CORS_ALLOWED_ORIGINS"
        JSON_ORIGINS="["
        FIRST_ORIGIN=true
        for i in "${ADDR[@]}"; do
            i="$(echo "$i" | xargs)"
            if [[ -z "$i" ]]; then
                continue
            fi
            if [[ "$FIRST_ORIGIN" = true ]]; then
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
    "responseHeader": ["Content-Type", "x-goog-resumable", "x-goog-meta-*"],
    "maxAgeSeconds": 3600
  }
]
EOF
)

        echo "$CORS_JSON" > /tmp/cors-config.json
        gsutil cors set /tmp/cors-config.json "gs://$UPLOADS_BUCKET"
        rm /tmp/cors-config.json
        echo "CORS configured for gs://$UPLOADS_BUCKET"
    fi
fi

echo "----------------------------------------------------"
echo "Bootstrap task ($STEP) complete!"
echo "----------------------------------------------------"
