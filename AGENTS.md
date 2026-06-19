# AGENTS.md for appforge-server

## Scope

- Applies to this repository.

## Must Follow

- Edit source definitions, not generated artifacts.
- Treat `api/proto/**` and Kotlin sources under `src/main/**` as the editable source of truth.
- Never hand-edit generated outputs under `src/generated/**`.
- Preserve domain/service boundaries.
- Keep transport concerns out of core domain logic.
- Check existing providers before adding new server abstractions; prefer reuse or extension.
- Prefer explicit error/null handling.

## Checks

- Run targeted server tests for changed packages first.
- Expand to broader suites only when signal indicates wider impact.

### Maintenance Gate

- `routing/**` changes:
  Run the matching route test class under `src/test/kotlin/com/appforge/server/routing/**`.
- `middleware/**` changes:
  Run `./gradlew test --tests "com.appforge.server.middleware.*"`.
- `providers/**` or `infrastructure/**` changes:
  Run the affected package tests first, then `./gradlew test --tests "com.appforge.server.infrastructure.DatabaseContractTest"`.
- SQL/database implementation changes:
  Also run `./gradlew test --tests "com.appforge.server.infrastructure.SqlDatabaseIntegrationTest"`.
- Public API, auth, billing, sharing, review, upload, or onboarding flow changes:
  Run the targeted unit/route tests and `./gradlew integrationTest`.
- `api/proto/**` changes:
  Run `npm run api:proto:check`, then the impacted server tests.
- Only run broad fallback suites when targeted checks point to cross-module impact:
  `./gradlew test`

## Doc Update Trigger

- Update `docs/architecture/providers.md` only when provider contracts, ownership, or reuse policy changed.
- Update `docs/testing/*` or the relevant module docs for runtime or operational changes.
- Avoid touching global architecture docs for purely local implementation edits.

## Key References

- `docs/index.md`
- `docs/architecture.md`
- `docs/testing/api-integration-coverage.md`
- `api/README.md`
- `src/main/kotlin/com/appforge/server/AGENTS.md`

## Out of Scope

- API schema design decisions owned by `api/proto/*`.
- Frontend UI and navigation conventions.
