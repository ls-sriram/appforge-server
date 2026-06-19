# AppForge API Reference

Complete endpoint documentation for the AppForge backend platform.

## Table of Contents
- [Authentication Model](#authentication-model)
- [Public Endpoints](#public-endpoints)
- [Session Endpoints](#session-endpoints)
- [User Profile Endpoint](#user-profile-endpoint)
- [Billing Endpoints](#billing-endpoints)
- [Upload Endpoints](#upload-endpoints)
- [Recording Endpoints](#recording-endpoints)
- [Review Endpoints](#review-endpoints)
- [Sharing Endpoints](#sharing-endpoints)
- [Public Share Endpoints](#public-share-endpoints)
- [System Endpoints](#system-endpoints)
- [Analytics Endpoints](#analytics-endpoints)
- [Internal Test Database Endpoint](#internal-test-database-endpoint)
- [Common Response Format](#common-response-format)
- [Error Responses](#error-responses)
- [Rate Limiting](#rate-limiting)

---

## Authentication Model

AppForge has two auth modes:

1. **Session endpoints** (`/session/*`, `/signup/*`) use explicit route requirements (for example JSON `idToken` or session cookie), and require `X-App-Id` where enforced by the route.
2. **User-authenticated endpoints** (using `UserAuthPlugin`) resolve auth in this order:
   - **Bearer Token** — `Authorization: Bearer <firebase-id-token>`
   - **Session Cookie** — HTTP-only cookie `appforge-session`
   - **Query Parameter** — `?token=<firebase-id-token>`

### Auth Requirements by Endpoint

| Endpoint Group | Auth Required | Auth Method |
|---------------|---------------|-------------|
| Health, Early Access Check | None | — |
| Session Login | `X-App-Id` + Firebase ID Token | JSON body: `idToken` |
| Session Logout | `X-App-Id` | Session cookie (optional) |
| Session Me | `X-App-Id` + Session cookie | Cookie only |
| Signup Finalize | `X-App-Id` + Firebase ID Token | JSON body: `idToken` + onboarding answers |
| Billing, Uploads, Reviews | User Auth | Bearer or Cookie (via `UserAuthPlugin`) |
| Public Shares | None | — |
| System Triggers | Internal Secret | `X-Internal-Secret` header |
| Webhooks | Signature Verification | HMAC-SHA256 |

---

## Public Endpoints

### Health Check

```
GET /health
```

**Response:**
```json
{ "status": "ok" }
```

**Status Codes:**
| Code | Meaning |
|------|---------|
| 200 | Server is running |
| 500 | Server error |

---

### Early Access — Check Status

```
GET /session/early-access/status
```

**Response:**
```json
{ "enabled": false }
```

**Notes:** Returns whether early access gating is currently active.

---

### Early Access — Check Email

```
POST /session/early-access/check
Content-Type: application/json

{ "email": "user@example.com" }
```

**Response (has access):**
```json
{ "hasAccess": true }
```

**Response (no access, waitlisted):**
```json
{ "hasAccess": false }
```

**Notes:** If early access is disabled, always returns `hasAccess: true`.

---

### Early Access — Join Waitlist

```
POST /session/early-access/join
Content-Type: application/json

{ "email": "user@example.com" }
```

**Response:**
```json
{ "success": true }
```

**Status Codes:**
| Code | Meaning |
|------|---------|
| 200 | Added to waitlist |
| 403 | Early access is disabled (no waitlist needed) |

---

## Session Endpoints

### Login

```
POST /session/login
Content-Type: application/json
X-App-Id: <app-id>

{ "idToken": "eyJhbGc..." }
```

**Response:**
```json
{ "success": true }
```

**Sets Cookie:** `appforge-session=<value>; HttpOnly; Secure; SameSite=Lax; Path=/; Max-Age=1209600`

**Side Effects:**
1. Verifies Firebase ID token
2. Checks early access (if enabled)
3. Creates Firebase session cookie
4. Returns session cookie in Set-Cookie header

**Status Codes:**
| Code | Meaning |
|------|---------|
| 200 | Login successful |
| 400 | Missing `X-App-Id` header |
| 401 | Invalid or expired ID token |
| 403 | Early access required, added to waitlist |

---

### Logout

```
POST /session/logout
X-App-Id: <app-id>
```

**Response:**
```json
{ "success": true }
```

**Sets Cookie:** `appforge-session=; HttpOnly; Secure; SameSite=Lax; Path=/; Max-Age=0`

**Side Effects:**
1. Revokes all Firebase refresh tokens for the user
2. Clears session cookie

**Status Codes:**
| Code | Meaning |
|------|---------|
| 200 | Logout successful |
| 400 | Missing `X-App-Id` header |

---

### Get Current User

```
GET /session/me
X-App-Id: <app-id>
Cookie: appforge-session=...
```

**Response:**
```json
{
  "uid": "firebase-uuid",
  "email": "user@example.com",
  "name": "User Name",
  "onboardingCompleted": true
}
```

**Status Codes:**
| Code | Meaning |
|------|---------|
| 200 | User info returned |
| 400 | Missing `X-App-Id` header |
| 401 | No valid session |

---

### Send Password Reset Link

```
POST /session/password/reset-link
Content-Type: application/json
X-App-Id: <app-id>

{ "email": "user@example.com" }
```

**Response:**
```json
{ "success": true }
```

**Status Codes:**
| Code | Meaning |
|------|---------|
| 200 | Request accepted |
| 400 | Missing `X-App-Id` or email |

---

### Signup Finalize

```
POST /signup/finalize
Content-Type: application/json
X-App-Id: <app-id>

{
  "idToken": "eyJhbGc...",
  "answers": [
    { "stepType": "personalization", "fieldId": "goal", "value": "pass-nbde" }
  ],
  "completedAt": { "seconds": "1716120000", "nanos": 0 }
}
```

**Response:**
```json
{ "success": true, "uid": "firebase-uuid" }
```

**Notes:** Creates/ensures user + trial, persists onboarding answers, and marks onboarding complete. Idempotent by user/question key.

**Status Codes:**
| Code | Meaning |
|------|---------|
| 200 | Signup finalized |
| 400 | Missing `X-App-Id` header |
| 401 | Invalid or expired ID token |

---

## User Profile Endpoint

### Get Full User Profile

```
GET /users/me
Authorization: Bearer <token>  (or session cookie)
X-App-Id: <app-id>
```

**Response:**
```json
{
  "uid": "firebase-uuid",
  "email": "user@example.com",
  "name": "User Name",
  "createdAt": "2026-05-15T10:00:00Z",
  "lastLoginAt": "2026-05-15T10:05:00Z",
  "plan": {
    "name": "trial",
    "status": "trialing",
    "expiresAt": "2026-05-22T10:00:00Z",
    "startedAt": "2026-05-15T10:00:00Z",
    "source": "trial",
    "cancelAtPeriodEnd": false,
    "checkoutUrl": null
  },
  "usage": {
    "reviewSubmissions": { "used": 1, "limit": 3, "unlocked": true },
    "entityCreations": { "used": 0, "limit": 5, "unlocked": true },
    "apiRequests": { "used": 0, "limit": 50, "unlocked": true },
    "sharedLinks": { "used": 0, "limit": 3, "unlocked": true },
    "storageBytes": { "used": 0, "limit": 10485760, "unlocked": true }
  }
}
```

**Status Codes:**
| Code | Meaning |
|------|---------|
| 200 | Profile returned |
| 401 | Unauthorized |

---

### Update Full User Profile

```
PUT /users/me
Content-Type: application/json
Authorization: Bearer <token>  (or session cookie)
X-App-Id: <app-id>

{ "name": "Updated Name" }
```

**Response:**
```json
{ "success": true }
```

**Status Codes:**
| Code | Meaning |
|------|---------|
| 200 | Profile updated |
| 400 | Invalid payload |
| 401 | Unauthorized |

---

## Billing Endpoints

### List Pricing Cards

```
GET /billing/pricing-cards
```

**Response:**
```json
{
  "cards": [
    {
      "id": "pro_monthly",
      "priceId": "price_123",
      "name": "Pro Monthly",
      "duration": "Monthly",
      "price": "$19",
      "originalPrice": null,
      "savings": null,
      "description": "$19/month, renews automatically",
      "featured": true,
      "monthlyPrice": "$19/mo",
      "features": [
        "100 API requests/month",
        "100 entities/month",
        "Unlimited sharing",
        "Priority support"
      ]
    }
  ]
}
```

### Get Entitlement Snapshot

```
GET /billing/entitlement
Authorization: Bearer <token>  (or session cookie)
X-App-Id: <app-id>
```

**Response:**
```json
{
  "userId": "firebase-uuid",
  "plan": "trial",
  "status": "trialing",
  "source": "trial",
  "startedAt": "2026-05-15T10:00:00Z",
  "expiresAt": "2026-05-22T10:00:00Z",
  "updatedAt": "2026-05-15T10:00:00Z",
  "features": [
    { "key": "review_submissions", "title": "Review Submissions", "unlocked": true, "used": 1, "limit": 3 },
    { "key": "entity_creations", "title": "Entity Creations", "unlocked": true, "used": 0, "limit": 5 },
    { "key": "api_requests", "title": "API Requests", "unlocked": true, "used": 0, "limit": 50 },
    { "key": "shared_links", "title": "Shared Links", "unlocked": true, "used": 0, "limit": 3 },
    { "key": "storage_bytes", "title": "Storage Bytes", "unlocked": true, "used": 0, "limit": 10485760 }
  ]
}
```

**Status Codes:**
| Code | Meaning |
|------|---------|
| 200 | Entitlement returned |
| 401 | Unauthorized |

---

### Create Checkout Session

```
POST /billing/checkout
Authorization: Bearer <token>
Content-Type: application/json

{
  "priceId": "price_123",
  "paymentType": "subscription",
  "customerEmail": "user@example.com",
  "successUrl": "https://app.example.com/billing/success",
  "cancelUrl": "https://app.example.com/billing/cancel",
  "metadata": {
    "source": "settings"
  }
}
```

**Response:**
```json
{
  "sessionId": "cs_test_123",
  "url": "https://test.dodopayments.com/checkout/cs_test_123"
}
```

**Notes:** Redirects the user to Dodo Payments to complete the payment.

**Status Codes:**
| Code | Meaning |
|------|---------|
| 200 | Checkout session created |
| 400 | Invalid priceId |
| 401 | Not authenticated |

---

### Cancel Subscription

```
POST /billing/subscription/cancel
Authorization: Bearer <token>
```

**Response:** HTTP 204 No Content

**Notes:** Cancels at the end of the current billing period. Status changes to `CANCEL_PENDING`.

---

### Billing Webhook (Dodo Payments)

```
POST /billing/webhook/dodo
Content-Type: application/json
webhook-id: msg_123
webhook-timestamp: 1743033600
webhook-signature: v1,...
```

**No auth required** — signature is verified using `DODO_PAYMENTS_WEBHOOK_KEY`.

**Response:**
```json
{ "received": true }
```

---

## Upload Endpoints

### Initialize Upload

```
POST /uploads/init
Authorization: Bearer <token>
Content-Type: application/json

{
  "type": "image",
  "entityId": "entity-uuid",
  "contentType": "image/jpeg",
  "sizeBytes": 1048576,
  "assetId": "client-chosen-asset-id"
}
```

**Response:**
```json
{
  "uploadId": "generated-upload-id",
  "assetId": "client-chosen-asset-id",
  "uploadUrl": "https://storage.googleapis.com/signed-put-url...",
  "expiresAtTimestamp": 1743034200000,
  "accessUrl": "/uploads/access/client-chosen-asset-id"
}
```

**Notes:**
- `type` is any string defined by the frontend (`"image"`, `"audio"`, `"document"`, etc.)
- `assetId` is chosen by the client and used to reference the file later
- `uploadUrl` is a signed GCS PUT URL (10-minute expiry)
- After uploading to `uploadUrl`, access the file via `accessUrl`

---

### Access Uploaded Asset

```
GET /uploads/access/{assetId}?redirect=true
Authorization: Bearer <token>
```

**Response (redirect=true, default):**
```
HTTP 302 Found
Location: https://storage.googleapis.com/signed-get-url...
```

**Response (redirect=false):**
```json
{ "url": "https://storage.googleapis.com/signed-get-url..." }
```

**Validation:**
- If `redirect` is provided, it must be exactly `true` or `false`; any other value returns `400 Bad Request`.

**Status Codes:**
| Code | Meaning |
|------|---------|
| 302 | Redirect to signed GCS URL |
| 200 | JSON with signed URL (when redirect=false) |
| 404 | Asset not found or expired |

---

## Recording Endpoints

All recording endpoints require user auth via Bearer token or session cookie (`UserAuthPlugin`), and `X-App-Id`.

### Create Recording

```
POST /api/v1/recordings
Content-Type: application/json
Authorization: Bearer <firebase-id-token>
X-App-Id: <app-id>

{
  "audioBase64": "base64-encoded-audio-bytes",
  "contentType": "audio/webm",
  "durationSeconds": 42
}
```

**Response:**
```json
{
  "id": "2f4a1a14-2e0b-4d0d-b4f8-d5af7a2c7be2",
  "createdAt": { "seconds": 1770000000, "nanos": 0 },
  "durationSeconds": 42,
  "contentType": "audio/webm",
  "sizeBytes": 38124
}
```

### List Recordings

```
GET /api/v1/recordings?limit=20
Authorization: Bearer <firebase-id-token>
X-App-Id: <app-id>
```

**Response:**
```json
{
  "recordings": [
    {
      "id": "2f4a1a14-2e0b-4d0d-b4f8-d5af7a2c7be2",
      "createdAt": { "seconds": 1770000000, "nanos": 0 },
      "durationSeconds": 42,
      "contentType": "audio/webm",
      "sizeBytes": 38124
    }
  ]
}
```

### Get Recording Content

```
GET /api/v1/recordings/{id}/content
Authorization: Bearer <firebase-id-token>
X-App-Id: <app-id>
```

**Response:** Raw audio bytes with `Content-Type` set to the recording MIME type.

**Status Codes:**
| Code | Meaning |
|------|---------|
| 200 | Recording content returned |
| 400 | Invalid request payload/params |
| 401 | Missing or invalid auth |
| 404 | Recording not found for current user |

---

## Review Endpoints

### List All Reviews

```
GET /api/v1/reviews
Authorization: Bearer <token>
```

**Response:**
```json
[
  {
    "id": "review-uuid",
    "authorRole": "ai",
    "authorId": "ai",
    "authorName": "AI Review Assistant",
    "authorEmail": null,
    "content": {
      "scores": { "clarity": 8 },
      "feedback": "Well-structured document..."
    },
    "createdAtTimestamp": 1743033600000
  }
]
```

---

### Get Reviews for Entity

```
GET /api/v1/entities/{type}/{id}/reviews
Authorization: Bearer <token>
```

**Path Parameters:**
| Parameter | Type | Description |
|-----------|------|-------------|
| `type` | string | Entity category (frontend-defined, e.g. `"document"`) |
| `id` | string | Entity ID |

**Response:**
```json
[
  {
    "id": "review-uuid",
    "authorRole": "ai",
    "authorName": "AI Review Assistant",
    "content": { "feedback": "..." },
    "createdAtTimestamp": 1743033600000
  }
]
```

---

### Request AI Review

```
POST /api/v1/entities/{type}/{id}/ai-review?versionId=optional-version
Authorization: Bearer <token>
```

**Response:**
```json
{ "status": "AI Review Enqueued", "versionId": "optional-version" }
```

**Status Codes:**
| Code | Meaning |
|------|---------|
| 202 | Review queued for async processing |
| 400 | Invalid entity type |

**Notes:** Fire-and-forget. The AI review is created asynchronously and will appear in the reviews list when complete.

---

## Sharing Endpoints

### Create Share Link

```
POST /api/v1/entities/{type}/{id}/shares
Authorization: Bearer <token>
Content-Type: application/json

{ "entityPath": "optional-path-hint" }
```

**Response:**
```json
{
  "token": "random-token",
  "shareUrl": "https://yourdomain.com/web/shares/random-token",
  "expiresAtTimestamp": 1745049600000
}
```

**Notes:**
- Creating a new share for the same entity replaces the previous one
- Default expiry: 21 days
- `entityPath` is optional and used for recording-type entities

---

### List Shares for Entity

```
GET /api/v1/entities/{type}/{id}/shares
Authorization: Bearer <token>
```

**Response:**
```json
[
  {
    "token": "random-token",
    "shareUrl": "https://yourdomain.com/web/shares/random-token",
    "expiresAtTimestamp": 1745049600000,
    "revokedAtTimestamp": null
  }
]
```

---

### Revoke Share

```
POST /api/v1/entities/shares/{token}/revoke
Authorization: Bearer <token>
```

**Response:** HTTP 204 No Content

**Status Codes:**
| Code | Meaning |
|------|---------|
| 204 | Share revoked |
| 403 | Not the share owner |

---

### Send Share via Email

```
POST /api/v1/entities/{type}/{id}/shares/{token}/email
Authorization: Bearer <token>
Content-Type: application/json

{ "toEmail": "recipient@example.com" }
```

**Response:** HTTP 204 No Content

**Notes:** Sends a ZeptoMail transactional email with the share link.

---

## Public Share Endpoints

### View Public Share

```
GET /shares/{token}
```

**Response:**
```json
{
  "share": {
    "token": "share-token",
    "entityType": "document",
    "entityId": "entity-uuid",
    "accessMode": "public_link",
    "expiresAt": { "seconds": "1743033600", "nanos": 0 },
    "revokedAt": null
  },
  "entity": {
    "id": "entity-uuid",
    "category": "document",
    "title": "Entity Title",
    "subtitle": "Optional subtitle",
    "content": "Full entity content as text",
    "question": "Optional question text",
    "assetUrl": "https://signed-gcs-url..."
  }
}
```

**No authentication required.** Share links are public by design.

**Status Codes:**
| Code | Meaning |
|------|---------|
| 200 | Share is valid, entity returned |
| 410 | Share expired or revoked |

---

### Fetch Shared Content (Compatibility Alias)

```
GET /shares/{token}/content
```

Returns raw bytes for share types that support direct binary content (currently recordings).  
`GET /shares/{token}/recording/content` remains supported for compatibility.

**Status Codes:**
| Code | Meaning |
|------|---------|
| 200 | Content stream returned |
| 410 | Share expired/revoked/unsupported |

---

### Submit Review on Shared Entity

```
POST /shares/{token}/reviews
Content-Type: application/json

{
  "displayName": "Reviewer Name",
  "scores": { "overall": 9 },
  "content": { "comments": "Great work!" }
}
```

**Response:**
```json
{
  "id": "review-uuid",
  "authorRole": "external",
  "authorName": "Reviewer Name",
  "content": { "overall": 9, "comments": "Great work!" },
  "createdAtTimestamp": 1743033600000
}
```

**No authentication required.** This is how external reviewers submit feedback.

---

## System Endpoints

All system endpoints require the `X-Internal-Secret` header matching the `INTERNAL_SECRET` environment variable.

### Trigger System Action

```
POST /api/v1/system/trigger
X-Internal-Secret: <secret>
```

**Response:** HTTP 200 OK

**Notes:** Accepts an optional `userId` query parameter.

---

### Approve Early Access

```
POST /api/v1/system/early-access/approve
X-Internal-Secret: <secret>
Content-Type: application/json

{ "email": "user@example.com" }
```

**Response:** HTTP 200 OK

---

## Analytics Endpoints

Analytics endpoints require the `X-Internal-Secret` header matching the `INTERNAL_SECRET` environment variable.

### Aggregate Analytics

```
GET /internal/analytics?window=60&limit=10
X-Internal-Secret: <secret>
```

**Response:**
```json
{
  "success": true,
  "summary": {
    "totalCalls": 120,
    "successRate": 0.98,
    "avgLatencyMs": 42,
    "p95LatencyMs": 95,
    "p99LatencyMs": 140
  },
  "topRoutes": [],
  "errors": [],
  "latency": {
    "p50": 30,
    "p90": 80,
    "p95": 95,
    "p99": 140,
    "max": 200
  }
}
```

### User Analytics

```
GET /internal/analytics/users/{userId}?window=60&limit=50
X-Internal-Secret: <secret>
```

**Response:** HTTP 200 OK with user summary, recent activity, and latency buckets.

### Initialize Analytics

```
POST /internal/analytics/init
X-Internal-Secret: <secret>
```

**Response:**
```json
{ "success": true, "message": "analytics initialized" }
```

---

## Internal Test Database Endpoint

`POST /internal/test-db` exercises the generic `Database` interface for local and internal diagnostics. This endpoint supplies the internal secret in the JSON body rather than the `X-Internal-Secret` header.

```
POST /internal/test-db
Content-Type: application/json

{
  "secret": "internal-secret",
  "op": "create",
  "collection": "example",
  "id": "record-1",
  "data": {
    "name": "Example"
  }
}
```

Supported `op` values are `create`, `get`, `update`, `delete`, `merge`, `setIfAbsent`, `findFirst`, and `query`.

**Response:**
```json
{
  "success": true,
  "data": {
    "name": "Example"
  }
}
```

---

## Common Response Format

Most endpoints return raw data. The wrapping envelope pattern is not enforced — Ktor serializes the response directly.

### Review Response (standardized)

```json
{
  "id": "string",
  "authorRole": "ai" | "external" | "self",
  "authorId": "string | null",
  "authorName": "string | null",
  "authorEmail": "string | null",
  "content": { "key": "value" },
  "createdAtTimestamp": 1743033600000
}
```

### Share Response (standardized)

```json
{
  "token": "string",
  "shareUrl": "https://yourdomain.com/web/shares/token",
  "expiresAtTimestamp": 1745049600000
}
```

### Upload Response (standardized)

```json
{
  "uploadId": "string",
  "assetId": "string",
  "uploadUrl": "https://signed-url...",
  "expiresAtTimestamp": 1743034200000,
  "accessUrl": "/uploads/access/assetId"
}
```

---

## Error Responses

### Standard Error

```json
{ "message": "Human-readable error description" }
```

### Error Status Codes

| Code | Exception | When |
|------|-----------|------|
| 400 | — | Invalid request body or parameters |
| 401 | `UnauthorizedException` | No valid auth token or session cookie |
| 403 | `ForbiddenException` | Early access required, not share owner, etc. |
| 404 | — | Resource not found |
| 410 | `GoneException` | Share expired or revoked |
| 500 | `Throwable` | Internal server error |

---

## Rate Limiting

AppForge does not implement rate limiting internally. Use:
- **API Gateway** (Cloud Run, Cloudflare) for external rate limiting
- **Firebase App Check** for abuse prevention
- **Rate limiting** for database-level protection
