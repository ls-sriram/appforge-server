# Shared API Contracts (`api/`)

This folder is the single source of truth for API contracts shared by:
- Frontend (TypeScript)
- Backend (Kotlin)

## Layout

- `proto/`: versioned protobuf contracts
- `buf.yaml`: module config + lint/breaking rules
- `buf.gen.yaml`: generation targets for TS and Kotlin

## Rules

- HTTP routes are defined only in proto via `google.api.http` annotations.
- API time fields must use `google.protobuf.Timestamp`.
- Avoid ad-hoc API timestamp fields (e.g. `createdAtTimestamp`, ISO strings).

## Commands (from repo root)

- `npm run api:proto:lint` — run repo-local proto guardrails
- `npm run api:proto:gen` — generate Kotlin protobuf sources
- `npm run api:proto:check` — run proto guardrails + generation

## Current Scope

Initial contracts are provided for:
- `auth.v1.AuthService`
- `users.v1.UsersService`

They define canonical route annotations for:
- `POST /session/login`
- `POST /session/logout`
- `GET /session/me`
- `GET /users/me`
- `PUT /users/me`
