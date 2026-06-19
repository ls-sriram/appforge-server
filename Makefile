# ──────────────────────────────────────────────────────────────────────────
# AppForge Backend — Makefile
# ──────────────────────────────────────────────────────────────────────────
# Usage:
#   make local        # Build and run locally
#   make dev-deploy   # Build, push, deploy to dev
#   make staging-deploy  # Build, push, deploy to staging
#   make dev-secrets  # Set secrets for dev
#   make staging-secrets  # Set secrets for staging
#   make setup-dev    # One-time GCP setup for dev
#   make setup-staging  # One-time GCP setup for staging
#   make clean        # Remove local build artifacts
# ──────────────────────────────────────────────────────────────────────────

SHELL := /bin/bash
.PHONY: local dev-deploy staging-deploy dev-secrets staging-secrets setup-dev setup-staging clean test build

# ─── Local ────────────────────────────────────────────────────────────────
local:
	ENV=local ./scripts/run-local.sh

# ─── Local with PostgreSQL ───────────────────────────────────────────────
local-sql:
	./scripts/run-local-sql.sh

local-sql-db:
	./scripts/run-local-sql.sh --db-only

local-sql-app:
	./scripts/run-local-sql.sh --app-only

setup-integrations:
	./scripts/dev/setup-integrations.sh

# ─── Local testing ───────────────────────────────────────────────────────
test-local:
	./scripts/test-local.sh

test-local-verbose:
	./scripts/test-local.sh --verbose

test-local-health:
	./scripts/test-local.sh --health-only

test-sql-integration:
	./gradlew test --tests "SqlDatabaseIntegrationTest"

# ─── Deploy ──────────────────────────────────────────────────────────────
dev-deploy:
	ENV=dev ./scripts/build-push.sh
	ENV=dev ./scripts/deploy.sh

staging-deploy:
	ENV=staging ./scripts/build-push.sh
	ENV=staging ./scripts/deploy.sh

# ─── Secrets ─────────────────────────────────────────────────────────────
dev-secrets:
	ENV=dev ./scripts/set-secrets.sh

staging-secrets:
	ENV=staging ./scripts/set-secrets.sh

# ─── One-time setup ──────────────────────────────────────────────────────
setup-dev:
	ENV=dev ./scripts/setup-gcloud.sh

setup-staging:
	ENV=staging ./scripts/setup-gcloud.sh

# ─── Build & test ────────────────────────────────────────────────────────
build:
	./gradlew build -x test

test:
	./gradlew test

# ─── Clean ───────────────────────────────────────────────────────────────
clean:
	./gradlew clean
	rm -rf build/ .gradle/
