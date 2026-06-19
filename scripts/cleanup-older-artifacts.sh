#!/bin/bash
set -e

function show_help() {
  echo "Usage: $0 <env> [args]"
  echo ""
  echo "env: staging or prod"
  echo ""
  echo "args:"
  echo "  --dry-run  Show what would be deleted without removing anything"
  echo "  --keep     Number of images to keep (default: 3)"
  echo "  --help, -h Show this help message"
  echo ""
  echo "Examples:"
  echo "  $0 staging --dry-run"
  echo "  $0 prod --keep 5"
}

ENV=$1
shift || true

if [[ -z "$ENV" || "$ENV" == "--help" || "$ENV" == "-h" ]]; then
  show_help
  exit 0
fi

if [[ "$ENV" != "staging" && "$ENV" != "prod" ]]; then
  echo "Invalid environment. Use 'staging' or 'prod'."
  exit 1
fi

REPO="appforge"
IMAGES=("backend" "frontend")
DRY_RUN="false"
KEEP=2

while [[ $# -gt 0 ]]; do
  case $1 in
    --dry-run) DRY_RUN="true"; shift ;;
    --keep) KEEP="$2"; shift 2 ;;
    --help|-h) show_help; exit 0 ;;
    *) echo "Unknown argument: $1"; exit 1 ;;
  esac
done

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"

PROJECT_ID="${PROJECT_ID:-$(gcloud config get-value project 2>/dev/null || true)}"

if [[ -z "$PROJECT_ID" ]]; then
  echo "Error: Could not determine PROJECT_ID. Please set the PROJECT_ID environment variable."
  exit 1
fi

PROJECT="$PROJECT_ID"

echo "Configuration:"
echo "  Environment: $ENV"
echo "  Project: $PROJECT"
echo "  Repository: $REPO"
echo "  Images: ${IMAGES[*]}"
echo "  Dry Run: $DRY_RUN"
echo "  Keep: $KEEP"
echo "--------------------------------"

cleanup_image() {
    local IMAGE_NAME=$1
    local FULL="us-central1-docker.pkg.dev/$PROJECT/$REPO/$IMAGE_NAME"
    
    echo "Processing image: $IMAGE_NAME"
    
    # Create temp files for processing
    local TMP_ALL_IMAGES=$(mktemp)
    local TMP_IMAGES_TO_DELETE=$(mktemp)

    echo "  Step 1: Fetching and sorting images by updateTime (newest first)..."
    # Use gcloud native sorting for reliability
    gcloud artifacts docker images list "$FULL" \
      --sort-by="~updateTime" \
      --format="value(version)" > "$TMP_ALL_IMAGES"

    local TOTAL_COUNT=$(wc -l < "$TMP_ALL_IMAGES")
    echo "  Found $TOTAL_COUNT images total for $IMAGE_NAME."

    if [ "$TOTAL_COUNT" -le "$KEEP" ]; then
      echo "  Count ($TOTAL_COUNT) is less than or equal to keep limit ($KEEP). Nothing to delete."
      rm "$TMP_ALL_IMAGES" "$TMP_IMAGES_TO_DELETE"
      return
    fi

    echo "  Step 2: Identifying images to remove (keeping newest $KEEP)..."
    tail -n +$((KEEP + 1)) "$TMP_ALL_IMAGES" > "$TMP_IMAGES_TO_DELETE"
    local DELETE_COUNT=$(wc -l < "$TMP_IMAGES_TO_DELETE")

    echo "  Found $DELETE_COUNT images eligible for deletion."

    echo "  Step 3: Processing deletions..."
    if [ "$DRY_RUN" = "true" ]; then
        echo "  [DRY RUN] usage enabled. The following actions WOULD be taken:"
        
        while IFS= read -r DIGEST || [ -n "$DIGEST" ]; do
            if [ -n "$DIGEST" ]; then
                 echo "    WOULD DELETE $FULL@$DIGEST"
            fi
        done < "$TMP_IMAGES_TO_DELETE"
    else
        while IFS= read -r DIGEST || [ -n "$DIGEST" ]; do
            if [ -n "$DIGEST" ]; then
                echo "  Deleting $FULL@$DIGEST"
                gcloud artifacts docker images delete \
                    "$FULL@$DIGEST" \
                    --project="$PROJECT" \
                    --quiet \
                    --delete-tags
            fi
        done < "$TMP_IMAGES_TO_DELETE"
    fi

    # Cleanup
    rm "$TMP_ALL_IMAGES" "$TMP_IMAGES_TO_DELETE"
    echo "  Cleanup complete for $IMAGE_NAME."
    echo "--------------------------------"
}

for img in "${IMAGES[@]}"; do
    cleanup_image "$img"
done

echo "All cleanups complete."
