# Environment Bootstrap Guide (GCP & Firebase)

This guide documents the high-level process to stand up a new AppForge environment.

## Phase 1: Project Configuration

Set these values in config-manager (`scope=backend`) for your target project/environment:

- `FIREBASE_PROJECT_ID`: your Firebase project id (for example `example-app-dev`)
- `UPLOADS_BUCKET`: your storage bucket name (for example `appforge-example-app-dev-uploads`)
- `BILLING_ACCOUNT_ID`: your billing id

## Phase 2: Firebase Manual Setup

### Enable Authentication

1. Open the Firebase Console.
2. Go to **Build > Authentication**.
3. Enable **Email/Password** if your app uses Firebase Auth.

### Create Firebase Web App

Copy the frontend web config values into your local runtime config source:

- `EXPO_PUBLIC_FIREBASE_API_KEY`
- `EXPO_PUBLIC_FIREBASE_AUTH_DOMAIN`
- `EXPO_PUBLIC_FIREBASE_PROJECT_ID`
- `EXPO_PUBLIC_FIREBASE_APP_ID`

## Phase 3: Service Account Key

Generate a Firebase service-account JSON and then load it into config-manager with:

```bash
APP_ID=example-app npm run config:firebase:set -- --json /path/to/service-account.json
```
