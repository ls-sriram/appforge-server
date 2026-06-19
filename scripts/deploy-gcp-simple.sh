#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<USAGE
Simple GCP deploy script

Usage:
  ./scripts/deploy-gcp-simple.sh --project <gcp-project-id> --env <dev|staging|prod> [options]

Required:
  --project        GCP project id (example: myapp-prod)
  --env            Environment label (dev|staging|prod)

Options:
  --region         GCP region (default: us-central1)
  --service        Cloud Run service name (default: appforge-backend)
  --repo           Artifact Registry repo (default: appforge)
  --image          Docker image name (default: appforge-backend)
  --tag            Docker image tag (default: current git short sha or latest)
  --bucket         Uploads bucket (default: <project>-uploads)
  --cors-origins   CORS origins JSON array string for app env var
  --bucket-cors-origins Comma-separated origins for GCS bucket CORS
  --env-file       Path to non-secret env file (.env style)
  --secrets-file   Path to secrets env file (.env style). Values override env-file
  --memory         Cloud Run memory (default by env)
  --cpu            Cloud Run cpu (default by env)
  --min            Cloud Run min instances (default by env)
  --max            Cloud Run max instances (default by env)
  --timeout        Cloud Run timeout seconds (default: 300)
  --skip-firestore Skip Firestore database creation
  --step           bootstrap|build|deploy|all (default: all)

Examples:
  ./scripts/deploy-gcp-simple.sh --project myapp-dev --env dev --step all
  ./scripts/deploy-gcp-simple.sh --project myapp-prod --env prod --tag v1.2.0 --step deploy
USAGE
}

PROJECT=""
ENV_NAME=""
REGION="us-central1"
SERVICE_NAME="appforge-backend"
REPO_NAME="appforge"
IMAGE_NAME="appforge-backend"
IMAGE_TAG=""
UPLOADS_BUCKET=""
CORS_ALLOWED_ORIGINS='[]'
BUCKET_CORS_ORIGINS=""
ENV_FILE=""
SECRETS_FILE=""
STEP="all"
SKIP_FIRESTORE="false"

CLOUD_RUN_MEMORY=""
CLOUD_RUN_CPU=""
CLOUD_RUN_MIN_INSTANCES=""
CLOUD_RUN_MAX_INSTANCES=""
CLOUD_RUN_TIMEOUT="300"

PLAIN_ENV_KEYS_DEFAULT="APP_ENV,PORT,UPLOADS_BUCKET,FIREBASE_PROJECT_ID,CORS_ALLOWED_ORIGINS,EARLY_ACCESS_ENABLED,NODE_ENV,COOKIE_SECURE,SESSION_COOKIE_NAME,SESSION_EXPIRY_DAYS,TRIAL_DURATION_DAYS"
SECRET_ENV_KEYS_DEFAULT="INTERNAL_SECRET,OPENAI_API_KEY,DATABASE_SQL_URL,DATABASE_SQL_USER,DATABASE_SQL_PASSWORD,FIREBASE_SERVICE_ACCOUNT_JSON,DODO_PAYMENTS_API_KEY,DODO_PAYMENTS_WEBHOOK_KEY,ZEPTOMAIL_SEND_TOKEN,ZEPTOMAIL_API_URL,EMAIL_FROM_ADDRESS,EMAIL_FROM_NAME,UPLOAD_EVENT_SHARED_SECRET"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --project) PROJECT="$2"; shift 2 ;;
    --env) ENV_NAME="$2"; shift 2 ;;
    --region) REGION="$2"; shift 2 ;;
    --service) SERVICE_NAME="$2"; shift 2 ;;
    --repo) REPO_NAME="$2"; shift 2 ;;
    --image) IMAGE_NAME="$2"; shift 2 ;;
    --tag) IMAGE_TAG="$2"; shift 2 ;;
    --bucket) UPLOADS_BUCKET="$2"; shift 2 ;;
    --cors-origins) CORS_ALLOWED_ORIGINS="$2"; shift 2 ;;
    --bucket-cors-origins) BUCKET_CORS_ORIGINS="$2"; shift 2 ;;
    --env-file) ENV_FILE="$2"; shift 2 ;;
    --secrets-file) SECRETS_FILE="$2"; shift 2 ;;
    --memory) CLOUD_RUN_MEMORY="$2"; shift 2 ;;
    --cpu) CLOUD_RUN_CPU="$2"; shift 2 ;;
    --min) CLOUD_RUN_MIN_INSTANCES="$2"; shift 2 ;;
    --max) CLOUD_RUN_MAX_INSTANCES="$2"; shift 2 ;;
    --timeout) CLOUD_RUN_TIMEOUT="$2"; shift 2 ;;
    --skip-firestore) SKIP_FIRESTORE="true"; shift ;;
    --step) STEP="$2"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) echo "Unknown argument: $1"; usage; exit 1 ;;
  esac
done

if [[ -n "$ENV_FILE" && ! -f "$ENV_FILE" ]]; then
  echo "--env-file not found: $ENV_FILE"
  exit 1
fi

if [[ -n "$SECRETS_FILE" && ! -f "$SECRETS_FILE" ]]; then
  echo "--secrets-file not found: $SECRETS_FILE"
  exit 1
fi

trim_spaces() {
  local s="$1"
  s="${s#${s%%[![:space:]]*}}"
  s="${s%${s##*[![:space:]]}}"
  printf '%s' "$s"
}

strip_quotes() {
  local v="$1"
  if [[ ${#v} -ge 2 && "${v:0:1}" == '"' && "${v: -1}" == '"' ]]; then
    v="${v:1:${#v}-2}"
  fi
  printf '%s' "$v"
}

get_file_value() {
  local file="$1"
  local key="$2"
  [[ -z "$file" || ! -f "$file" ]] && return 1

  local line
  line="$(grep -E "^(export[[:space:]]+)?${key}=" "$file" | tail -n 1 || true)"
  [[ -z "$line" ]] && return 1

  line="${line#export }"
  line="${line#${key}=}"
  line="$(trim_spaces "$line")"
  line="$(strip_quotes "$line")"
  printf '%s' "$line"
}

get_merged_value() {
  local key="$1"
  local v=""
  v="$(get_file_value "$SECRETS_FILE" "$key" || true)"
  if [[ -n "$v" ]]; then
    printf '%s' "$v"
    return 0
  fi
  v="$(get_file_value "$ENV_FILE" "$key" || true)"
  if [[ -n "$v" ]]; then
    printf '%s' "$v"
    return 0
  fi
  v="${!key:-}"
  printf '%s' "$v"
}

if [[ -z "$PROJECT" || -z "$ENV_NAME" ]]; then
  echo "--project and --env are required"
  usage
  exit 1
fi

if [[ "$ENV_NAME" != "dev" && "$ENV_NAME" != "staging" && "$ENV_NAME" != "prod" ]]; then
  echo "--env must be one of: dev, staging, prod"
  exit 1
fi

if [[ "$STEP" != "bootstrap" && "$STEP" != "build" && "$STEP" != "deploy" && "$STEP" != "all" ]]; then
  echo "--step must be one of: bootstrap, build, deploy, all"
  exit 1
fi

if [[ -z "$IMAGE_TAG" ]]; then
  IMAGE_TAG="$(git rev-parse --short HEAD 2>/dev/null || echo latest)"
fi

if [[ -z "$UPLOADS_BUCKET" ]]; then
  UPLOADS_BUCKET="${PROJECT}-uploads"
fi

if [[ -z "$CLOUD_RUN_MEMORY" || -z "$CLOUD_RUN_CPU" || -z "$CLOUD_RUN_MIN_INSTANCES" || -z "$CLOUD_RUN_MAX_INSTANCES" ]]; then
  case "$ENV_NAME" in
    dev)
      CLOUD_RUN_MEMORY="${CLOUD_RUN_MEMORY:-256Mi}"
      CLOUD_RUN_CPU="${CLOUD_RUN_CPU:-1}"
      CLOUD_RUN_MIN_INSTANCES="${CLOUD_RUN_MIN_INSTANCES:-0}"
      CLOUD_RUN_MAX_INSTANCES="${CLOUD_RUN_MAX_INSTANCES:-2}"
      ;;
    staging)
      CLOUD_RUN_MEMORY="${CLOUD_RUN_MEMORY:-512Mi}"
      CLOUD_RUN_CPU="${CLOUD_RUN_CPU:-1}"
      CLOUD_RUN_MIN_INSTANCES="${CLOUD_RUN_MIN_INSTANCES:-0}"
      CLOUD_RUN_MAX_INSTANCES="${CLOUD_RUN_MAX_INSTANCES:-5}"
      ;;
    prod)
      CLOUD_RUN_MEMORY="${CLOUD_RUN_MEMORY:-1Gi}"
      CLOUD_RUN_CPU="${CLOUD_RUN_CPU:-2}"
      CLOUD_RUN_MIN_INSTANCES="${CLOUD_RUN_MIN_INSTANCES:-1}"
      CLOUD_RUN_MAX_INSTANCES="${CLOUD_RUN_MAX_INSTANCES:-20}"
      ;;
  esac
fi

AR_REPO="${REGION}-docker.pkg.dev/${PROJECT}/${REPO_NAME}"
CONTAINER_IMAGE="${AR_REPO}/${IMAGE_NAME}:${IMAGE_TAG}"

run_bootstrap() {
  echo "[bootstrap] Project: ${PROJECT}, Region: ${REGION}"
  gcloud config set project "$PROJECT"

  local merged_bucket_cors
  merged_bucket_cors="$(get_merged_value "BUCKET_CORS_ORIGINS")"
  if [[ -n "$merged_bucket_cors" ]]; then
    BUCKET_CORS_ORIGINS="$merged_bucket_cors"
  fi

  gcloud services enable \
    run.googleapis.com \
    artifactregistry.googleapis.com \
    secretmanager.googleapis.com \
    storage.googleapis.com \
    firestore.googleapis.com \
    cloudbuild.googleapis.com

  gcloud artifacts repositories describe "$REPO_NAME" \
    --location="$REGION" \
    --format="value(name)" >/dev/null 2>&1 || {
    gcloud artifacts repositories create "$REPO_NAME" \
      --repository-format=docker \
      --location="$REGION" \
      --description="Container images for ${SERVICE_NAME}"
  }

  gsutil ls -b "gs://${UPLOADS_BUCKET}" >/dev/null 2>&1 || \
    gsutil mb -l "$REGION" "gs://${UPLOADS_BUCKET}"

  if [[ -n "$BUCKET_CORS_ORIGINS" ]]; then
    local tmp_file="/tmp/${UPLOADS_BUCKET}-cors.json"
    local origins_json=""
    local trimmed
    IFS=',' read -r -a origin_list <<< "$BUCKET_CORS_ORIGINS"
    for origin in "${origin_list[@]}"; do
      trimmed="$(echo "$origin" | xargs)"
      [[ -z "$trimmed" ]] && continue
      if [[ -z "$origins_json" ]]; then
        origins_json="\"$trimmed\""
      else
        origins_json+=" ,\"$trimmed\""
      fi
    done

    if [[ -n "$origins_json" ]]; then
      cat > "$tmp_file" <<EOF
[
  {
    "origin": [${origins_json}],
    "method": ["GET", "PUT", "POST", "DELETE", "HEAD"],
    "responseHeader": ["Content-Type", "x-goog-resumable", "x-goog-meta-*"],
    "maxAgeSeconds": 3600
  }
]
EOF
      gsutil cors set "$tmp_file" "gs://${UPLOADS_BUCKET}"
      rm -f "$tmp_file"
      echo "Configured bucket CORS for gs://${UPLOADS_BUCKET}"
    fi
  fi

  if [[ "$SKIP_FIRESTORE" = "false" ]]; then
    if ! gcloud firestore databases describe --project="$PROJECT" >/dev/null 2>&1; then
      gcloud firestore databases create --location="$REGION" --type=firestore-native
    else
      echo "Firestore database already exists; skipping creation"
    fi
  fi

  gcloud auth configure-docker "${REGION}-docker.pkg.dev" --quiet
}

run_build() {
  echo "[build] Building and pushing ${CONTAINER_IMAGE}"
  gcloud config set project "$PROJECT"
  gcloud auth configure-docker "${REGION}-docker.pkg.dev" --quiet
  docker build -t "$CONTAINER_IMAGE" .
  docker push "$CONTAINER_IMAGE"
}

run_deploy() {
  echo "[deploy] Deploying ${SERVICE_NAME} -> Cloud Run"
  gcloud config set project "$PROJECT"

  local merged_port merged_cors merged_uploads_bucket merged_firebase_project
  merged_port="$(get_merged_value "PORT")"
  merged_cors="$(get_merged_value "CORS_ALLOWED_ORIGINS")"
  merged_uploads_bucket="$(get_merged_value "UPLOADS_BUCKET")"
  merged_firebase_project="$(get_merged_value "FIREBASE_PROJECT_ID")"

  merged_port="${merged_port:-8080}"
  merged_cors="${merged_cors:-$CORS_ALLOWED_ORIGINS}"
  merged_uploads_bucket="${merged_uploads_bucket:-$UPLOADS_BUCKET}"
  merged_firebase_project="${merged_firebase_project:-$PROJECT}"

  local env_list="APP_ENV=${ENV_NAME},PORT=${merged_port},UPLOADS_BUCKET=${merged_uploads_bucket},FIREBASE_PROJECT_ID=${merged_firebase_project},CORS_ALLOWED_ORIGINS=${merged_cors}"

  local plain_keys_csv secret_keys_csv
  plain_keys_csv="${PLAIN_ENV_KEYS:-$PLAIN_ENV_KEYS_DEFAULT}"
  secret_keys_csv="${SECRET_ENV_KEYS:-$SECRET_ENV_KEYS_DEFAULT}"

  local key val
  IFS=',' read -r -a plain_keys <<< "$plain_keys_csv"
  for key in "${plain_keys[@]}"; do
    key="$(trim_spaces "$key")"
    [[ -z "$key" || "$key" == "APP_ENV" || "$key" == "PORT" || "$key" == "UPLOADS_BUCKET" || "$key" == "FIREBASE_PROJECT_ID" || "$key" == "CORS_ALLOWED_ORIGINS" ]] && continue
    val="$(get_merged_value "$key")"
    [[ -z "$val" ]] && continue
    env_list+=",${key}=${val}"
  done

  local secret_specs=""
  local secret_id
  IFS=',' read -r -a secret_keys <<< "$secret_keys_csv"
  for key in "${secret_keys[@]}"; do
    key="$(trim_spaces "$key")"
    [[ -z "$key" ]] && continue
    val="$(get_merged_value "$key")"
    [[ -z "$val" ]] && continue

    secret_id="${SERVICE_NAME}-${ENV_NAME}-${key}"
    if ! gcloud secrets describe "$secret_id" --project="$PROJECT" >/dev/null 2>&1; then
      printf '%s' "$val" | gcloud secrets create "$secret_id" --replication-policy=automatic --data-file=- --project="$PROJECT" >/dev/null
    else
      printf '%s' "$val" | gcloud secrets versions add "$secret_id" --data-file=- --project="$PROJECT" >/dev/null
    fi

    if [[ -z "$secret_specs" ]]; then
      secret_specs="${key}=${secret_id}:latest"
    else
      secret_specs+=",${key}=${secret_id}:latest"
    fi
  done

  local deploy_args=(
    run deploy "$SERVICE_NAME"
    --image="$CONTAINER_IMAGE"
    --region="$REGION"
    --platform=managed
    --memory="$CLOUD_RUN_MEMORY"
    --cpu="$CLOUD_RUN_CPU"
    --min-instances="$CLOUD_RUN_MIN_INSTANCES"
    --max-instances="$CLOUD_RUN_MAX_INSTANCES"
    --timeout="$CLOUD_RUN_TIMEOUT"
    --port=8080
    --allow-unauthenticated
    --set-env-vars="$env_list"
    --no-cpu-throttling
  )

  if [[ -n "$secret_specs" ]]; then
    deploy_args+=(--set-secrets="$secret_specs")
  fi

  gcloud "${deploy_args[@]}"

  local service_url
  service_url="$(gcloud run services describe "$SERVICE_NAME" --region="$REGION" --format='value(status.url)')"
  echo "Service URL: ${service_url}"
  echo "Health: ${service_url}/health"
}

case "$STEP" in
  bootstrap) run_bootstrap ;;
  build) run_build ;;
  deploy) run_deploy ;;
  all)
    run_bootstrap
    run_build
    run_deploy
    ;;
esac

echo "Done: step=${STEP}, env=${ENV_NAME}, project=${PROJECT}, image=${CONTAINER_IMAGE}"
