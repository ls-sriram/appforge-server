# AppForge Platform API Specification

> Version: 0.1.0 | Protocol: HTTPS | Serialization: JSON

## Table of Contents

- [1. Core Concepts](#1-core-concepts)
- [2. Authentication Model](#2-authentication-model)
- [3. Error Format](#3-error-format)
- [4. Session & Auth Endpoints](#4-session--auth-endpoints)
- [5. Entity System](#5-entity-system)
- [6. Reviews](#6-reviews)
- [7. Sharing](#7-sharing)
- [8. Uploads](#8-uploads)
- [9. Billing](#9-billing)
- [10. Health & System](#10-health--system)
- [11. Webhooks](#11-webhooks)
- [12. Headers & Metadata](#12-headers--metadata)

---

## 1. Core Concepts

### 1.1 User

A user is identified by a Firebase UID (opaque string, assigned by Firebase Auth).

```json
{
  "uid": "firebase-uid-string",
  "email": "user@example.com",
  "name": "User Display Name"
}
```

Users belong to zero or one **app** (identified by `X-App-Id` header). When `X-App-Id` is provided, the user's data is scoped under that app.

### 1.2 Entity

The fundamental data unit. An entity is **any** document, asset, or artifact that a frontend application wants to manage.

```json
{
  "id": "entity-uuid",
  "type": "document",
  "ownerId": "firebase-uid",
  "metadata": {
    "title": "My Document",
    "customField": "any-value"
  },
  "createdAt": "2025-01-01T00:00:00Z",
  "updatedAt": "2025-01-01T00:00:00Z"
}
```

**Key principle:** `type` is defined by the frontend. AppForge doesn't validate or interpret it. Valid types include `"document"`, `"image"`, `"audio"`, `"contract"`, `"design"`, or anything.

### 1.3 Entity Version

Entities support versioning. Each version is a snapshot of the entity's content.

```json
{
  "id": "version-uuid",
  "entityId": "entity-uuid",
  "content": "Full text or serialized state",
  "metadata": { "author": "user-id", "note": "Draft 2" },
  "createdAt": "2025-01-01T00:00:00Z"
}
```

### 1.4 Review

A review is feedback on an entity. Can be AI-generated or from external reviewers via share links.

```json
{
  "id": "review-uuid",
  "entityId": "entity-uuid",
  "entityCategory": "document",
  "authorRole": "ai",
  "authorName": "AI Review Assistant",
  "content": {
    "scores": { "clarity": 8, "structure": 7 },
    "feedback": "The document is well-organized but could use..."
  },
  "createdAtTimestamp": 1743033600000
}
```

**Author roles:**
| Role | Source | Trigger |
|------|--------|---------|
| `ai` | AI pipeline | `POST /api/v1/entities/{type}/{id}/ai-review` |
| `external` | Share link visitor | `POST /shares/{token}/reviews` |
| `self` | Owner | Reserved for future use |

### 1.5 Share

A share is a public (or semi-public) link to an entity. Anyone with the token can view and submit reviews.

```json
{
  "token": "high-entropy-random-string",
  "shareUrl": "https://yourdomain.com/web/shares/token",
  "expiresAtTimestamp": 1745049600000,
  "revokedAtTimestamp": null
}
```

### 1.6 Upload

File uploads use direct-to-GCS signed URLs.

```json
{
  "uploadId": "upload-uuid",
  "assetId": "client-chosen-id",
  "type": "image",
  "entityId": "entity-uuid",
  "uploadUrl": "https://storage.googleapis.com/signed-put-url...",
  "accessUrl": "/uploads/access/client-chosen-id",
  "expiresAtTimestamp": 1743034200000
}
```

---

## 2. Authentication Model

### 2.1 Auth Methods (Priority Order)

| Method | How | When |
|--------|-----|------|
| **Bearer Token** | `Authorization: Bearer <firebase-id-token>` | SPAs, mobile apps, server-to-server |
| **Session Cookie** | `Cookie: appforge-session=<value>` (HTTP-only) | Browser-based apps after login |
| **Query Param** | `?token=<firebase-id-token>` | Embeds, iframe integrations |

### 2.2 Auth Levels

| Level | Mechanism | Endpoints |
|-------|-----------|-----------|
| **Public** | No auth required | `/health`, `/shares/{token}`, early access check |
| **User Auth** | Bearer token or session cookie | All `/api/v1/` and `/uploads/` endpoints |
| **Internal Secret** | `X-Internal-Secret` header | `/api/v1/system/` endpoints |
| **Webhook Sig** | `webhook-signature` header | `/billing/webhook/dodo` |

### 2.3 RequestContext

Every authenticated request resolves a context available to all route handlers:

```
{
  "userId": "firebase-uid",
  "appId": "my-app",          // from X-App-Id header
  "teamId": "team-abc",       // from X-Team-Id header
  "roles": ["OWNER"]
}
```

---

## 3. Error Format

### 3.1 Standard Error

All errors return:

```json
{
  "message": "Human-readable description"
}
```

### 3.2 Error Status Codes

| Code | Meaning | Example |
|------|---------|---------|
| `400` | Bad Request | Invalid JSON, missing required fields |
| `401` | Unauthorized | No valid auth token or session cookie |
| `403` | Forbidden | Early access required, not share owner |
| `404` | Not Found | Entity or asset doesn't exist |
| `410` | Gone | Share link expired or revoked |
| `500` | Internal Server Error | Unexpected server error |

### 3.3 Example Error Responses

```json
// 401 Unauthorized
{ "message": "Unauthorized" }

// 403 Forbidden
{ "message": "Early access required. You have been added to our waitlist." }

// 410 Gone
{ "message": "Expired or Revoked" }
```

---

## 4. Session & Auth Endpoints

### 4.1 Health

```
GET /health
```

**Auth:** None

**Response (200):**
```json
{ "status": "ok" }
```

---

### 4.2 Early Access — Status

```
GET /session/early-access/status
```

**Auth:** None

**Response (200):**
```json
{ "enabled": false }
```

---

### 4.3 Early Access — Check Email

```
POST /session/early-access/check
Content-Type: application/json
```

**Auth:** None

**Request:**
```json
{ "email": "user@example.com" }
```

**Response (200):**
```json
{ "hasAccess": true }
```

When early access is disabled, always returns `hasAccess: true`.

---

### 4.4 Early Access — Join Waitlist

```
POST /session/early-access/join
Content-Type: application/json
```

**Auth:** None

**Request:**
```json
{ "email": "user@example.com" }
```

**Response (200):**
```json
{ "success": true }
```

**Response (403):**
```json
{ "message": "Early access is disabled." }
```

---

### 4.5 Login

```
POST /session/login
Content-Type: application/json
```

**Auth:** None (Firebase ID token in body)

**Request:**
```json
{ "idToken": "eyJhbGciOiJSUzI1NiIs..." }
```

**Response (200):**
```json
{ "success": true }
```

**Sets Cookie:** `appforge-session` (HTTP-only, Secure, SameSite=Lax, Path=/, Max-Age=1209600)

**Side Effects:**
1. Verifies Firebase ID token
2. Checks early access (if enabled)
3. Creates Firebase session cookie

**Response (401):**
```json
{ "message": "Unauthorized" }
```

**Response (403):**
```json
{ "message": "Early access required. You have been added to our waitlist." }
```

---

### 4.6 Logout

```
POST /session/logout
```

**Auth:** Session cookie

**Response (200):**
```json
{ "success": true }
```

**Sets Cookie:** `appforge-session=; Max-Age=0` (clears)

**Side Effects:** Revokes all Firebase refresh tokens for the user.

---

### 4.7 Get Current User

```
GET /session/me
```

**Auth:** Session cookie or Bearer token

**Response (200):**
```json
{
  "uid": "firebase-uuid",
  "email": "user@example.com",
  "name": "User Name",
  "onboardingCompleted": true
}
```

**Response (401):**
```json
{ "message": "Unauthorized" }
```

---

### 4.8 Signup Finalize

```
POST /signup/finalize
Content-Type: application/json
```

**Auth:** None (Firebase ID token in body)

**Request:**
```json
{
  "idToken": "eyJhbGciOiJSUzI1NiIs...",
  "answers": [
    { "stepType": "personalization", "fieldId": "goal", "value": "pass-nbde" }
  ],
  "completedAt": { "seconds": "1716120000", "nanos": 0 }
}
```

**Response (200):**
```json
{ "success": true, "uid": "firebase-uuid" }
```

**Notes:** Creates user/trial, persists onboarding answers, and marks onboarding completed.

---

## 5. Entity System

### 5.1 Concept

Entities are the core data model. They are **generic** — AppForge doesn't define what an entity is. The frontend decides via the `type` parameter in the URL.

```
GET /api/v1/entities/{type}/{id}/reviews
                       ↑
               Any string: "document",
               "image", "contract", etc.
```

### 5.2 Storage Layout

```
users/{userId}/entities/
  {entityId}/                    ← Entity metadata
    versions/
      {versionId}/               ← Version content
        { content: "...", ... }
```

When `X-App-Id` header is provided:
```
apps/{appId}/users/{userId}/entities/
  {entityId}/...
```

### 5.3 Entity Operations

AppForge does **not** expose CRUD endpoints for entities. Entity lifecycle is handled by:

| Operation | How |
|-----------|-----|
| **Create** | Frontend writes directly to API endpoint |
| **Read** | Via share links (`GET /shares/{token}`) |
| **Review** | `GET /api/v1/entities/{type}/{id}/reviews` |
| **AI Review** | `POST /api/v1/entities/{type}/{id}/ai-review` |
| **Share** | `POST /api/v1/entities/{type}/{id}/shares` |

Entities are created by the frontend application; AppForge provides the review, sharing, and upload infrastructure around them.

---

## 6. Reviews

### 6.1 List All Reviews (User's Entities)

```
GET /api/v1/reviews
```

**Auth:** User (Bearer or Cookie)

**Response (200):**
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
      "feedback": "Well-structured..."
    },
    "createdAtTimestamp": 1743033600000
  }
]
```

---

### 6.2 Get Reviews for Specific Entity

```
GET /api/v1/entities/{type}/{id}/reviews
```

**Auth:** User (Bearer or Cookie)

**Path Parameters:**
| Param | Type | Description |
|-------|------|-------------|
| `type` | string | Entity category (frontend-defined) |
| `id` | string | Entity ID |

**Response (200):**
```json
[
  {
    "id": "review-uuid",
    "authorRole": "external",
    "authorId": null,
    "authorName": "John Reviewer",
    "authorEmail": null,
    "content": {
      "overall": 9,
      "comments": "Great work!"
    },
    "createdAtTimestamp": 1743033600000
  }
]
```

---

### 6.3 Request AI Review

```
POST /api/v1/entities/{type}/{id}/ai-review?versionId=optional
```

**Auth:** User (Bearer or Cookie)

**Query Parameters:**
| Param | Required | Description |
|-------|----------|-------------|
| `versionId` | No | Specific version to review; latest if omitted |

**Response (202):**
```json
{
  "status": "AI Review Enqueued",
  "versionId": "optional"
}
```

**Notes:** Fire-and-forget. The AI review is created asynchronously. Poll `GET /api/v1/entities/{type}/{id}/reviews` for the result.

---

### 6.4 Submit Review on Shared Entity

```
POST /shares/{token}/reviews
Content-Type: application/json
```

**Auth:** None

**Request:**
```json
{
  "displayName": "Reviewer Name",
  "scores": { "overall": 9, "clarity": 8 },
  "content": { "comments": "Great work!" }
}
```

**Response (201):**
```json
{
  "id": "review-uuid",
  "authorRole": "external",
  "authorId": null,
  "authorName": "Reviewer Name",
  "authorEmail": null,
  "content": {
    "overall": 9,
    "clarity": 8,
    "comments": "Great work!"
  },
  "createdAtTimestamp": 1743033600000
}
```

---

## 7. Sharing

### 7.1 Create Share Link

```
POST /api/v1/entities/{type}/{id}/shares
Content-Type: application/json
```

**Auth:** User (Bearer or Cookie)

**Request:**
```json
{
  "entityType": "document",
  "entityPath": "optional-path-hint"
}
```

| Field | Required | Description |
|-------|----------|-------------|
| `entityType` | No | Type hint for the entity |
| `entityPath` | No | entity path hint (used for recording-type entities) |

**Response (201):**
```json
{
  "id": "share-token",
  "shareUrl": "https://yourdomain.com/web/shares/share-token",
  "expiresAtTimestamp": 1745049600000
}
```

**Notes:** Creating a new share for the same entity replaces the previous active share.

---

### 7.2 List Shares for Entity

```
GET /api/v1/entities/{type}/{id}/shares
```

**Auth:** User (Bearer or Cookie)

**Response (200):**
```json
[
  {
    "id": "share-token",
    "shareUrl": "https://yourdomain.com/web/shares/share-token",
    "expiresAtTimestamp": 1745049600000,
    "revokedAtTimestamp": null
  }
]
```

---

### 7.3 Revoke Share

```
POST /api/v1/entities/shares/{token}/revoke
```

**Auth:** User (Bearer or Cookie)

**Response (204):** No Content

**Response (403):**
```json
{ "message": "Unauthorized" }
```

---

### 7.4 Send Share via Email

```
POST /api/v1/entities/{type}/{id}/shares/{token}/email
Content-Type: application/json
```

**Auth:** User (Bearer or Cookie)

**Request:**
```json
{ "toEmail": "recipient@example.com" }
```

**Response (204):** No Content

**Notes:** Sends a ZeptoMail transactional email with the share link.

---

### 7.5 View Public Share

```
GET /shares/{token}
```

**Auth:** None

**Response (200):**
```json
{
  "entity": {
    "id": "entity-uuid",
    "category": "document",
    "title": "My Document Title",
    "subtitle": "Optional subtitle",
    "content": "Full entity content as text...",
    "question": "Optional question text",
    "assetUrl": "https://signed-gcs-url..."
  }
}
```

**Response (410):**
```json
{ "message": "Expired or Revoked" }
```

**Properties:**
| Property | Description |
|----------|-------------|
| `title` | From entity metadata (`title` or `name` field) |
| `subtitle` | From entity metadata (`subtitle` field) |
| `content` | Text content of the entity (for document types) |
| `question` | From entity metadata (`question` field) |
| `assetUrl` | Signed GCS URL for file attachments (if present) |

---

## 8. Uploads

### 8.1 Initialize Upload

```
POST /uploads/init
Content-Type: application/json
```

**Auth:** User (Bearer or Cookie)

**Request:**
```json
{
  "type": "image",
  "entityId": "entity-uuid",
  "contentType": "image/jpeg",
  "sizeBytes": 1048576,
  "assetId": "client-chosen-id"
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `type` | string | Yes | Upload type (frontend-defined: `"image"`, `"audio"`, `"document"`) |
| `entityId` | string | Yes | Entity this upload belongs to |
| `contentType` | string | Yes | MIME type of the file |
| `sizeBytes` | number | Yes | File size in bytes |
| `assetId` | string | Yes | Client-chosen unique identifier for the asset |

**Response (200):**
```json
{
  "uploadId": "upload-uuid",
  "assetId": "client-chosen-id",
  "uploadUrl": "https://storage.googleapis.com/bucket/users/uid/entities/eid/uploads/assetId.jpg?signature=...",
  "expiresAtTimestamp": 1743034200000,
  "accessUrl": "/uploads/access/client-chosen-id"
}
```

**Next Step:** Upload the file directly to `uploadUrl` using a PUT request:

```bash
curl -X PUT -H "Content-Type: image/jpeg" --data-binary @file.jpg "<uploadUrl>"
```

---

### 8.2 Access Uploaded Asset

```
GET /uploads/access/{assetId}?redirect=true
```

**Auth:** User (Bearer or Cookie)

**Query Parameters:**
| Param | Type | Default | Description |
|-------|------|---------|-------------|
| `redirect` | boolean | `true` | If `true`, returns 302 redirect. If `false`, returns JSON with URL. |

**Response (redirect=true, 302):**
```
Location: https://storage.googleapis.com/signed-get-url...
```

**Response (redirect=false, 200):**
```json
{ "url": "https://storage.googleapis.com/signed-get-url..." }
```

**Response (404):**
```json
{ "message": "Access URL not found or expired" }
```

---

### 8.3 Supported Content Types

| Category | MIME Types |
|----------|-----------|
| Images | `image/jpeg`, `image/png`, `image/webp`, `image/heic` |
| Audio | `audio/webm`, `audio/mp4`, `audio/mpeg`, `audio/wav` |
| Video | `video/webm`, `video/mp4` |
| Documents | `application/pdf` |

---

## 9. Billing

### 9.1 List Pricing Cards

```
GET /billing/pricing-cards
```

**Auth:** None

**Response (200):**
```json
{
  "cards": [
    {
      "id": "pro_monthly",
      "priceId": "pro_monthly",
      "name": "Pro Monthly",
      "duration": "monthly",
      "price": "$19/month",
      "originalPrice": null,
      "savings": null,
      "description": "$19/month, renews automatically",
      "featured": true,
      "monthlyPrice": "$19/month",
      "features": [
        "100 API requests/month",
        "100 entities/month",
        "Unlimited sharing",
        "Priority support"
      ]
    },
    {
      "id": "pro_annual",
      "priceId": "pro_annual",
      "name": "Pro Annual",
      "duration": "yearly",
      "price": "$199/year",
      "originalPrice": null,
      "savings": "30% off monthly",
      "description": "$199 upfront for 1 year access",
      "featured": false,
      "monthlyPrice": "$16.58/month",
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

---

### 9.2 Create Checkout Session

```
POST /billing/checkout
Content-Type: application/json
```

**Auth:** User (Bearer or Cookie)

**Request:**
```json
{
  "priceId": "pro_monthly",
  "paymentType": "subscription",
  "customerEmail": "user@example.com",
  "successUrl": "https://app.example.com/billing/success",
  "cancelUrl": "https://app.example.com/billing/cancel",
  "metadata": {
    "userId": "firebase-uid"
  }
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `priceId` | string | No | Product ID from pricing cards |
| `paymentType` | string | No | `"subscription"` or `"one_time"` |
| `customerEmail` | string | Yes | Customer email for the checkout |
| `successUrl` | string | No | Redirect after successful payment |
| `cancelUrl` | string | No | Redirect after cancelled payment |
| `metadata` | object | No | Custom key-value pairs |

**Response (200):**
```json
{
  "sessionId": "cs_test_...",
  "url": "https://test.dodopayments.com/checkout/cs_test_..."
}
```

**Notes:** Redirect the user to `url` to complete payment.

---

### 9.3 Cancel Subscription

```
POST /billing/subscription/cancel
```

**Auth:** User (Bearer or Cookie)

**Response (204):** No Content

**Notes:** Cancels at the end of the current billing period. Status changes to `CANCEL_PENDING`.

---

## 10. Health & System

### 10.1 Health Check

```
GET /health
```

**Auth:** None

**Response (200):**
```json
{ "status": "ok" }
```

---

### 10.2 System Trigger

```
POST /api/v1/system/trigger?userId=optional
```

**Auth:** Internal Secret (`X-Internal-Secret` header)

**Response (200):**
```json
{ "status": "triggered", "action": "dashboard-sync" }
```

---

### 10.3 Approve Early Access

```
POST /api/v1/system/early-access/approve
Content-Type: application/json
```

**Auth:** Internal Secret (`X-Internal-Secret` header)

**Request:**
```json
{
  "email": "user@example.com",
  "forceSend": false
}
```

**Response (200):**
```json
{
  "success": true,
  "email": "user@example.com",
  "previousStatus": "waitlisted",
  "created": false,
  "emailSent": true
}
```

---

## 11. Webhooks

### 11.1 Dodo Payments Webhook

```
POST /billing/webhook/dodo
```

**Auth:** Webhook signature (no user auth)

**Headers:**
| Header | Description |
|--------|-------------|
| `webhook-id` | Unique event identifier |
| `webhook-timestamp` | Unix timestamp of event |
| `webhook-signature` | HMAC-SHA256 signature |

**Request Body:** Raw JSON from Dodo Payments

**Response (200):**
```json
{ "received": true }
```

**Events Handled:**
| Event | Action |
|-------|--------|
| `payment.succeeded` | Activate/renew user entitlement |
| `subscription.canceled` | Mark entitlement as CANCEL_PENDING |
| `payment.failed` | Mark entitlement as PAST_DUE |

---

## 12. Headers & Metadata

### 12.1 Request Headers

| Header | Auth | Description |
|--------|------|-------------|
| `Authorization` | Bearer | `Bearer <firebase-id-token>` |
| `X-App-Id` | Platform | Identifies the frontend application |
| `X-Team-Id` | Platform | Optional team context |
| `X-Internal-Secret` | Internal | Secret for system endpoints |
| `X-Request-Id` | Tracing | Request correlation ID (echoed in response) |

### 12.2 Response Headers

| Header | Description |
|--------|-------------|
| `X-Request-Id` | Echoes the request's correlation ID |
| `Set-Cookie` | Session cookie on `/session/login` and `/session/logout` |
| `Cache-Control` | `public, max-age=86400` on `/shares/{token}` |

### 12.3 MDC Logging

Every request logs with these MDC keys:
- `requestId` — unique per request
- `userId` — Firebase UID (if authenticated)
- `appId` — Frontend app ID (if provided)
- `teamId` — Team ID (if provided)

---

## Appendix A: Data Models Reference

### Plan Entitlement

```json
{
  "customerId": "firebase-uid",
  "plan": "trial",
  "status": "trialing",
  "expiresAt": "2025-04-08T00:00:00Z",
  "startedAt": "2025-04-01T00:00:00Z",
  "source": "trial",
  "features": {
    "review_submissions": { "limit": 3, "used": 1, "unlocked": true },
    "entity_creations": { "limit": 5, "used": 0, "unlocked": true },
    "api_requests": { "limit": 50, "used": 12, "unlocked": true },
    "shared_links": { "limit": 3, "used": 0, "unlocked": true },
    "storage_bytes": { "limit": 10485760, "used": 0, "unlocked": true }
  }
}
```

### Plans

| Plan | Trigger | Entitlement |
|------|---------|-------------|
| `free` | Automatic | 1 review, everything else locked |
| `trial` | On first login | 3 reviews, 5 entities, 50 API requests, 3 shared links, 10MB storage |
| `pro` | After payment | 100 reviews, 100 entities, 1000 API requests, 100 shared links, 1GB storage |

### Billing Statuses

| Status | Meaning |
|--------|---------|
| `active` | Paid and current |
| `trialing` | Active trial period |
| `cancel_pending` | User canceled, valid until period end |
| `past_due` | Payment failed |
| `canceled` | Subscription fully terminated |

---

## Appendix B: Endpoint Summary

| # | Method | Path | Auth | Response |
|---|--------|------|------|----------|
| 1 | `GET` | `/health` | None | `HealthResponse` |
| 2 | `GET` | `/session/early-access/status` | None | `EarlyAccessStatusResponse` |
| 3 | `POST` | `/session/early-access/check` | None | `EarlyAccessCheckResponse` |
| 4 | `POST` | `/session/early-access/join` | None | `EarlyAccessJoinResponse` |
| 5 | `POST` | `/session/login` | None | `SessionLoginResponse` + Cookie |
| 6 | `POST` | `/session/logout` | Cookie | `SessionLogoutResponse` |
| 7 | `GET` | `/session/me` | Cookie/Token | `SessionMeResponse` |
| 8 | `POST` | `/signup/finalize` | ID Token | `SignupFinalizeResponse` |
| 9 | `GET` | `/billing/pricing-cards` | None | `PricingCardsResponse` |
| 10 | `POST` | `/billing/checkout` | User | `CheckoutResponse` |
| 11 | `POST` | `/billing/subscription/cancel` | User | 204 No Content |
| 12 | `POST` | `/billing/webhook/dodo` | Webhook Sig | `WebhookResponse` |
| 13 | `POST` | `/uploads/init` | User | `UploadInitResponse` |
| 14 | `GET` | `/uploads/access/{assetId}` | User | 302 or `{ url }` |
| 15 | `GET` | `/api/v1/reviews` | User | `List<ReviewResponse>` |
| 16 | `GET` | `/api/v1/entities/{type}/{id}/reviews` | User | `List<ReviewResponse>` |
| 17 | `POST` | `/api/v1/entities/{type}/{id}/ai-review` | User | 202 + status |
| 18 | `POST` | `/api/v1/entities/{type}/{id}/shares` | User | `ShareResponse` (201) |
| 19 | `GET` | `/api/v1/entities/{type}/{id}/shares` | User | `List<ShareSummaryResponse>` |
| 20 | `POST` | `/api/v1/entities/shares/{token}/revoke` | User | 204 No Content |
| 21 | `POST` | `/api/v1/entities/{type}/{id}/shares/{token}/email` | User | 204 No Content |
| 22 | `GET` | `/shares/{token}` | None | `PublicEntityResponse` |
| 23 | `POST` | `/shares/{token}/reviews` | None | `ReviewResponse` (201) |
| 24 | `POST` | `/api/v1/system/trigger` | Internal Secret | `Map<String, String>` |
| 25 | `POST` | `/api/v1/system/early-access/approve` | Internal Secret | `EarlyAccessApproveResponse` |

**25 endpoints total**
