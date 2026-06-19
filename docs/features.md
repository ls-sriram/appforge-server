# Backend Features

This public AppForge starter currently exposes the shared platform backend plus the `example-app` extension shell.

## Shared Platform Routes

Examples include:

- auth/session routes under `/api/v1/session/*`
- users/profile routes under `/api/v1/users/*`
- shared billing, uploads, reviews, recordings, sharing, and analytics infrastructure

## Example App Extension

`extensions/exampleapp/*` is intentionally minimal in the public starter.
It exists as the app-specific backend hook point for future app-owned routes.

## Reduced Boot Mode

When `APP_ID=example-app` and `FIREBASE_ENABLED=false`, the backend starts in reduced boot mode.
In that mode, AppForge exposes health/runtime verification only and does not mount Firebase-backed authenticated routes.
