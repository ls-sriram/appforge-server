# AppForge Backend Deployment Guide

## Prerequisites

- `gcloud` CLI installed and authenticated
- Docker installed
- Firebase project created
- GCP project created for the target environment

## Deployment Paths

### Fast Path

Use the generic GCP deploy script:

```bash
cd server
./scripts/deploy-gcp-simple.sh --project myapp-dev --env dev --step all
```

This handles:
- GCP API enablement
- Artifact Registry setup
- uploads bucket creation
- optional Firestore creation
- Docker build and push
- Cloud Run deploy

### Split Path

Run the steps individually when needed:

```bash
cd server
./scripts/deploy-gcp-simple.sh --project myapp-dev --env dev --step bootstrap
./scripts/deploy-gcp-simple.sh --project myapp-dev --env dev --step build
./scripts/deploy-gcp-simple.sh --project myapp-dev --env dev --step deploy
```

## Common Flags

- `--project`: GCP project id
- `--env`: `dev`, `staging`, or `prod`
- `--region`: GCP region, defaults to `us-central1`
- `--service`: Cloud Run service name, defaults to `appforge-backend`
- `--repo`: Artifact Registry repo, defaults to `appforge`
- `--image`: Docker image name
- `--tag`: Docker image tag
- `--bucket`: uploads bucket name
- `--env-file`: non-secret env file
- `--secrets-file`: secrets env file
- `--skip-firestore`: skip Firestore database creation

## Local Development

For local server development:

```bash
cd server
./scripts/run-local.sh
```

Or with local SQL support:

```bash
cd server
./scripts/run-local-sql.sh
```

## Deployment Flow

1. Build or build-and-push image with `deploy-gcp-simple.sh`
2. Deploy to Cloud Run with the same script
3. Update secrets with `set-secrets.sh` when needed

## Notes

- Keep deployment changes in the shell scripts above so the workflow stays consistent.
