# AppForge Backend

> A thin, generic, multi-tenant backend platform — deploy once, hook up any frontend.

## Table of Contents

- [Implemented Feature Reference](./features.md)
- [1. Overview](#1-overview)
- [2. Architecture](#2-architecture)
- [3. Tech Stack](#3-tech-stack)
- [4. Design Principles](#4-design-principles)
- [5. Project Structure](#5-project-structure)
- [6. Configuration](#6-configuration)
- [7. Request Context & Multi-Tenancy](#7-request-context--multi-tenancy)
- [8. Database Abstraction Layer](#8-database-abstraction-layer)
- [9. Authentication & Authorization](#9-authentication--authorization)
- [10. Database Schema](#10-database-schema)
- [11. API Reference](#11-api-reference)
- [12. Billing System](#12-billing-system)
- [13. Entity System](#13-entity-system)
- [14. Reviews](#14-reviews)
- [15. Sharing](#15-sharing)
- [16. Uploads](#16-uploads)
- [17. Infrastructure Layer](#17-infrastructure-layer)
- [18. External Services](#18-external-services)
- [19. Error Handling](#19-error-handling)
- [20. Logging & Observability](#20-logging--observability)
- [21. Deployment](#21-deployment)
- [22. Development Guide](#22-development-guide)
- [23. Extending AppForge](#23-extending-appforge)
- [24. Decision Log](#24-decision-log)

---

## 1. Overview

AppForge is a **thin backend platform** written in Kotlin/Ktor. It provides:

- **Authentication** — Firebase Auth with Bearer tokens and HTTP-only session cookies
- **Billing** — Generic entitlement system with Dodo Payments integration
- **Entities** — Generic document/asset storage with versioning
- **Reviews** — AI-powered and manual reviews on any entity type
- **Sharing** — Shareable links with public viewing
- **Uploads** — GCS signed URL generation for direct-to-cloud file uploads

### What AppForge Is

- A **platform**, not an application. It has no domain-specific business logic.
- Frontend applications define their own entity types (`"document"`, `"image"`, `"audio"`, or anything).
- AppForge provides the plumbing: auth, billing, storage, sharing, reviews.

### What AppForge Is Not

- Not a monolithic app backend with hardcoded domain models.
- Not tied to any specific product (no "statements", "recommendations", "interviews", etc.).
- Not opinionated about what entities mean — only that they have IDs, owners, versions, and reviews.

---

## 2. Architecture

```
┌─────────────────────────────────────────────────────────┐
│                    Frontend Application                  │
│                  (React, Mobile, etc.)                   │
│  Headers: X-App-Id, X-Team-Id                           │
└──────────────────────┬──────────────────────────────────┘
                       │ HTTPS
                       ▼
┌─────────────────────────────────────────────────────────┐
│                   AppForge Backend                       │
│                                                         │
│  ┌───────────────────────────────────────────────────┐  │
│  │              Routing Layer                         │  │
│  │  RoutesModule.kt                                  │  │
│  │  AuthRoutes | BillingRoutes | UploadRoutes        │  │
│  │  ReviewRoutes | ShareRoutes | HealthRoutes        │  │
│  │  SystemRoutes                                     │  │
│  └─────────────────────┬─────────────────────────────┘  │
│                        │                                │
│  ┌─────────────────────▼─────────────────────────────┐  │
│  │           Middleware Layer                         │  │
│  │  RequestContext (userId, appId, teamId, roles)     │  │
│  │  UserAuthPlugin | SecretAuthPlugin                 │  │
│  │  ErrorHandling | CORS                               │  │
│  └─────────────────────┬─────────────────────────────┘  │
│                        │                                │
│  ┌─────────────────────▼─────────────────────────────┐  │
│  │           Service Layer                            │  │
│  │                                                    │  │
│  │  AuthProvider  │  BillingProvider  │ ShareProvider │  │
│  │  UploadProvider│  ReviewProvider   │ SystemProvider│  │
│  │                                                    │  │
│  │  Each provider wires:                              │  │
│  │    Services ─► UseCases ─► Coordinators            │  │
│  │                        ─► Services                 │  │
│  │                        ─► Repositories             │  │
│  └─────────────────────┬─────────────────────────────┘  │
│                        │                                │
│  ┌─────────────────────▼─────────────────────────────┐  │
│  │         Infrastructure Layer                       │  │
│  │                                                    │  │
│  │  ┌────────────────────────────────────────────┐   │  │
│  │  │  Database Interface (abstract)             │   │  │
│  │  │  ┌─────────────┐  ┌───────────────────┐   │   │  │
│  │  │  │SqlDatabase  │  │InMemoryDatabase   │   │   │  │
│  │  │  │(PostgreSQL) │  │(testing)          │   │   │  │
│  │  │  └─────────────┘  └───────────────────┘   │   │  │
│  │  └────────────────────────────────────────────┘   │  │
│  │                                                    │  │
│  │  DatabaseRepository<T> (typed, with Mapper<T>)    │  │
│  │  DatabaseRepositoryFactory                          │  │
│  │  Resource<T> (Success/Error/Loading)               │  │
│  └─────────────────────┬─────────────────────────────┘  │
└────────────────────────┼────────────────────────────────┘
                         │
         ┌───────────────┼───────────────┐
         ▼               ▼               ▼
    ┌────────┐    ┌──────────┐    ┌──────────┐
    │  SQL (Primary)  │    │ External  │
    │  (DB)  │    │ (Cache)  │    │ Services │
    └────────┘    └──────────┘    └──────────┘
```

### Layer Responsibilities

| Layer | Responsibility | Key Files |
|-------|---------------|-----------|
| **Routing** | HTTP endpoint definitions, request deserialization, response serialization | `routing/*.kt` |
| **Middleware** | RequestContext resolution, auth, CORS, error handling | `middleware/*.kt` |
| **Use Cases** | Thin orchestration — call services, transform results | `services/*/UseCases.kt` |
| **Services** | Business operations — validation, coordination, external calls | `services/*/Service.kt` |
| **Coordinators** | Multi-step flows (e.g., upload initiation, billing webhooks) | `services/*Coordinator.kt` |
| **Repositories** | Data access — CRUD on a single collection | `services/*/repository/*Repository.kt` |
| **Infrastructure** | Generic abstractions — Database, Repository, Resource, Mapper | `infrastructure/*.kt` |

### Data Flow

```
HTTP Request (+ X-App-Id, X-Team-Id headers)
  → RequestContext middleware (resolves userId, appId, teamId, roles)
    → Route handler (deserialize body, extract params)
      → UseCase (orchestrate)
        → Service / Coordinator (business logic)
          → DatabaseRepository<T> (typed data access)
            → Database (PostgreSQL via Exposed)
              → SQL / GCS
  ← Response (serialize to JSON)
```

---

## 3. Tech Stack

### Core

| Component | Technology | Version | Why |
|-----------|-----------|---------|-----|
| Language | Kotlin | 1.9.24 | Null safety, coroutines, expressive syntax |
| JVM Target | Java | 17 | LTS, widespread cloud support |
| Framework | Ktor Server | 2.3.12 | Lightweight, Kotlin-native, plugin system |
| HTTP Server | Netty | (via Ktor) | High-performance, async I/O |
| Build Tool | Gradle (Kotlin DSL) | 8 | Convention plugins, dependency management |
| Serialization | kotlinx.serialization | 1.6.3 | Native Kotlin, compile-time safety |

### External Services

| Service | SDK | Purpose |
|---------|-----|---------|
| Firebase Admin SDK | 9.4.2 | Authentication, session cookies |
| PostgreSQL | 42.7.3 | Primary relational database |
| Google Cloud Storage | (via BOM) | File uploads, AI processing artifacts |
| OpenAI API | openai-client 3.8.2 | AI-powered reviews |
| Dodo Payments | dodo-payments-kotlin 1.70.0 | Payment processing |
| Stripe Java SDK | 24.12.0 | Declared but not actively used |
| ZeptoMail | REST API | Transactional emails |

### Testing

| Tool | Purpose |
|------|---------|
| JUnit 5 | Test framework |
| MockK | Mocking library |
| Ktor Test Client | HTTP route testing |

---

## 4. Design Principles

### 4.1 Thin Business Logic

Routes should **deserialize → call use case → return**. Use cases should **coordinate services**. Services should **perform operations**. No layer should contain complex decision trees — business rules belong in **configuration** or **database records**, not code.

### 4.2 Generic Entity Types

There is no `Statement`, `Recommendation`, or `InterviewAnswer`. There is only:

```
entities/{entityId}
  ├── type: string          (defined by the frontend)
  ├── metadata: map          (arbitrary key-value pairs)
  └── versions/
        ├── {versionId}
        └── {versionId}
```

The frontend decides what `"type"` means. AppForge stores and serves it.

### 4.3 Feature-Gated Billing

Billing is not hardcoded. Plans define a `features` map:

```json
{
  "entity_creations": { "limit": 100, "used": 23, "unlocked": true },
  "review_submissions": { "limit": 50, "used": 5, "unlocked": true }
}
```

Adding a new feature gate requires **no code changes** — just a new key in the features map.

### 4.4 Provider/UseCase Pattern

Every domain module follows the same pattern:

```
DomainProvider(env)
  └── DomainServices
        ├── DomainUseCases        ← called by routes
        │     └── UseCasesImpl
        ├── DomainService         ← actual operations
        ├── DomainCoordinator     ← multi-step flows
        └── DomainRepository      ← data access
```

This makes every module **predictable** and **testable**.

### 4.5 Multi-Tenant Ready

All user data is namespaced under `users/{userId}/`. Adding an `appId` layer (`apps/{appId}/users/{userId}/`) would support multiple frontend applications sharing one backend instance.

### 4.6 No Domain-Specific Prompts

AI review prompts are **not stored in the codebase**. They should be:
1. Stored in the database and loaded at runtime, or
2. Provided by the frontend application via the review request

The `prompts/` resource directory has been removed.

---

## 5. Project Structure

```
backend/
├── build.gradle.kts              # Gradle build config
├── settings.gradle.kts           # Project name: appforge-backend
├── Dockerfile                    # Multi-stage Docker build
├── .gitignore
├── .dockerignore
├── docs/
│   └── storage-and-indexing.md   # GCS storage strategy docs
├── src/
│   ├── main/
│   │   ├── resources/
│   │   │   ├── application.conf           # Base HOCON config
│   │   │   ├── application-dev.conf       # Dev overrides
│   │   │   └── logback.xml                # Logging config
│   │   └── kotlin/com.appforge/server/
│   │       ├── App.kt                     # Entry point
│   │       ├── api/                       # Request/Response DTOs
│   │       │   ├── AuthModels.kt
│   │       │   ├── BillingModels.kt
│   │       │   ├── CommonResponses.kt
│   │       │   ├── DodoPaymentsModels.kt
│   │       │   ├── UploadModels.kt
│   │       │   ├── reviews/
│   │       │   └── sharing/
│   │       ├── clients/                   # External service clients
│   │       │   ├── FirebaseAdminClient.kt
│   │       │   ├── DodoPaymentsClient.kt
│   │       │   └── OpenAIClient.kt
│   │       ├── config/                    # Configuration
│   │       │   ├── AppEnv.kt              # Top-level config aggregator
│   │       │   ├── ConfigReader.kt
│   │       │   ├── ConfigDefaults.kt
│   │       │   └── options/               # Per-domain option classes
│   │       ├── converters/                # Domain → API DTO converters
│   │       ├── db/
│   │       │   └── DbPaths.kt      # Centralized path constants
│   │       ├── infrastructure/            # Data access abstractions
│   │       │   ├── DataSource.kt
│   │       │   ├── DatabaseRepository.kt
│   │       │   ├── Repository.kt
│   │       │   ├── GcsDataSource.kt
│   │       │   ├── MapMapper.kt
│   │       │   └── Resource.kt
│   │       ├── middleware/                # Ktor plugins
│   │       │   ├── AuthMiddleware.kt
│   │       │   ├── UserAuthPlugin.kt
│   │       │   ├── SecretAuthPlugin.kt
│   │       │   └── ErrorHandling.kt
│   │       ├── routing/                   # Route definitions
│   │       │   ├── RoutesModule.kt
│   │       │   ├── Routes.kt              # CORS config
│   │       │   ├── AuthRoutes.kt
│   │       │   ├── BillingRoutes.kt
│   │       │   ├── HealthRoutes.kt
│   │       │   ├── ReviewRoutes.kt
│   │       │   ├── ShareRoutes.kt
│   │       │   ├── PublicShareRoutes.kt
│   │       │   ├── SystemRoutes.kt
│   │       │   ├── UploadRoutes.kt
│   │       │   └── ApiConverters.kt
│   │       └── services/                  # Domain services
│   │           ├── ServicesModule.kt      # Top-level factory
│   │           ├── ClientRegistry.kt
│   │           ├── auth/
│   │           ├── billing/
│   │           ├── dodopayments/
│   │           ├── email/
│   │           ├── openai/
│   │           ├── reviews/
│   │           ├── sharing/
│   │           ├── system/
│   │           ├── uploads/
│   │           └── wiring/
│   └── test/
│       └── kotlin/com.appforge/server/    # Tests mirror main structure
```

---

## 6. Configuration

### 6.1 Configuration Loading

Configuration loads in a **cascade** (later sources override earlier):

```
1. application.conf              (base defaults, committed)
2. application-{env}.conf        (environment overrides, committed)
3. .secrets.conf                 (secrets, NOT committed, .gitignore'd)
4. Environment variables         (highest priority, for containers)
```

```kotlin
// AppEnv.kt
val appEnv = System.getenv("APP_ENV") ?: "dev"
val config = ConfigFactory.load()  // HOCON merge
val reader = ConfigReader(config, System.getenv())
```

### 6.2 Configuration Options

| Option Class | Environment Variables | Purpose |
|-------------|----------------------|---------|
| `RuntimeOptions` | `PORT`, `HOST`, `CORS_ALLOWED_ORIGINS`, `NODE_ENV`, `INTERNAL_SECRET`, `EARLY_ACCESS_ENABLED` | Server runtime |
| `SessionOptions` | `SESSION_COOKIE_NAME`, `SESSION_EXPIRY_DAYS`, `COOKIE_SECURE` | Session cookies |
| `FirebaseOptions` | `FIREBASE_PROJECT_ID`, `FIREBASE_*` | Firebase Auth |
| `UploadOptions` | `UPLOADS_BUCKET`, `UPLOAD_MAX_BYTES`, `UPLOAD_URL_EXPIRY_SECONDS` | GCS uploads |
| `DodoPaymentsOptions` | `DODO_PAYMENTS_BASE_URL`, `DODO_PAYMENTS_API_KEY`, `DODO_PAYMENTS_WEBHOOK_KEY` | Payments |
| `OpenAIOptions` | `OPENAI_API_KEY` | AI reviews |
| `EmailOptions` | `ZEPTOMAIL_API_URL`, `ZEPTOMAIL_SEND_TOKEN`, `EMAIL_FROM_ADDRESS` | Transactional email |
| `BillingOptions` | `TRIAL_DURATION_DAYS`, `DODO_PRODUCT_IDS` | Billing catalog mapping |

### 6.3 Example Configuration

```hocon
# application.conf (base)
app {
  env = ${?APP_ENV}
}

ktor {
  deployment {
    port = ${?PORT}
    host = ${?HOST}
  }
  application {
    modules = [ com.appforge.server.AppKt.module ]
  }
}
```

```hocon
# application-dev.conf (dev overrides)
PORT=8080
HOST="0.0.0.0"
COOKIE_SECURE=false
SESSION_COOKIE_NAME="appforge-session"
CORS_ALLOWED_ORIGINS=["http://localhost:3000", "http://localhost:3001"]
FIREBASE_PROJECT_ID="appforge-dev"
DODO_PAYMENTS_BASE_URL="https://test.dodopayments.com"
UPLOADS_BUCKET="appforge-dev-uploads"
UPLOAD_MAX_BYTES=10485760
TRIAL_DURATION_DAYS=7
```

### 6.4 Secrets Management

Create a `.secrets.conf` file (never committed):

```hocon
FIREBASE_SERVICE_ACCOUNT_JSON="path/to/service-account.json"
DODO_PAYMENTS_API_KEY="sk_live_..."
DODO_PAYMENTS_WEBHOOK_KEY="whsec_..."
OPENAI_API_KEY="sk-..."
ZEPTOMAIL_SEND_TOKEN="ZP_..."
INTERNAL_SECRET="your-internal-secret-for-system-routes"
```

---

## 7. Request Context & Multi-Tenancy

### 7.1 RequestContext

Every authenticated request carries a rich context object, not just a `userId`:

```kotlin
data class RequestContext(
    val userId: String,               // Firebase UID
    val appId: String?,               // Which frontend app (from X-App-Id header)
    val teamId: String?,              // Optional team context (from X-Team-Id header)
    val roles: Set<PlatformRole>,     // OWNER, ADMIN
)
```

Routes access it via:
```kotlin
val ctx = call.attributes[RequestContextKey]
// ctx.userId, ctx.appId, ctx.teamId, ctx.isAdmin
```

### 7.2 Multi-Tenant Path Resolution

When `X-App-Id` is provided, all database collection paths are scoped under that app:

| Mode | Path |
|------|------|
| Single-app (no header) | `users/{userId}/entities/{entityId}` |
| Multi-tenant (`X-App-Id: my-app`) | `apps/my-app/users/{userId}/entities/{entityId}` |

This is handled transparently by `DbPaths`:
```kotlin
DbPaths.Collections.entities(userId, appId = ctx.appId)
// → "users/uid/entities"  (no appId)
// → "apps/my-app/users/uid/entities"  (with appId)
```

### 7.3 Frontend Integration

Frontends identify themselves via headers:

```
GET /api/v1/reviews
X-App-Id: example-app
X-Team-Id: team-abc
Authorization: Bearer <firebase-token>
```

---

## 8. Database Abstraction Layer

### 8.1 Architecture

AppForge no longer embeds Firestore directly. All data access flows through an abstract `Database` interface:

```kotlin
interface Database {
    val name: String
    suspend fun create(collection: String, id: String, data: Map<String, Any?>): Resource<String>
    suspend fun get(collection: String, id: String): Resource<Map<String, Any?>>
    suspend fun update(collection: String, id: String, data: Map<String, Any?>): Resource<Unit>
    suspend fun delete(collection: String, id: String): Resource<Unit>
    suspend fun merge(collection: String, id: String, data: Map<String, Any?>): Resource<Unit>
    suspend fun setIfAbsent(collection: String, id: String, data: Map<String, Any?>): Resource<Boolean>
    suspend fun findFirstByField(collection: String, field: String, value: Any): Resource<Map<String, Any?>?>
    suspend fun query(collection: String, query: DatabaseQuery): Resource<List<Map<String, Any?>>>
    suspend fun <T> transaction(block: suspend TransactionContext.() -> T): Resource<T>
}
```

### 8.2 Implementations

| Implementation | Status | Purpose |
|---------------|--------|---------|
| `SqlDatabase` | ✅ Full | PostgreSQL primary via raw JDBC + HikariCP. All CRUD ops implemented. |
| `SQLDatabase (PostgreSQL)` | ✅ Full | Now acts as **read cache only** — gets and merge updates from primary. |
| `InMemoryDatabase` | ✅ Full | Thread-safe map for testing. |
| *(cache layer removed)* | — | No longer used. SQL direct only. |

**Current default:** `DATABASE_PRIMARY=sql` (backward compatible). Set `DATABASE_PRIMARY=sql` when the SQL implementation is complete.

### 8.3 Configuration

```hocon
# .secrets.conf or environment variables

# # Option 1: SQL (default)
DATABASE_PRIMARY=sql
DATABASE_MODE=single

# Option 2: SQL with connection pool tuning
DATABASE_PRIMARY=sql
DATABASE_SQL_URL="jdbc:postgresql://localhost:5432/appforge"
DATABASE_SQL_USER="appforge"
DATABASE_SQL_PASSWORD="secret"
# No cache layer — SQL direct
DATABASE_MODE=cached

# Option 3: In-memory (testing only)
DATABASE_PRIMARY=memory
DATABASE_MODE=single
```

### 8.4 Caching Behavior

When `DATABASE_MODE=cached`:

```
Read Path:
  1. Query SQL directly
  2. On cache miss → read from primary (SQL)
  3. Populate cache with result

Write Path:
  1. Write to primary (SQL)
  2. 
  3. If cache write fails → log warning, don't fail the request
```

This is ideal for setups where:
- SQL is the source of truth (relational integrity, complex queries, analytics)
-  for the hot path
- Cache invalidation on writes keeps data reasonably fresh

### 8.5 Repository Layer

Domain services use `DatabaseRepository<T>`, which wraps `Database` with type safety:

```
DomainService
  → DatabaseRepository<User> (typed, with UserMapper)
    → Database (raw key-value operations)
      └── 
            ├── SqlDatabase (primary)
            └── SQLDatabase (PostgreSQL) (cache)
```

The `DatabaseRepositoryFactory` creates typed repositories:

```kotlin
val factory = DatabaseRepositoryFactory(database)
val userRepo = factory.create("users", UserMapper)
userRepo.get(userId)  // Returns Resource<User>
```

### 8.6 Activating SQL

To configure the database:

1. Add dependencies to `build.gradle.kts`:
```kotlin
implementation("org.jetbrains.exposed:exposed-core:0.47.0")
implementation("org.jetbrains.exposed:exposed-jdbc:0.47.0")
implementation("org.postgresql:postgresql:42.7.3")
```

2. Implement the stub methods in `SqlDatabase.kt` (see file for TODOs)

3. Set `DATABASE_PRIMARY=sql` in configuration

---

## 9. Authentication & Authorization

### 9.1 Authentication Flow

```
┌──────────┐                     ┌──────────┐                  ┌──────────┐
│ Frontend │                     │ AppForge │                  │ Firebase │
└────┬─────┘                     └────┬─────┘                  └────┬─────┘
     │                                │                             │
     │ 1. Sign in (Firebase Client SDK)                            │
     │──────────────────────────────────────────────────────────►│
     │                                │  ← ID Token                │
     │◄───────────────────────────────────────────────────────────│
     │                                │                             │
     │ 2. POST /session/login                                      │
     │    { idToken: "..." }          │                             │
     │───────────────────────────────►│                             │
     │                                │ 3. Verify ID Token          │
     │                                │────────────────────────────►│
     │                                │  ← UID, Email, Name         │
     │                                │◄────────────────────────────│
     │                                │                             │
     │ 4. Create Firebase Session Cookie                           │
     │                                │────────────────────────────►│
     │                                │  ← Session Cookie           │
     │                                │◄────────────────────────────│
     │                                │                             │
     │ 5. Set HTTP-only cookie                                       │
     │◄───────────────────────────────│                             │
     │  { success: true }             │                             │
```

### 7.2 Auth Mechanisms (in priority order)

When `AuthMiddleware.resolveUserId()` is called, it tries:

1. **Bearer Token** — `Authorization: Bearer <firebase-id-token>`
2. **Session Cookie** — HTTP-only, secure cookie named `appforge-session`
3. **Query Parameter** — `?token=<firebase-id-token>` (for embeds/signed URLs)

### 7.3 Auth Enforcement

| Plugin | Usage | Behavior |
|--------|-------|----------|
| `UserAuthPlugin` | Route-scoped | Resolves user ID, returns 401 if missing |
| `SecretAuthPlugin` | Route-scoped | Checks `X-Internal-Secret` header, returns 401 if mismatch |

### 7.4 Session Cookie Properties

| Property | Value | Rationale |
|----------|-------|-----------|
| Name | `appforge-session` | Generic, product-level session cookie |
| HTTP-only | `true` | XSS protection |
| Secure | `true` in prod, `false` in dev | HTTPS enforcement |
| SameSite | `Lax` | CSRF protection, allows top-level navigation |
| Max Age | `SESSION_EXPIRY_DAYS` (default 14) | Configurable session lifetime |
| Path | `/` | All routes |

### 7.5 Early Access Gate

When `EARLY_ACCESS_ENABLED=true`:
- During login, the user's email is checked against `admin/access/early-access` in the database
- If not approved, the user is auto-added to the waitlist
- Login returns 401 with a waitlist message

When `EARLY_ACCESS_ENABLED=false` (default for open deployments):
- All emails are auto-approved on first login

---

## 8. Database Schema

### 8.1 Database Structure

```
users/
  {userId}/
    billing/
      entitlement          ← Single doc, current billing state
    billing-history/
      {paymentId}          ← Payment records (append-only)
    entities/
      {entityId}           ← Entity metadata (type, title, etc.)
        versions/
          {versionId}      ← Version content
    reviews/
      {reviewId}           ← Reviews linked to entities
    entity-shares/
      {key}                ← Active share per entity
    profile/
      profile              ← User profile document
    dashboard/
      tasks/
      billing/

shared/
  global/
    shares/
      {token}              ← Share link metadata
    profiles/
      {hashedEmail}        ← Shared user profiles

admin/
  audit/
    billing/
      {webhookId}          ← Dedup log for payment webhooks
  uploads/
    metadata/
      {assetId}            ← Upload tracking
    processed/
      {assetId}            ← AI processing results
  access/
    early-access/
      {email}              ← Waitlist/approvals
```

### 8.2 Design Decisions

| Decision | Rationale |
|----------|-----------|
| **Per-user collections** | Natural multi-tenant isolation, database permissions can scope by userId |
| **Generic `entities/`** | No domain coupling — frontends define types via the `type` field |
| **`versions/` subcollection** | Every entity supports versioning, no schema changes needed for new types |
| **`shared/global/shares`** | Cross-user share discovery, tokens are high-entropy random strings |
| **`admin/audit/billing`** | Webhook deduplication — prevent double-crediting on retries |

### 8.3 Key Collections

#### `users/{userId}/billing/entitlement`

```json
{
  "customerId": "user-uuid",
  "plan": "trial",
  "status": "trialing",
  "expiresAtTimestamp": 1743638400000,
  "startedAtTimestamp": 1743033600000,
  "source": "trial",
  "features": {
    "review_submissions": { "limit": 3, "used": 1, "unlocked": true },
    "entity_creations": { "limit": 5, "used": 0, "unlocked": true },
    "api_requests": { "limit": 50, "used": 12, "unlocked": true },
    "shared_links": { "limit": 3, "used": 0, "unlocked": true },
    "storage_bytes": { "limit": 10485760, "used": 0, "unlocked": true }
  },
  "createdAtTimestamp": 1743033600000,
  "updatedAtTimestamp": 1743033600000
}
```

#### `shared/global/shares/{token}`

```json
{
  "entityId": "entity-uuid",
  "entityCategory": "document",
  "entityPath": null,
  "ownerId": "user-uuid",
  "expiresAtTimestamp": 1745049600000,
  "revokedAtTimestamp": null
}
```

---

## 9. API Reference

### 9.1 Public Endpoints (No Auth)

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/health` | Health check, returns `{ status: "ok" }` |
| `POST` | `/session/early-access/check` | Check if email has access |
| `POST` | `/session/early-access/join` | Join waitlist (when early access enabled) |
| `GET` | `/shares/{token}` | View a public shared entity |
| `POST` | `/shares/{token}/reviews` | Submit a review on a shared entity |

### 9.2 Session Endpoints

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `POST` | `/session/login` | Firebase ID Token | Exchange for session cookie |
| `POST` | `/session/logout` | Session Cookie | Revoke session |
| `GET` | `/session/me` | Session Cookie | Get current user info |
| `POST` | `/signup/init` | Firebase ID Token | Initialize trial entitlement |
| `DELETE` | `/users/me` | Session Cookie | Delete account data (idempotent), attempt Firebase Auth user deletion, and write deletion audit events |

### 9.3 Authenticated Endpoints (Bearer or Session Cookie)

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/billing/pricing-cards` | List available pricing plans |
| `GET` | `/billing/entitlement` | Get current user entitlement (auto-creates default `free` entitlement if missing) |
| `POST` | `/billing/checkout` | Create checkout session (Dodo Payments) |
| `POST` | `/billing/subscription/cancel` | Cancel subscription (at period end) |
| `POST` | `/uploads/init` | Initialize file upload, get GCS signed URL |
| `GET` | `/uploads/access/{assetId}` | Get signed access URL (redirects by default) |
| `GET` | `/api/v1/reviews` | List all reviews for current user |
| `GET` | `/api/v1/entities/{type}/{id}/reviews` | Get reviews for specific entity |
| `POST` | `/api/v1/entities/{type}/{id}/ai-review` | Request AI review |
| `POST` | `/api/v1/entities/{type}/{id}/shares` | Create share link |
| `GET` | `/api/v1/entities/{type}/{id}/shares` | List shares for entity |
| `POST` | `/api/v1/entities/shares/{token}/revoke` | Revoke share |
| `POST` | `/api/v1/entities/{type}/{id}/shares/{token}/email` | Send share via email |
| `POST` | `/api/v1/tasks` | Create task (`title` required, `tag` optional) |
| `GET` | `/api/v1/tasks` | List tasks (optional filters: `status`, `type`, `tag`) |
| `GET` | `/api/v1/tasks/{id}` | Get one task |
| `PATCH` | `/api/v1/tasks/{id}` | Partial update task fields |
| `POST` | `/api/v1/tasks/{id}/complete` | Mark task completed |
| `POST` | `/api/v1/tasks/{id}/reopen` | Reopen task |
| `DELETE` | `/api/v1/tasks/{id}` | Delete task |

### 9.4 Internal Endpoints (X-Internal-Secret Header)

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/v1/system/trigger` | Trigger system actions |
| `POST` | `/api/v1/system/early-access/approve` | Approve user for early access |

### 9.5 Webhook Endpoints (Signature Verified)

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/billing/webhook/dodo` | Receive Dodo Payments events |

---

## 10. Billing System

### 10.1 Architecture

```
┌──────────────┐     ┌───────────────┐     ┌──────────────┐
│   Frontend   │     │    AppForge   │     │ DodoPayments │
└──────┬───────┘     └───────┬───────┘     └──────┬───────┘
       │                     │                     │
       │ GET /billing/       │                     │
       │ pricing-cards       │                     │
       │◄────────────────────│                     │
       │                     │                     │
       │ POST /billing/      │                     │
       │ checkout            │                     │
       │────────────────────►│                     │
       │                     │ Create checkout     │
       │                     │────────────────────►│
       │                     │  ← session URL       │
       │                     │◄────────────────────│
       │ { sessionId, url }  │                     │
       │◄────────────────────│                     │
       │                     │                     │
       │  User completes     │                     │
       │  payment on Dodo    │                     │
       │                     │                     │
       │                     │ Webhook:            │
       │                     │ payment.succeeded   │
       │                     │◄────────────────────│
       │                     │                     │
       │                     │ 1. Verify signature │
       │                     │ 2. Check audit log  │
       │                     │ 3. Update entitlement│
       │                     │ 4. Send email       │
```

### 10.2 Plans

| Plan | Entitlement | Features |
|------|------------|----------|
| `FREE` | Always available | 1 review, everything else locked |
| `TRIAL` | Auto-created on signup | 3 reviews, 5 entities, 50 API requests, 3 shared links, 10MB storage |
| `PRO` | After payment | 100 reviews, 100 entities, 1000 API requests, 100 shared links, 1GB storage |

### 10.3 Products (Catalog)

| Product ID | Price | Billing | Plan | Expiry |
|-----------|-------|---------|------|--------|
| `pro_monthly` | $19/month | Subscription | PRO | 30 days (auto-renews) |
| `pro_annual` | $199/year | One-time | PRO | 365 days |

### 10.4 Webhook Processing

The Dodo webhook handler (`POST /billing/webhook/dodo`):

1. **Verify signature** — HMAC verification using `DODO_PAYMENTS_WEBHOOK_KEY`
2. **Parse event** — Determine event type (payment succeeded, subscription canceled, etc.)
3. **Deduplicate** — Check `admin/audit/billing/{webhookId}` to prevent double-processing
4. **Update entitlement** — Modify `users/{userId}/billing/entitlement`
5. **Send email** — Payment confirmation via ZeptoMail

### 10.5 Entitlement Lifecycle

```
FREE ───► TRIAL ───► ACTIVE ───► CANCEL_PENDING ───► CANCELED
          (7 days)   (paid)       (user cancels)     (period ends)
                                    │
                                    ▼
                                 PAST_DUE
                                 (payment failed)
```

---

## 11. Entity System

### 11.1 Generic Entity Model

AppForge has **no predefined entity types**. A frontend application defines types by the string it sends:

```
POST /api/v1/entities/{type}/{id}/...
                           ↑
                    Any string: "document", "image", "report",
                    "contract", "design", "recording", etc.
```

### 11.2 Storage Layout

```
users/{userId}/entities/
  {entityId}/                    ← Entity metadata
    versions/
      {versionId}/               ← Version content
        { content: "...", updatedAt: timestamp, ... }
```

### 11.3 Entity Categories (Reviews)

The review system uses `EntityCategory(value: String)`:

| Frontend Category | Pipeline Used |
|------------------|---------------|
| `"document"`, `"text"` | TextDocumentPipeline (reads entity content) |
| `"audio"`, `"recording"` | AudioRecordingPipeline (reads GCS asset bytes) |
| `"image"`, `"photo"` | ImageReviewPipeline (reads GCS asset bytes) |
| Any other string | Defaults to TextDocumentPipeline |

---

## 12. AI Support (Platform Level)

AI capabilities are **built into the platform** — every extension gets them for free. No extension needs to implement its own AI integration.

### 12.1 AI Capabilities

| Capability | Models Used | Input | Output |
|-----------|------------|-------|--------|
| **Document Review** | GPT-4 | Text content from entity versions | Structured JSON feedback with scores and comments |
| **Audio Review** | Whisper (transcription) + GPT-4 | Audio bytes from GCS | Structured JSON feedback on transcript |
| **Image Review** | GPT-4 Vision | Image bytes from GCS | Structured JSON analysis |

### 12.2 How It Works

```
Frontend:
  POST /api/v1/entities/{type}/{id}/ai-review?versionId=optional
                    ↑
         Any entity type: "document",
         "xray", "contract", etc.

Platform:
  1. Resolve entity content (text from database, or bytes from GCS)
  2. Select pipeline based on entity type:
     - Text → TextDocumentPipeline → GPT-4
     - Audio → AudioRecordingPipeline → Whisper → GPT-4
     - Image → ImageReviewPipeline → GPT-4 Vision
  3. Create Review document with AI feedback
  4. Return 202 Accepted (async processing)
```

### 12.3 Configuration

```hocon
# .secrets.conf
OPENAI_API_KEY="sk-..."
```

The OpenAI client is initialized once in `ClientRegistry` and shared across all review pipelines. Extensions don't need to configure their own AI — they just trigger reviews via the platform API.

### 12.4 For Extensions

Extensions don't implement AI themselves. They:
1. **Trigger reviews** via the platform endpoint (any entity type works)
2. **Read reviews** via `GET /api/v1/entities/{type}/{id}/reviews`
3. **Subscribe to hooks** like `after-review-submitted` to react when AI finishes

```kotlin
// Extension hooks into AI workflow
override fun defineHooks(): List<HookRegistration> = listOf(
    HookRegistration("after-review-submitted") { payload ->
        val authorRole = payload["authorRole"]
        if (authorRole == "ai") {
            notifyUserOfAIResults(payload["entityId"] as String)
        }
        HookResponse(allow = true)
    },
)
```

### 12.5 Review Prompts

AI review prompts are **not stored in the codebase**. They should be:
1. Stored in the database and loaded at runtime, or
2. Provided by the frontend application via the review request

This keeps the platform generic — different apps can use different prompts for the same AI model.

---

## 13. Reviews

### 13.1 Review Types

| Type | Author | Trigger |
|------|--------|---------|
| **AI Review** | `ai` | `POST /api/v1/entities/{type}/{id}/ai-review` |
| **External Review** | `external` | `POST /api/v1/entities/{type}/{id}/shares/{token}/reviews` (via share link) |
| **Self Review** | `self` | Reserved for future use |

### 13.2 AI Review Pipeline

```
POST /api/v1/entities/{type}/{id}/ai-review?versionId=optional
  │
  ▼
ReviewUseCases.requestAiReview()
  │
  ▼
AIReviewWorker.enqueueAIReview()  (async, fire-and-forget)
  │
  ▼
ReviewPipelineFactory.getPipeline(category)
  ├── TextDocumentPipeline → resolveText() → OpenAI.reviewDocument()
  ├── AudioRecordingPipeline → resolveBytes() → OpenAI.reviewRecording()
  └── ImageReviewPipeline → resolveBytes() → OpenAI.reviewImage()
  │
  ▼
Create Review document in users/{userId}/reviews/
  {
    id: "uuid",
    entityId: "...",
    entityCategory: "document",
    authorRole: "ai",
    authorName: "AI Review Assistant",
    content: { scores: {...}, feedback: "..." }
  }
```

### 13.3 Review Model

```json
{
  "id": "review-uuid",
  "entityId": "entity-uuid",
  "entityCategory": "document",
  "authorRole": "ai",
  "authorId": "ai",
  "authorName": "AI Review Assistant",
  "authorEmail": null,
  "content": {
    "scores": { "clarity": 8, "structure": 7 },
    "feedback": "The document is well-organized..."
  },
  "createdAtTimestamp": 1743033600000
}
```

---

## 13. Sharing

### 13.1 Share Link Flow

```
Owner creates share link:
  POST /api/v1/entities/{type}/{id}/shares
  → Returns: { token: "abc123...", shareUrl: ".../web/shares/abc123..." }

Frontend opens share page:
  /web/shares/{token}

Frontend resolves public share data (no auth required):
  GET /shares/{token}
  → Returns: { entity: { title, content, assetUrl, ... } }

Anyone submits review (no auth required):
  POST /shares/{token}/reviews
  → Creates external review on owner's entity
```

### 13.2 Share Model

```json
{
  "token": "high-entropy-random-string",
  "entityId": "entity-uuid",
  "entityCategory": "document",
  "entityPath": null,
  "ownerId": "user-uuid",
  "expiresAtTimestamp": 1745049600000,
  "revokedAtTimestamp": null
}
```

### 13.3 Share Properties

| Property | Default | Notes |
|----------|---------|-------|
| Expiry | 21 days from creation | Configurable via `DEFAULT_SHARE_EXPIRY_DAYS` |
| Token | 32 random bytes, base64url | ~256 bits of entropy, unguessable |
| Revocable | Yes | Owner can revoke anytime |
| One per entity | Yes | Creating a new share for the same entity replaces the old one |

---

## 14. Uploads

### 14.1 Upload Flow (Direct-to-GCS)

```
Frontend                     AppForge                      GCS
   │                            │                           │
   │ POST /uploads/init          │                           │
   │ { type, entityId,           │                           │
   │   contentType, sizeBytes,   │                           │
   │   assetId }                 │                           │
   │───────────────────────────►│                           │
   │                            │ 1. Check ownership        │
   │                            │ 2. Validate content type  │
   │                            │ 3. Generate uploadId      │
   │                            │ 4. Create pending record  │
   │                            │                           │
   │                            │ 5. Request signed URL     │
   │                            │──────────────────────────►│
   │                            │  ← PUT URL (10 min expiry) │
   │                            │◄──────────────────────────│
   │                            │                           │
   │  { uploadUrl, assetId }    │                           │
   │◄──────────────────────────│                           │
   │                            │                           │
   │ 6. PUT file directly      │                           │
   │──────────────────────────────────────────────────────►│
   │                            │                           │
   │ 7. GET /uploads/access/{assetId}                      │
   │──────────────────────────►│                           │
   │                            │ 8. Generate signed GET URL│
   │                            │──────────────────────────►│
   │  ← 302 redirect           │◄──────────────────────────│
   │◄──────────────────────────│                           │
```

### 14.2 Upload Model

```json
{
  "uploadId": "generated-id",
  "assetId": "client-provided-id",
  "uid": "user-uuid",
  "type": "image",
  "entityId": "entity-uuid",
  "bucket": "appforge-uploads",
  "objectName": "users/uid/entities/eid/uploads/assetId.jpg",
  "contentType": "image/jpeg",
  "sizeBytes": 1048576,
  "status": "pending",
  "createdAtTimestamp": 1743033600000,
  "expiresAtTimestamp": 1743034200000
}
```

### 14.3 Content Type Handling

Supported content types (extensible):

| Category | Allowed Types |
|----------|--------------|
| Images | `image/jpeg`, `image/png`, `image/webp`, `image/heic` |
| Audio | `audio/webm`, `audio/mp4`, `audio/mpeg`, `audio/wav` |
| Video | `video/webm`, `video/mp4` |
| Documents | `application/pdf` |

---

## 17. Infrastructure Layer

### 17.1 Database Interface

The core abstraction. All data access flows through this:

```kotlin
interface Database {
    val name: String
    suspend fun create(collection: String, id: String, data: Map<String, Any?>): Resource<String>
    suspend fun get(collection: String, id: String): Resource<Map<String, Any?>>
    suspend fun update(collection: String, id: String, data: Map<String, Any?>): Resource<Unit>
    suspend fun delete(collection: String, id: String): Resource<Unit>
    suspend fun merge(collection: String, id: String, data: Map<String, Any?>): Resource<Unit>
    suspend fun setIfAbsent(collection: String, id: String, data: Map<String, Any?>): Resource<Boolean>
    suspend fun findFirstByField(collection: String, field: String, value: Any): Resource<Map<String, Any?>?>
    suspend fun query(collection: String, query: DatabaseQuery): Resource<List<Map<String, Any?>>>
    suspend fun <T> transaction(block: suspend TransactionContext.() -> T): Resource<T>
}
```

**Implementations:**
- `ExposedDatabase` — PostgreSQL via Kotlin Exposed ORM
- `SqlDatabase` — Relational DB via JDBC/Exposed (skeleton, ready for implementation)
- `InMemoryDatabase` — Thread-safe map (testing only)
- `` — Wraps primary + cache layer

### 17.2 DatabaseRepository (Typed)

Wraps `Database` with type safety via a `Mapper`:

```kotlin
class DatabaseRepository<DOMAIN>(
    private val database: Database,
    private val collection: String,
    private val mapper: Mapper<DOMAIN, Map<String, Any?>>,
) : Repository<DOMAIN>
```

### 17.3 Resource Sealed Class

```kotlin
sealed class Resource<out T> {
    data class Success<T>(val data: T) : Resource<T>()
    data class Error(val exception: Exception) : Resource<Nothing>()
    object Loading : Resource<Nothing>()
}
```

### 17.4 Mapper Interface

```kotlin
interface Mapper<DOMAIN, DOC> {
    fun toDoc(domain: DOMAIN): DOC
    fun fromDoc(id: String, doc: DOC): DOMAIN
}
```

Every domain model has a corresponding mapper that converts between Kotlin data classes and database `Map<String, Any?>` documents.

### 17.5 MapMapper

A passthrough mapper for untyped data — used when the code needs to read/write database records without a specific domain model (e.g., generic entity metadata).

---

## 16. External Services

### 16.1 Firebase Admin

**Role:** Authentication and session management.

| Operation | SDK Method |
|-----------|-----------|
| Verify ID token | `FirebaseAuth.getInstance().verifyIdToken(token)` |
| Create session cookie | `FirebaseAuth.getInstance().createSessionCookie(idToken, expiresIn)` |
| Verify session cookie | `FirebaseAuth.getInstance().verifySessionCookie(cookie)` |
| Revoke refresh tokens | `FirebaseAuth.getInstance().revokeRefreshTokens(uid)` |

### 16.2 Dodo Payments

**Role:** Payment processing (subscriptions + one-time).

| Operation | Endpoint |
|-----------|----------|
| Create checkout session | `POST /v1/checkouts` |
| Cancel subscription | `POST /v1/subscriptions/{id}/cancel` |
| Webhook events | `POST /billing/webhook/dodo` (incoming) |

**Webhook events handled:**
- `payment.succeeded` → Activate/renew entitlement
- `subscription.canceled` → Mark as CANCEL_PENDING
- `payment.failed` → Mark as PAST_DUE

### 16.3 OpenAI

**Role:** AI-powered reviews.

| Capability | Use |
|-----------|-----|
| GPT-4 text | Document review (clarity, structure, content) |
| Whisper | Audio transcription (recordings) |
| GPT-4 Vision | Image review (bench prep, diagrams) |

**Review prompts are NOT stored in the codebase.** They should be loaded from configuration or provided by the frontend.

### 16.4 ZeptoMail

**Role:** Transactional emails.

| Email Type | Trigger |
|-----------|---------|
| Payment confirmation | Successful payment webhook |
| Share invite | Owner sends share link via email |
| Early access approval | Admin approves waitlist user |

### 16.5 Google Cloud Storage

**Role:** File storage for uploads and AI processing artifacts.

| Operation | Method |
|-----------|--------|
| Upload (signed PUT URL) | `Blob.signUrl(HttpMethod.PUT, duration, ...)` |
| Download (signed GET URL) | `Blob.signUrl(HttpMethod.GET, duration, ...)` |
| Object metadata | Stored in the database `admin/uploads/metadata` |

---

## 17. Error Handling

### 17.1 Status Pages Configuration

```kotlin
// ErrorHandling.kt
install(StatusPages) {
    exception<UnauthorizedException> { call, _ ->
        call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Unauthorized"))
    }
    exception<ForbiddenException> { call, _ ->
        call.respond(HttpStatusCode.Forbidden, ErrorResponse("Forbidden"))
    }
    exception<GoneException> { call, _ ->
        call.respond(HttpStatusCode.Gone, ErrorResponse("Expired or Revoked"))
    }
    exception<Throwable> { call, cause ->
        call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Internal server error"))
    }
}
```

### 17.2 Error Response Format

```json
{
  "message": "Human-readable error description"
}
```

### 17.3 Exception Hierarchy

| Exception | HTTP Status | When Thrown |
|-----------|------------|-------------|
| `UnauthorizedException` | 401 | Auth plugin can't resolve user |
| `ForbiddenException` | 403 | User lacks permission for resource |
| `GoneException` | 410 | Share link expired or revoked |

---

## 18. Logging & Observability

### 18.1 Request ID

Every request gets a unique `X-Request-Id`:
- From incoming header if present
- Generated UUID if absent
- Logged in MDC as `requestId`

### 18.2 Call Logging Format

```
200 GET - /api/v1/reviews in 45ms requestId=abc-123-def
```

### 18.3 Logback Configuration

See `src/main/resources/logback.xml` for the full logging setup. Key points:
- Console appender with structured format
- MDC includes `requestId` and `userId`
- Ktor call logging enabled (except `/uploads/` paths)

### 18.4 Observability Checklist

| Concern | Mechanism |
|---------|-----------|
| Request tracing | `X-Request-Id` header + MDC |
| User attribution | `userId` in MDC (set by AuthMiddleware) |
| Error tracking | `StatusPages` catches all exceptions |
| Performance | Call logging includes duration |
| Audit trail | `admin/audit/billing` for payment events |

---

## 19. Deployment

### 19.1 Docker

```dockerfile
# Build stage
FROM gradle:8-jdk17 AS build
COPY --chown=gradle:gradle . /home/gradle/src
WORKDIR /home/gradle/src
RUN gradle installDist --no-daemon

# Run stage
FROM eclipse-temurin:17-jre-jammy
EXPOSE 8080
WORKDIR /app
COPY --from=build /home/gradle/src/build/install/appforge-backend /app
ENTRYPOINT ["/app/bin/appforge-backend"]
```

### 19.2 Build Commands

```bash
# Build
./gradlew build

# Run locally
./gradlew run

# Create distributable
./gradlew installDist

# Docker
docker build -t appforge-backend .
docker run -p 8080:8080 --env-file .env appforge-backend

# Run migration
./gradlew runBackfillUploadMetadataTimestamps
```

### 19.3 Required Environment Variables

| Variable | Required | Example |
|----------|----------|---------|
| `FIREBASE_PROJECT_ID` | Yes | `appforge-prod` |
| `FIREBASE_SERVICE_ACCOUNT_JSON` | Yes | Path to service account JSON |
| `DODO_PAYMENTS_API_KEY` | Yes | `sk_live_...` |
| `DODO_PAYMENTS_WEBHOOK_KEY` | Yes | `whsec_...` |
| `UPLOADS_BUCKET` | Yes | `appforge-prod-uploads` |
| `INTERNAL_SECRET` | Yes | Random string for system routes |
| `OPENAI_API_KEY` | No (AI reviews) | `sk-...` |
| `ZEPTOMAIL_SEND_TOKEN` | No (emails) | `ZP_...` |
| `PORT` | No (default 8080) | `8080` |
| `NODE_ENV` | No (default `development`) | `production` |
| `CORS_ALLOWED_ORIGINS` | No | `["https://app.example.com"]` |

`CORS` behavior:
- In `NODE_ENV=development`, localhost/loopback and private LAN origins are accepted for local development.
- In non-development environments (including production), only `CORS_ALLOWED_ORIGINS` entries are accepted.

### 19.4 GCP Service Account Permissions

The Firebase service account needs these roles:
- **Cloud Run Admin** — Read/write database
- **Storage Admin** — Sign URLs, read/write GCS objects
- **Firebase Authentication Config Viewer** — Verify tokens, create session cookies

---

## 20. Development Guide

### 20.1 Prerequisites

- JDK 17+
- Gradle 8+ (or use `./gradlew` wrapper)
- Firebase project with Auth enabled
- Google Cloud project with GCS enabled

### 20.2 Quick Start

```bash
# 1. Clone
git clone <repo> && cd backend

# 2. Create .secrets.conf
cat > .secrets.conf << 'EOF'
FIREBASE_PROJECT_ID="your-project"
# ... add secrets
EOF

# 3. Run
./gradlew run

# Server starts on http://localhost:8080
curl http://localhost:8080/health
# → { "status": "ok" }
```

### 20.3 Running Tests

```bash
# All tests
./gradlew test

# Single test
./gradlew test --tests "com.appforge.server.services.billing.BillingFeatureCatalogTest"
```

### 20.4 Code Style

- Kotlin convention: 4-space indent, K&R braces
- Package: `com.appforge.server.*`
- File naming: `PascalCase.kt` for classes, `*Routes.kt`, `*UseCases.kt`, etc.

### 20.5 Adding a New Domain Module

See [Section 21: Extending AppForge](#21-extending-appforge).

---

## 21. Extension Model

### 21.1 Platform Extensions

AppForge supports **pluggable client extensions**. Each extension:
- Identifies itself with an `appId` (used for `X-App-Id` header routing)
- Registers HTTP routes under its own prefix
- Optionally defines SQL tables (created by platform at startup)
- Subscribes to platform hooks (before/after events)

```kotlin
object DentalAppExtension : PlatformExtension {
    override val appId = "example-app"

    override fun registerRoutes(routing: Routing, services: PlatformServices) {
        routing.route("/api/v1/dental") {
            // ... app-specific routes
        }
    }

    override fun defineTables(): List<Table> = listOf(Patients, ToothRecords)

    override fun defineHooks(): List<HookRegistration> = listOf(
        HookRegistration("after-entity-created", ::onEntityCreated)
    )
}
```

Register at startup:
```kotlin
ClientRegistry.registerExtension(DentalAppExtension)
```

### 21.2 Hook Engine

The platform fires hooks at key lifecycle points:

| Event | Type | Blocking? |
|-------|------|-----------|
| `before-entity-created` | Before | Yes — return `{ "allow": false }` to block |
| `after-entity-created` | After | No — fire-and-forget |
| `before-entity-updated` | Before | Yes |
| `after-entity-updated` | After | No |
| `before-review-submitted` | Before | Yes |
| `after-review-submitted` | After | No |
| `before-share-created` | Before | Yes |
| `after-share-created` | After | No |
| `on-entitlement-changed` | After | No |
| `on-user-login` | After | No |

Hooks can be:
1. **In-process** — Kotlin handlers registered by extensions
2. **Webhooks** — HTTP POST to external URLs with HMAC-SHA256 signatures

### 21.3 Platform Services

Extensions receive `PlatformServices`:

```kotlin
data class PlatformServices(
    val database: Database,          // SQL primary database
    val authService: AuthService,     // Firebase Auth
    val hookEngine: HookEngine,       // Fire events, send webhooks
    val extensions: List<PlatformExtension>, // All registered extensions
)
```

### 21.4 Adding a New Entity Type

No backend changes needed. The frontend simply uses a new `type` string:

```bash
POST /api/v1/entities/contract/abc-123/shares
```

### 21.5 Adding a New Billing Feature

1. Add the key to `BillingFeatureCatalog.Keys`
2. Set limits in `BillingFeatureCatalog.defaultForPlan()`
3. Check the feature gate in your route/use case

---

## 21.6 Test Coverage

| Metric | Value |
|--------|-------|
| Total tests | 185 |
| Pass rate | 100% (0 failures, 4 skipped) |
| Test files | 50 |
| Test lines | ~2,100 |
| Main lines | 3,617 |

### Coverage by Module

| Module | Tests | Status |
|--------|-------|--------|
| **Database (CRUD contract)** | 18 | ✅ All pass — covers InMemoryDatabase, full CRUD contract |
| **** | 15 | ✅ All pass — cache hit/miss, write-through, error tolerance |
| **HookEngine** | 11 | ✅ All pass — blocking before-hooks, async after-hooks, fail-open |
| **RequestContext** | 10 | ✅ All pass — auth resolution, appId/teamId extraction, MDC |
| **Billing** | 20+ | ✅ Good — catalog, use cases, Dodo parsing, webhooks |
| **Sharing** | 8+ | ✅ Good — models, repositories, services |
| **Reviews** | 8+ | ✅ Good — service, repository, pipelines, AI worker |
| **Uploads** | 4+ | ✅ Good — init service, metadata repo |
| **Auth** | 4+ | ✅ Good — use cases, early access |
| **Extensions** | Not yet | 🔴 Hook engine tested; extension registration not yet |
| **SqlDatabase (PostgreSQL)** | Not yet | 🔴 CRUD contract tested via InMemoryDatabase; needs PG integration test |
| **Routes** | 12+ | ✅ Good — billing, upload, auth routes |

### Running Tests

```bash
# All tests
./gradlew test

# Single test class
./gradlew test --tests "com.appforge.server.infrastructure.DatabaseContractTest"

# Single test method
./gradlew test --tests "com.appforge.server.extensions.HookEngineTest.before hook can deny operation"
```

---

## 22. Decision Log

### 2024-XX-XX: Generic Entity System

**Decision:** Replace domain-specific collections (`statements`, `recommendations`, `interviews`) with generic `entities/{entityId}` with a `type` discriminator.

**Rationale:** Enables any frontend to define its own entity types without backend code changes. AppForge becomes a platform, not an app.

**Trade-offs:**
- Pro: Zero backend changes for new entity types
- Pro: Single consistent API for all entities
- Con: Frontend must manage entity schema (type-specific metadata)
- Con:  (no type-specific indexes by default)

### 2024-XX-XX: Removed Domain-Specific Prompts

**Decision:** Delete `prompts/` resource directory (statement-review.txt, etc.)

**Rationale:** AI review prompts are business logic, not platform code. They should be loaded from configuration or provided by the frontend.

### 2024-XX-XX: Removed Universities Module

**Decision:** Delete entire universities module (routes, services, models, public stats).

**Rationale:** Product-specific feature, not platform functionality. Any analytics/stats module would be app-specific.

### 2024-XX-XX: EntityCategory as Data Class

**Decision:** Change `enum class EntityCategory` to `data class EntityCategory(val value: String)`.

**Rationale:** Enums require code changes for new types. A string wrapper allows any frontend-defined category while keeping type safety in Kotlin.

### 2024-XX-XX: UploadType as Data Class

**Decision:** Change `enum class UploadType` to `data class UploadType(val value: String)`.

**Rationale:** Same reasoning as EntityCategory — frontends define their own upload types.

### 2024-XX-XX: Single uploadMaxBytes Config

**Decision:** Replace `uploadMaxBytesAnswerRecording` and `uploadMaxBytesBenchPrepImage` with single `uploadMaxBytes`.

**Rationale:** With generic upload types, per-type size limits don't make sense without a type registry. A single global limit is simpler and sufficient for most use cases.

### 2024-XX-XX: RequestContext Over Flat userId

**Decision:** Replace flat `userId: String` attribute with rich `RequestContext(userId, appId, teamId, roles)`.

**Rationale:** A single userId string doesn't support multi-tenant scenarios. By resolving a full context in middleware, every route handler gets app identification, team scoping, and role information without changing signatures. Frontends identify themselves via `X-App-Id` and `X-Team-Id` headers.

**Trade-offs:**
- Pro: Single middleware change unlocks multi-tenancy everywhere
- Pro: Future team/role features need zero route changes
- Pro: MDC logging gets appId and teamId automatically
- Con: Slightly more complex auth resolution (negligible perf impact)

### 2024-XX-XX: Database Abstraction Layer

**Decision:** Replace direct `DatabaseRepository` usage with abstract `Database` interface + `DatabaseRepository<T>` typed wrapper.

**Rationale:** Historical decision from the Firestore era. Today, the platform runs on SQL-backed repositories and keeps the database abstraction for portability/testing:
- SQL as primary database (``)
- 
- In-memory for testing
- Future: Redis cache, DynamoDB, etc.

**Trade-offs:**
- Pro: Database is a configuration choice, not a code commitment
- Pro: Cache layer is transparent — domain code doesn't know the difference
- Pro: SQL skeleton provides clear extension path
- Con: One level of indirection (minimal perf impact)
- Con: Migration effort for existing repositories (done)

### 2024-XX-XX: DbPaths appId-Aware

**Decision:** All `DbPaths.Collections.*` functions accept optional `appId` parameter.

**Rationale:** When `appId` is present, paths shift from `users/{userId}/...` to `apps/{appId}/users/{userId}/...`. This is the foundation for multi-tenant data isolation. The `appId` parameter defaults to `null` for backward compatibility — existing code works unchanged.

**Trade-offs:**
- Pro: Zero migration needed — single-app mode works identically
- Pro: Multi-tenant mode is opt-in per request (via RequestContext)
- Con: Historical Firestore index cost (no longer relevant to the active SQL path)

### 2024-XX-XX: SQL Primary

**Decision:** Flip the database architecture — SQL (PostgreSQL) becomes the primary store for all platform-owned data. Firestore is not used in the active backend path.

**Rationale:** 

**Trade-offs:**
- Pro: ACID transactions for critical data (entitlements, billing)
- Pro: Standard SQL tooling (migrations, backups, queries)
- Pro:  — stale cache is safe
- Con: SQL CRUD implementation still needed (skeleton in place)
- Con: Initial database setup

### 2024-XX-XX: Extension Model with Hooks

**Decision:** Replace hardcoded service modules with a `PlatformExtension` interface. Extensions register routes, define tables, and subscribe to hooks.

**Rationale:** The old model required modifying `ServicesModule.kt` and `RoutesModule.kt` for every new app. Extensions are self-contained: routes + tables + hooks, registered at startup. The hook engine enables before/after validation without coupling.

**Trade-offs:**
- Pro: New apps = new extension module, zero platform changes
- Pro: Hooks enable pluggable business logic (webhooks or in-process)
- Pro: Tables defined per extension, schema auto-created at startup
- Con: Extension isolation is trust-based (no sandbox)
- Con: Hook ordering/retries need careful design

---
