# Local Development with PostgreSQL

Quick start for running the AppForge backend locally with PostgreSQL.

## Quick Start

1. Install dependencies at the repo root:

```bash
npm install
```

2. Initialize local config for the reference app:

```bash
APP_ID=example-app npm run local:init
```

3. Optional: configure backend Firebase from a service-account JSON:

```bash
APP_ID=example-app npm run config:firebase:set -- --json /path/to/service-account.json
```

4. Start the local stack:

```bash
npm run local:start
```

## Notes

- Config-manager (`tools/config-manager`) stores local backend and frontend runtime values.
- When `APP_ID=example-app` and `FIREBASE_ENABLED=false`, the backend supports reduced boot mode for health/runtime verification only.
- In reduced boot mode, authenticated Firebase-backed routes such as `/api/v1/session/*` are not mounted.
- The local Postgres topology uses one shared Docker container with one app-specific database per app id.
