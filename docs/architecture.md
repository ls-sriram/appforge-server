# AppForge Architecture

## Overview

AppForge is a **multi-tenant application platform** that provides a framework for isolated application spaces.

```
Platform (code only)                    Application Database
┌─────────────────────────────┐         ┌────────────────────────┐
│ Schema (Flyway V2)          │    ──→  │ app_users              │
│ Repositories (Exposed ORM)  │         │ entities (JSONB + RLS) │
│ Auth middleware             │         │ reviews (RLS)          │
│ RLS policies                │         │ shares, entity_shares  │
│                             │         │ billing_entitlements   │
│                             │         │ billing_payments       │
│                             │         │ upload_records         │
└─────────────────────────────┘         └────────────────────────┘
     ▲                                       ▲
     │                                       │
  App A  ────────────────────────────────────┘ (owns this DB)
  App B  ────────────────────────────────────→ (its own DB)
  App C  ────────────────────────────────────→ (its own DB)
```

**The platform provides the schema and code. Each application has its own database.** The database connection IS the security boundary — no `app_id` columns needed, no cross-app data leakage possible.

## Global Isolation Policy (All Features)

- App isolation is enforced primarily by infrastructure/database targeting: each app must run against its own database.
- `X-App-Id` is still required in authenticated requests for request-context validation, auditing, and extension routing.
- For app-owned tables in per-app databases, do not add redundant `app_id` columns unless a feature explicitly spans shared storage.
- If a feature is moved to shared storage in the future, that feature must introduce explicit app scoping at schema + query level.

## Ownership Model

| Level | What it owns | Scope |
|-------|-------------|-------|
| **Platform** | Schema, code, migrations, billing processing, file storage infrastructure | Framework only |
| **Application** | Its own database, users, entities, reviews, shares, billing, config | Fully isolated |
| **User** | Their own data within the app they're using | Per-user RLS |

---

## Database Schema (Per-Application)

Each application gets its own database with these tables. No `app_id` columns — the database connection IS the boundary.

### User Identity

#### `app_users` — User Identity

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `uid` | VARCHAR(255) | PK | Firebase Auth UID (unique within this app) |
| `email` | VARCHAR(255) | NOT NULL | User's email |
| `email_normalized` | VARCHAR(255) | UNIQUE | Lowercase, trimmed |
| `display_name` | VARCHAR(255) | | User's chosen display name |
| `created_at` | TIMESTAMPTZ | DEFAULT NOW() | |
| `last_login_at` | TIMESTAMPTZ | | |

### Billing

#### `billing_entitlements` — Per-User Plan/Subscription

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `user_id` | VARCHAR(255) | PK, FK → app_users | |
| `plan` | VARCHAR(50) | DEFAULT 'free' | `free`, `trial`, `pro` |
| `status` | VARCHAR(50) | DEFAULT 'active' | `active`, `trialing`, `past_due`, `canceled` |
| `expires_at` | TIMESTAMPTZ | | When the plan expires |
| `started_at` | TIMESTAMPTZ | DEFAULT NOW() | |
| `features` | JSONB | DEFAULT '{}' | Feature limits per feature |
| `entitlement_source` | VARCHAR(50) | DEFAULT 'manual' | `trial`, `dodo_payments`, `manual` |
| `external_customer_id` | VARCHAR(255) | | Payment provider customer ID |
| `external_reference_id` | VARCHAR(255) | | Payment provider subscription/order ID |
| `billing_type` | VARCHAR(50) | | `subscription`, `one_time` |
| `last_payment_amount_cents` | BIGINT | | |
| `last_payment_currency` | VARCHAR(10) | | |
| `created_at` | TIMESTAMPTZ | DEFAULT NOW() | |
| `updated_at` | TIMESTAMPTZ | DEFAULT NOW() | |

#### `billing_payments` — Per-User Payment History

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | VARCHAR(255) | PK | |
| `user_id` | VARCHAR(255) | NOT NULL, FK → app_users | |
| `date` | TIMESTAMPTZ | NOT NULL | |
| `amount_cents` | BIGINT | NOT NULL | |
| `currency` | VARCHAR(10) | NOT NULL | ISO 4217 |
| `plan_id` | VARCHAR(100) | NOT NULL | |
| `email_sent_at` | TIMESTAMPTZ | | |
| `created_at` | TIMESTAMPTZ | DEFAULT NOW() | |

### User-Scoped Tables (with RLS)

#### `reviews` — Per-User Reviews (RLS)

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | VARCHAR(255) | PK | UUID v4 |
| `owner_uid` | VARCHAR(255) | PK, FK → app_users | User who owns this review |
| `entity_id` | VARCHAR(255) | NOT NULL | |
| `entity_category` | VARCHAR(100) | NOT NULL | |
| `entity_type` | VARCHAR(100) | NOT NULL | |
| `author_role` | VARCHAR(50) | NOT NULL | `ai`, `external`, `self` |
| `author_id` | VARCHAR(255) | | |
| `author_name` | VARCHAR(255) | | |
| `author_email` | VARCHAR(255) | | |
| `content` | JSONB | NOT NULL DEFAULT '{}' | |
| `created_at` | TIMESTAMPTZ | DEFAULT NOW() | |

**RLS Policy:** `owner_uid = current_setting('app.current_user_id')`

#### `entities` — Per-User Flexible Data (RLS, JSONB)

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | VARCHAR(255) | PK | Frontend-defined entity ID |
| `owner_uid` | VARCHAR(255) | PK, FK → app_users | |
| `category` | VARCHAR(100) | NOT NULL | Frontend-defined category |
| `data` | JSONB | NOT NULL DEFAULT '{}' | **Full frontend-defined schema** |
| `created_at` | TIMESTAMPTZ | DEFAULT NOW() | |
| `updated_at` | TIMESTAMPTZ | DEFAULT NOW() | |

**RLS Policy:** `owner_uid = current_setting('app.current_user_id')`

**GIN index** on `data` enables efficient JSONB queries.

#### `entity_shares` — Canonical Entity Shares

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | VARCHAR(255) | PK | Current public share token id |
| `owner_uid` | VARCHAR(255) | NOT NULL, FK → app_users | Owner of shared entity |
| `entity_type` | VARCHAR(100) | NOT NULL | Entity category/type |
| `entity_id` | VARCHAR(255) | NOT NULL | Shared entity id |
| `access_mode` | VARCHAR(50) | CHECK = `public_link` | Public-link mode only (current) |
| `token_hash` | VARCHAR(128) | UNIQUE | SHA-256 of token for lookup |
| `expires_at` | TIMESTAMPTZ | NULLABLE | Null means no expiry |
| `created_by` | VARCHAR(255) | NOT NULL | Creator uid |
| `revoked_at` | TIMESTAMPTZ | NULLABLE | Revoked timestamp |
| `revoked_by` | VARCHAR(255) | NULLABLE | Revoker uid |
| `created_at` | TIMESTAMPTZ | DEFAULT NOW() | |

### Shared Tables (No RLS)

`entity_shares` above is the canonical share storage for public links.

#### `upload_records` — GCS Upload Metadata

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `upload_id` | VARCHAR(255) | PK | |
| `uid` | VARCHAR(255) | NOT NULL, FK → app_users | |
| `type` | VARCHAR(100) | NOT NULL | Frontend-defined |
| `entity_id` | VARCHAR(255) | NOT NULL | |
| `bucket` | VARCHAR(255) | NOT NULL | GCS bucket |
| `object_name` | VARCHAR(1000) | NOT NULL | GCS object path |
| `content_type` | VARCHAR(255) | NOT NULL | MIME type |
| `size_bytes` | BIGINT | NOT NULL | |
| `status` | VARCHAR(50) | DEFAULT 'pending' | |
| `created_at` | TIMESTAMPTZ | DEFAULT NOW() | |
| `expires_at` | TIMESTAMPTZ | | |

---

## Platform Database (2 tables)

Only two tables live in the platform database. Everything else is per-app.

| Table | Purpose |
|-------|---------|
| `platform_applications` | App registry — which apps exist, their config, status |
| `platform_api_calls` | Cross-app request analytics — usage, latency, errors |

---

## Per-Application Database (12 tables)

Everything else lives in each app's own database:

| Table | Scope | RLS |
|-------|-------|-----|
| `app_users` | Per-app user identity | No |
| `profiles` | App-level user profiles | No |
| `billing_entitlements` | Per-user plan | No |
| `billing_payments` | Per-user payment history | No |
| `billing_audit_records` | App-level webhook audit | No |
| `reviews` | Per-user reviews | ✅ |
| `entities` | Per-user flexible data (JSONB) | ✅ |
| `entity_shares` | Canonical entity share links | No |
| `early_access_entries` | App waitlist | No |
| `upload_records` | GCS upload metadata | No |

---

## Row-Level Security (RLS)

Two tables have RLS enabled: `reviews`, `entities`.

### How RLS Works

```sql
-- Set context at start of each request (via middleware)
SELECT set_config('app.current_user_id', 'user-abc123', false);

-- RLS policy automatically filters ALL queries
CREATE POLICY entities_user_isolation ON entities
    USING (owner_uid = current_setting('app.current_user_id', true));
```

### Security Guarantee

- **User A1** can only see their own entities/reviews/shares within this app
- **Database-level enforcement** — no application code can bypass this
- **`SECURITY DEFINER`** on `set_current_user_id()` — only the platform can set the context
- **Cross-app isolation** is physical (separate databases), not just RLS

### RLS Context Setup

The middleware extracts the authenticated user's UID from the session cookie and calls:

```kotlin
exec("SELECT set_config('app.current_user_id', '$uid', false)")
```

This happens **before** any database operation in the request pipeline.

---

## Migration Status

### Current State (as of 2025-04-08)

| Component | Status | Notes |
|-----------|--------|-------|
| **V2 Flyway Migration** | ✅ Ready | `V2__normalize_schema.sql` creates all 13 tables + RLS + data migration |
| **Exposed Table Definitions** | ✅ Ready | 12 table objects in `domain/tables/` |
| **Existing Repositories** | ⚠️ Using old system | Still use `Repository<Map<String, Any?>>` via `platform_records` |
| **RLS Middleware** | ❌ Not built yet | Needs to set `app.current_app_id` + `app.current_user_id` per request |
| **New Repositories** | ❌ Not built yet | Will use Exposed ORM with typed entities |

### Migration Strategy

1. **V2 migration runs on first boot** — creates new tables alongside `platform_records`
2. **Old repositories continue working** — they read/write `platform_records` (backward compatible)
3. **New repositories added alongside old ones** — each service migrates independently
4. **RLS middleware wired into request pipeline** — sets context before DB access
5. **`platform_records` dropped in V3** — after all services migrated

### How to Migrate a Service

```kotlin
// OLD (current — works, uses platform_records JSONB):
class ReviewRepository(factory: RepositoryFactory) {
    private val ds: Repository<Review> = factory.create(
        collectionName = DbPaths.Collections.reviews(userId),
        mapper = ReviewMapper
    )
}

// NEW (future — uses proper table with RLS):
class ReviewRepositoryV2(db: RelationalDatabase) {
    fun getByEntity(appId: String, userId: String, entityId: String): List<Review> {
        return db.execute(appId, userId) {
            Reviews.select { (Reviews.appId eq appId) and (Reviews.ownerUid eq userId)
                           and (Reviews.entityId eq entityId) }
                .map { row -> /* map to Review */ }
        }
    }
}
```

---

## Authentication Flow

```
┌──────────────┐         ┌──────────────┐         ┌──────────────┐
│   Frontend   │         │   Backend    │         │   Firebase   │
│   (Expo)     │         │   (Ktor)     │         │   (Auth)     │
└──────┬───────┘         └──────┬───────┘         └──────┬───────┘
       │                        │                        │
       │ 1. signInWithEmailAndPassword()                 │
       │─────────────────────────────────────────────────→│
       │                        │                        │
       │ 2. Firebase ID Token                           │
       │←────────────────────────────────────────────────│
       │                        │                        │
       │ 3. POST /session/login {idToken}                │
       │───────────────────────→│                        │
       │                        │ 4. verifyIdToken()     │
       │                        │───────────────────────→│
       │                        │ 5. UID + claims        │
       │                        │←───────────────────────│
       │                        │                        │
       │                        │ 6. createSessionCookie()│
       │                        │───────────────────────→│
       │                        │ 7. Session cookie      │
       │                        │←───────────────────────│
       │                        │                        │
       │ 8. Set-HttpOnly-Cookie │                        │
       │←───────────────────────│                        │
       │                        │                        │
       │ 9. POST /signup/init {idToken}                  │
       │───────────────────────→│                        │
       │                        │ 10. Create trial        │
       │                        │     entitlement         │
       │                        │                        │
       │ 11. {success: true}    │                        │
       │←───────────────────────│                        │
```

### Session Verification (Subsequent Requests)

```
Request → RlsMiddleware extracts session cookie
        → AuthService.verifySessionCookie()
        → Sets app.current_app_id (from X-App-Id header)
        → Sets app.current_user_id (from cookie)
        → All DB queries automatically scoped by RLS
```

---

## Data Flow

```
Frontend Request
    │
    ├─ Cookie: appforge-session=<signed>
    │
    ▼
Middleware
    ├─ Verifies session cookie → uid = "user-abc123"
    ├─ Sets RLS context:
    │   SELECT set_config('app.current_user_id', 'user-abc123', false)
    │
    ▼
Route Handler
    │
    ▼
Repository (e.g., ReviewRepository)
    │
    ▼
SQL Query (automatically filtered by RLS):
    SELECT * FROM reviews
    WHERE owner_uid = 'user-abc123'    -- set by RLS
```

**The repository code never explicitly filters by user_id.** PostgreSQL's RLS policies enforce it at the database level.

---

## Frontend Architecture

### Component Hierarchy (Responsibility-Based)

```
Primitives          Molecules             Shells               Screens
─────────────       ──────────────        ─────────────        ─────────────
Text                SearchBar             WorkspaceShell       / (login)
Input               MetricRow             NavigationShell      /register
Button              NavItem               HeaderShell          /onboarding
Surface             StatPill              PageShell            /dashboard
Stack               QuickActionCard       ContentShell         /settings
Icon                FeatureCard                                /settings/profile
Avatar              SectionHeader
Skeleton            SettingsRow
Badge               LoginField
Tag                 RegisterField
Toggle              AuthHeader
ProgressBar         SubmitButton
EmptyState          LinkButton
                    ChartLegend
```

### Screens

| Route | Description | Auth Required |
|-------|-------------|---------------|
| `/` | Login (email/password via Firebase Auth) | No |
| `/register` | Registration + trial entitlement | No |
| `/onboarding` | 4-step feature carousel | Yes (session gate) |
| `/dashboard` | Full-width sections: metrics, usage stats, chart, quick actions, entities table, activity feed | Yes |
| `/settings` | Profile, Plan & Usage, Preferences (toggles), Security, Support, Danger Zone | Yes |
| `/settings/profile` | Profile editor (name edit, readonly email, UID display) | Yes |

### Navigation Gates

```
_layout.tsx → Gate component
    │
    ├─ if (skipAuth) → authenticated
    ├─ GET /session/me
    │   ├─ 401/unauthenticated → redirect to /
    │   └─ 200/authenticated
    │       ├─ hasCompletedOnboarding() → stay on current page
    │       └─ !hasCompletedOnboarding() → redirect to /onboarding
```

### API Layer

| Service | Method | Endpoint | Purpose |
|---------|--------|----------|---------|
| `BackendAuthService` | POST | `/session/login` | Exchange Firebase ID token for session cookie |
| `BackendAuthService` | POST | `/signup/init` | Initialize trial entitlement |
| `BackendAuthService` | POST | `/session/logout` | Revoke session |
| `BackendAuthService` | GET | `/session/me` | Check current session |
| `UserProfileService` | GET | `/users/me` | Get full profile, plan, usage |

### Design System

- **Theme factory**: `createTheme()` generates full design system from brand colors
- **Dark mode**: Hardcoded dark theme (violet/emerald/amber/rose/sky palette)
- **Layout tokens**: `section`, `grid`, `onboarding`, `anim`, `elevation`, `iconSizes`
- **Component patterns**: Every component receives theme via `useTheme()` hook

---

## Security Model

| Layer | What it protects | How |
|-------|-----------------|-----|
| **Database-per-app** | Cross-app isolation | Physical separation — each app has its own database |
| **Firebase Auth** | Identity verification | ID tokens verified server-side |
| **Session cookies** | Request authentication | HttpOnly, Secure, SameSite cookies |
| **RLS policies** | User data isolation | `owner_uid` enforced at DB level |
| **RLS middleware** | Context setting | Extracts uid from request, sets PG session var |
| **Early access gate** | Platform access control | Email-based approval list (optional) |

---

## Deployment

### Backend

- **Runtime:** Ktor on JVM 17
- **Database:** PostgreSQL 16 (Docker locally, Cloud SQL in production)
- **Migrations:** Flyway (V1, V2, ...)
- **Config:** App-scoped config-manager exports for dev, Cloud Run secrets for prod

### Frontend

- **Runtime:** Expo SDK 54, React Native 0.81.5
- **Routing:** expo-router v6 (file-based)
- **Platform:** iOS, Android, Web (react-native-web)
- **Auth:** Firebase Auth (client SDK) → backend session cookies

---

## Key Design Decisions

### 1. Database-per-app, not shared with RLS

Each application gets its own database. The database connection IS the security boundary. No `app_id` columns, no cross-app queries possible. Cross-app isolation is physical, not logical.

### 2. JSONB for entities, typed columns for everything else

Entities keep a JSONB `data` column because frontend applications define their own schemas. Everything else (billing, reviews, shares) gets proper typed columns because the platform owns those schemas.

### 3. Composite primary keys for user-scoped tables

Tables like `reviews(id, owner_uid)` use composite PKs so that RLS can enforce isolation at the index level.

### 4. RLS over application-level filtering

Row-level security ensures **no application code can bypass data isolation**. Even if a repository has a bug, PostgreSQL enforces the `owner_uid` boundary.

### 5. Platform database = 2 tables only

Only `applications` (registry) and `api_calls` (analytics) are platform-level. Everything else — profiles, early access, billing audit, payments — lives in each app's own database. The app owns its data; the platform just tracks what apps exist and how they're performing.

---

## Changelog

| Date | Change |
|------|--------|
| 2025-04-08 | Firestore completely purged. PostgreSQL only. |
| 2025-04-08 | Frontend redesign: onboarding, full-width dashboard, settings sections |
| 2025-04-08 | V2 normalized relational schema with RLS |
| 2025-04-08 | Multi-tenant application isolation in V2 schema |
