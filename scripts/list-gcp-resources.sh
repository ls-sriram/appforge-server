#!/bin/bash
set -e

# Usage: ./list-gcp-resources.sh <project_id>

PROJECT_ID=$1

if [[ -z "$PROJECT_ID" ]]; then
    echo "Usage: $0 <project_id>"
    exit 1
fi

echo "----------------------------------------------------"
echo "Listing Resources for Project: $PROJECT_ID"
echo "----------------------------------------------------"

echo ""
echo "=== Cloud Run Services ==="
gcloud run services list --project="$PROJECT_ID" --format="table(name,region,status.url)"

echo ""
echo "=== Artifact Registry Repositories ==="
gcloud artifacts repositories list --project="$PROJECT_ID" --format="table(name,format,location)"

echo ""
echo "=== GCS Buckets ==="
gsutil ls -p "$PROJECT_ID"

echo ""
echo "=== Cloud Functions ==="
gcloud functions list --project="$PROJECT_ID" --format="table(name,status,trigger.eventType)"

echo ""
echo "=== Service Accounts ==="
gcloud iam service-accounts list --project="$PROJECT_ID" --format="table(displayName,email)"

echo ""
echo "----------------------------------------------------"
echo "Done!"
echo "----------------------------------------------------"
