# Core/Public API Integration Coverage Matrix

Manual suite: `./gradlew integrationTest`

| Method | Path | Happy Scenario | Negative Scenario |
|---|---|---|---|
| GET | /health | returns 200 + status payload | n/a |
| POST | /api/v1/session/early-access/check | approved/eligible email returns access result | invalid payload handled with 4xx |
| POST | /api/v1/session/early-access/join | waitlist join accepted | disabled mode returns forbidden |
| GET | /api/v1/session/early-access/status | returns runtime flag | n/a |
| GET | /api/v1/session/me | valid session cookie returns identity | missing session cookie returns unauthorized |
| POST | /api/v1/session/login | valid id token returns session cookie | invalid token returns unauthorized |
| POST | /api/v1/session/logout | valid request clears session cookie | missing app header returns bad request |
| POST | /api/v1/session/password/reset-link | valid email + app header returns success | missing app header returns bad request |
| POST | /api/v1/signup/init | valid token initializes signup state | invalid token returns unauthorized |
| POST | /api/v1/signup/finalize | valid token + answers finalizes onboarding | invalid token or bad payload returns 4xx |
| GET | /api/v1/onboarding/flow | app header returns onboarding flow | missing app header returns bad request |
| GET | /api/v1/users/me | authorized request returns profile | unauthorized request rejected |
| PUT | /api/v1/users/me | authorized update returns success | unauthorized request rejected |
| DELETE | /api/v1/users/me | authorized delete returns success | unauthorized request rejected |
| GET | /api/v1/billing/pricing-cards | returns pricing card list | n/a |
| GET | /api/v1/billing/entitlement | authorized request returns entitlement | unauthorized request rejected |
| POST | /api/v1/billing/checkout | authorized request returns checkout session | unauthorized request rejected |
| POST | /api/v1/billing/subscription/cancel | authorized request returns no-content | unauthorized request rejected |
| POST | /api/v1/billing/webhook/dodo | valid webhook accepted | missing/invalid signature rejected |
| POST | /api/v1/uploads/init | authorized request returns upload init payload | unauthorized request rejected |
| GET | /api/v1/uploads/access/{assetId} | authorized existing asset returns access URL/redirect | missing/expired asset returns not found |
| POST | /api/v1/upload-events/complete | valid shared secret processes event | missing/invalid secret rejected |
| GET | /api/v1/reviews | authorized request returns user reviews | unauthorized request rejected |
| GET | /api/v1/entities/{type}/{id}/reviews | authorized request returns entity reviews | unauthorized request rejected |
| POST | /api/v1/entities/{type}/{id}/ai-review | authorized request enqueues AI review | unauthorized request rejected |
| POST | /api/v1/entities/{type}/{id}/shares | authorized request creates share | unauthorized request rejected |
| GET | /api/v1/entities/{type}/{id}/shares | authorized request lists shares | unauthorized request rejected |
| POST | /api/v1/entities/shares/{token}/revoke | authorized request revokes share | unauthorized request rejected |
| POST | /api/v1/entities/{type}/{id}/shares/{token}/email | authorized request sends email | unauthorized request rejected |
| GET | /shares/{token} | valid public token returns shared resource | expired/revoked token returns gone |
| POST | /shares/{token}/reviews | valid public token accepts review | expired/revoked token returns gone |

Notes:
- Excluded by design: `/api/v1/system/*` and extension routes.
