# AI Pro — Phase 1 Spec: Pro Plumbing &amp; Hetzner Backend

> **Parent plan:** [`plans/ai-pro-plan.md`](ai-pro-plan.md)
> **Status:** Ready for implementation
> **Prerequisites:** None (starts fresh on the `feat/ai-pro-plan` branch)
> **Ships as:** Android v1.6.0 + backend v0.1.0
> **User-visible AI:** **None.** This phase is plumbing only. UI ships a "PesaTrack Pro" settings entry with Play Billing wired up; no AI features are enabled.

## Table of Contents

1. [Goal &amp; Non-Goals](#1-goal--non-goals)
2. [Deliverables Checklist](#2-deliverables-checklist)
3. [Android Client Changes](#3-android-client-changes)
4. [Backend (`pesatrack-api.jmumo.com`)](#4-backend-pesatrack-apijmumocom)
5. [Data Digest Builder](#5-data-digest-builder)
6. [Entitlement &amp; Auth Flow](#6-entitlement--auth-flow)
7. [Privacy Policy &amp; Play Console Updates](#7-privacy-policy--play-console-updates)
8. [Test Plan](#8-test-plan)
9. [Rollout &amp; Feature Flag](#9-rollout--feature-flag)
10. [Definition of Done](#10-definition-of-done)

---

## 1. Goal &amp; Non-Goals

### Goal

Ship the invisible foundation for the Pro AI tier:

1. Users can subscribe to PesaTrack Pro via Play Billing (monthly + annual).
2. The Android app can verify Pro entitlement locally and remotely.
3. The Android app can build a privacy-safe `DataDigest` from local Room data.
4. The Hetzner backend is deployed, healthy, and responds to `/health` + `/billing/verify`.
5. The backend has a stub `/ai/echo` endpoint (returns the digest it received, no LLM call) for end-to-end wiring tests.
6. The full pipeline (client → HTTPS → Hetzner → OpenAI SDK loaded but unused) is proven with real Play test purchases.

### Non-goals

- **No user-visible AI feature.** Phase 2 ships the first one (Coach Insights).
- **No LLM calls in production.** The OpenAI SDK is wired but the only endpoint that invokes it is `/ai/echo/self-test` (protected, non-production).
- **No Room migration.** All Phase 1 state lives in DataStore (Pro entitlement) or is stateless (ephemeral digest IDs). Room stays at v18.
- **No AI settings toggles** for individual features. Pro is a single on/off entitlement.

---

## 2. Deliverables Checklist

### Android (client)

- [ ] New Gradle module dependency: `com.android.billingclient:billing-ktx:7.x` (latest stable at build time)
- [ ] `INTERNET` and `ACCESS_NETWORK_STATE` permissions — **already declared** in v1.5.2 for Firebase Analytics; verify remain present, no additional permissions.
- [ ] Network Security Config addition: whitelist `pesatrack-api.jmumo.com` (TLS-only, no cleartext).
- [ ] New package `com.pesatrack.services.pro/` containing:
    - `ProEntitlementRepository.kt` — reads/writes entitlement state in DataStore
    - `PlayBillingClient.kt` — Hilt-provided wrapper around `BillingClient`
    - `ProPurchaseFlow.kt` — orchestrates launchBillingFlow → verify → store
- [ ] New package `com.pesatrack.services.ai/` containing:
    - `DataDigestBuilder.kt` — pure function `(period: YearMonth) -> DataDigest`
    - `PesaTrackAiClient.kt` — Retrofit interface for backend calls
    - `AiFallbackPolicy.kt` — decides when to render template summary instead
- [ ] New screen: `PesaTrackProScreen.kt` in `presentation/screens/pro/` — feature list, monthly/annual price toggle, "Start free trial" button, "Restore purchase" link
- [ ] New screen: `ProUpsellSheet.kt` — bottom sheet shown only from the *one* discoverable Pro CTA (Settings → PesaTrack Pro)
- [ ] Nav: new `Screen.PesaTrackPro` sealed-class entry; NavGraph route
- [ ] Settings screen: add "PesaTrack Pro" row (with Pro/Free badge)
- [ ] Feature flag `pro_ai_enabled` in `AppPreferences` (default `false`, unlocked when entitled) — controls whether any AI-calling code path is even reachable
- [ ] Telemetry events (allow-listed per v1.5.2 pattern): `pro_screen_viewed`, `pro_purchase_started`, `pro_purchase_completed`, `pro_purchase_failed`, `pro_restore_tapped`, `pro_entitlement_gained`, `pro_entitlement_lost`

### Backend (`backend/` folder — reactivated)

- [ ] Rip out existing Daraja/STK Push code (never consumed by shipped app per AGENTS.md). Move to a `backend/legacy/` folder in a *separate commit* for reversibility.
- [ ] `Dockerfile` targeting Node 20 (Alpine)
- [ ] `docker-compose.yml` naming project `pesatrack-api`, joining external `web` network (per [`C:\Eng\StockUp\deploy\README.md`](../../StockUp/deploy/README.md) §4)
- [ ] `.env.example` documenting required vars
- [ ] Express app with middleware: request ID, structured logging (no PII), CORS locked to `pesatrack.jmumo.com` + app package ID, rate limit
- [ ] Routes:
    - `GET  /health` — liveness, returns `{status: "ok", version, uptime}`
    - `POST /billing/verify` — Play Billing purchase-token verification
    - `GET  /billing/entitlement` — current Pro state
    - `POST /ai/echo` — round-trips a digest (for wiring test; disabled in prod via env flag)
- [ ] Prisma schema: `Entitlement` table (see §6)
- [ ] `AiProvider` interface (Node) with `OpenAiProvider` impl (unused in Phase 1 endpoints)
- [ ] Google Play Developer API client for token verification (service account JSON)
- [ ] Hetzner deploy: DNS A record `pesatrack-api.jmumo.com` → `2.28.75.21`, Caddy block in `~/proxy/Caddyfile`, `~/apps/pesatrack-api/` directory

### Documentation

- [ ] Update `_docs/implementation-status.md` per AGENTS.md auto-update rule
- [ ] Update `docs/privacy-policy.html` §4/§5 with the AI privacy contract from `ai-pro-plan.md` §7
- [ ] Update Play Console Data Safety declaration (see §7)
- [ ] Update `plans/website-full-plan.md` sync trigger table entry once website copy is drafted (separate PR)
- [ ] Update `backend/README.md` — replace Daraja intro with new AI-proxy scope

---

## 3. Android Client Changes

### 3.1 Package layout (additions only)

```
com.pesatrack/
├── services/
│   ├── pro/                     ← NEW
│   │   ├── ProEntitlementRepository.kt
│   │   ├── PlayBillingClient.kt
│   │   ├── ProPurchaseFlow.kt
│   │   └── ProProduct.kt        ← enum { MONTHLY, ANNUAL }
│   └── ai/                      ← NEW (Phase 1 lays the skeleton)
│       ├── DataDigestBuilder.kt
│       ├── DataDigest.kt        ← data class matching backend schema
│       ├── PesaTrackAiClient.kt ← Retrofit interface
│       ├── AiHttpModule.kt      ← Hilt module: OkHttp + Retrofit
│       └── AiFallbackPolicy.kt
├── presentation/
│   ├── screens/
│   │   └── pro/                 ← NEW
│   │       ├── PesaTrackProScreen.kt
│   │       ├── PesaTrackProViewModel.kt
│   │       └── PesaTrackProUiState.kt
│   └── components/
│       └── ProUpsellSheet.kt    ← NEW
```

### 3.2 DataStore additions

Extend `AppPreferences` with a `pro` block:

```kotlin
data class ProState(
    val isEntitled: Boolean = false,
    val tier: ProProduct? = null,      // MONTHLY | ANNUAL | null
    val purchaseToken: String? = null, // Play Billing purchase token
    val expiresAtEpochMs: Long? = null,
    val lastVerifiedAtEpochMs: Long? = null,
    val autoRenewing: Boolean = false
)

// New keys in AppPreferences DataStore (JSON-encoded ProState under one key)
val KEY_PRO_STATE = stringPreferencesKey("pro_state_json_v1")
```

**Why JSON-encoded instead of one key per field:** atomic write, easy schema evolution (`_v1` → `_v2`), no partial-state race conditions.

### 3.3 Network Security Config

Update `res/xml/network_security_config.xml` to whitelist the API host (already TLS-only from v1.5.2 Firebase setup):

```xml
<domain-config cleartextTrafficPermitted="false">
    <domain includeSubdomains="false">pesatrack-api.jmumo.com</domain>
</domain-config>
```

### 3.4 OkHttp + Retrofit setup

Standard Retrofit + Moshi. One custom interceptor:

- `ProAuthInterceptor` — attaches `Authorization: Bearer <purchase_token>` header on every `/ai/*` call. Reads from `ProEntitlementRepository`. Short-circuits with HTTP 401 (client-side synthesized) if not entitled — never dispatches the request.

Timeout: 30s connect, 60s read (chat streaming will need SSE handling in Phase 4 — out of scope here).

### 3.5 Feature flag `pro_ai_enabled`

Add to `AppPreferences`:

```kotlin
val KEY_PRO_AI_ENABLED = booleanPreferencesKey("pro_ai_enabled")
```

**Default `false`.** Set to `true` only when `ProEntitlementRepository.isEntitled.value == true` AND remote server-verification returned success within the last 24h. Any AI-calling code path guards on `pro_ai_enabled` — if false, silent no-op.

This lets us ship Phase 1 to production with the flag off, activate it for closed-testing entitled users, and roll to production once verified.

### 3.6 New telemetry events

Extend `TelemetryEvents.kt` allow-list. All new event names/params below become `const val` in that file. Follow the v1.5.2 pattern.

| Event | Params |
|---|---|
| `pro_screen_viewed` | `source` (`settings` \| `deeplink`) |
| `pro_purchase_started` | `product_id` (`monthly` \| `annual`) |
| `pro_purchase_completed` | `product_id`, `is_trial` (`true` \| `false`) |
| `pro_purchase_failed` | `product_id`, `reason` (bucketed: `user_cancel` \| `network` \| `billing_error` \| `verify_failed`) |
| `pro_restore_tapped` | (none) |
| `pro_entitlement_gained` | `product_id` |
| `pro_entitlement_lost` | `reason` (bucketed: `expired` \| `refunded` \| `revoked`) |

Zero amounts, zero merchant strings, zero SMS content — matches the v1.5.2 privacy contract.

---

## 4. Backend (`pesatrack-api.jmumo.com`)

### 4.1 Directory layout

```
backend/
├── Dockerfile
├── docker-compose.yml       ← names project 'pesatrack-api', joins 'web' network
├── package.json
├── prisma/
│   ├── schema.prisma
│   └── migrations/
├── src/
│   ├── index.js             ← Express bootstrap
│   ├── config/              ← env loader, provider config
│   ├── middleware/
│   │   ├── requestId.js
│   │   ├── logger.js        ← PII-scrubbing logger
│   │   ├── rateLimit.js
│   │   └── entitlement.js   ← reads Authorization, checks Entitlement table
│   ├── routes/
│   │   ├── health.js
│   │   ├── billing.js
│   │   └── ai.js            ← Phase 1: just /ai/echo
│   ├── services/
│   │   ├── playBilling.js   ← Google Play Developer API client
│   │   └── ai/
│   │       ├── AiProvider.js       ← interface
│   │       ├── OpenAiProvider.js   ← wraps openai SDK, strict Structured Outputs
│   │       ├── GroqProvider.js     ← TODO stub
│   │       └── GeminiProvider.js   ← TODO stub
│   └── legacy/              ← ripped-out Daraja code (separate commit before this one)
└── README.md
```

### 4.2 Docker Compose

Following the StockUp multi-app pattern ([`C:\Eng\StockUp\deploy\README.md`](../../StockUp/deploy/README.md) §4):

```yaml
name: pesatrack-api
services:
  web:
    build: .
    restart: unless-stopped
    env_file: .env
    depends_on: [db]
    networks: [default, web]
    healthcheck:
      test: ["CMD", "wget", "-qO-", "http://localhost:3000/health"]
      interval: 30s
      timeout: 5s
      retries: 3

  db:
    image: postgres:16-alpine
    restart: unless-stopped
    environment:
      POSTGRES_DB: pesatrack_api
      POSTGRES_USER: pesatrack
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD}
    volumes:
      - db_data:/var/lib/postgresql/data

volumes:
  db_data:

networks:
  default:
  web:
    external: true
```

### 4.3 Caddyfile block (on the host, in `~/proxy/Caddyfile`)

```caddy
pesatrack-api.jmumo.com {
    encode zstd gzip
    reverse_proxy pesatrack-api-web-1:3000
}
```

### 4.4 Environment variables

`.env.example` (never commit real `.env`):

```
NODE_ENV=production
PORT=3000
DATABASE_URL=postgres://pesatrack:${POSTGRES_PASSWORD}@db:5432/pesatrack_api

POSTGRES_PASSWORD=                    # openssl rand -hex 24

# OpenAI
OPENAI_API_KEY=
OPENAI_MODEL=gpt-4.1-mini             # or current mini workhorse — pinned, never -latest
OPENAI_MAX_TOKENS_OUT=800

# Google Play
GOOGLE_PLAY_PACKAGE_NAME=com.pesatrack
GOOGLE_PLAY_SERVICE_ACCOUNT_JSON=/run/secrets/play_service_account.json

# CORS
ALLOWED_ORIGIN_APP=com.pesatrack       # app package (added to Access-Control-Allow-Origin via custom check)
ALLOWED_ORIGIN_WEB=https://pesatrack.jmumo.com

# Feature flags
ENABLE_AI_ENDPOINTS=false             # OFF in Phase 1; flip to true in Phase 2 prod cutover
ENABLE_AI_ECHO=true                    # /ai/echo enabled for wiring tests
```

Secrets deployed as Docker secrets (Play service account JSON) or as `.env` values (OpenAI key) per the host convention.

### 4.5 Prisma schema (Phase 1)

```prisma
model Entitlement {
  id                    String   @id @default(cuid())
  // Play Billing purchase token — primary key from user's perspective
  purchaseToken         String   @unique
  productId             String   // "pesatrack_pro_monthly" | "pesatrack_pro_annual"
  packageName           String
  // Derived from Play Developer API response
  expiryTimeMillis      BigInt
  autoRenewing          Boolean
  paymentState          Int      // 0 = pending, 1 = received, 2 = free trial, 3 = pending deferred
  linkedPurchaseToken   String?  // upgrade/downgrade chain
  // Ops
  lastVerifiedAt        DateTime @default(now())
  createdAt             DateTime @default(now())
  updatedAt             DateTime @updatedAt

  @@index([expiryTimeMillis])
}

model AuditEvent {
  id          String   @id @default(cuid())
  type        String   // "billing.verified" | "billing.expired" | "ai.endpoint_called" | "ai.fallback"
  purchaseTokenHash String  // sha256 of purchase token — never raw
  metadata    Json     // bounded, no PII
  createdAt   DateTime @default(now())

  @@index([type, createdAt])
}
```

**No `User` table.** The purchase token *is* the identity. No PII stored anywhere in the DB.

### 4.6 Endpoints (Phase 1)

#### `GET /health`

Returns `200 {"status":"ok","version":"0.1.0","uptime":123456}`. No auth. Used by Caddy healthcheck and monitoring.

#### `POST /billing/verify`

Request:
```jsonc
{
  "purchaseToken": "abcd1234...",
  "productId": "pesatrack_pro_monthly"
}
```

Server:
1. Call Google Play Developer API `purchases.subscriptionsv2.get(package, token)`.
2. Reject if `packageName != com.pesatrack`.
3. Upsert into `Entitlement` table.
4. Return normalized entitlement:

```jsonc
{
  "entitled": true,
  "productId": "pesatrack_pro_monthly",
  "expiresAtEpochMs": 1731234567890,
  "autoRenewing": true,
  "isTrialPeriod": false
}
```

Errors: 400 (malformed), 401 (Play says invalid), 429 (rate limit), 5xx (Google API down — client keeps last known entitlement).

#### `GET /billing/entitlement`

Auth: `Authorization: Bearer <purchaseToken>` header.

Server:
1. Look up `Entitlement` by token.
2. If `expiryTimeMillis - now < 6h`, force revalidation against Google.
3. Return same shape as `/billing/verify` response.

#### `POST /ai/echo` (Phase 1 only, feature-flagged)

Auth required. Request body:
```jsonc
{ "digest": { /* DataDigest JSON */ } }
```

Response:
```jsonc
{
  "received_at": "2026-09-12T10:00:00Z",
  "digest_field_count": 12,
  "period": "2026-09"
}
```

**Never invokes an LLM.** Just proves the pipeline: entitlement check → JSON body → response. Disabled in prod via `ENABLE_AI_ECHO=false` after Phase 2 ships.

### 4.7 Rate limiting (Phase 1)

`express-rate-limit` with per-purchase-token key:

- `/billing/verify`: 20/hr per token
- `/billing/entitlement`: 60/hr per token
- `/ai/echo`: 100/day per token

Phase 2 adds the per-endpoint caps from `ai-pro-plan.md` §8.5.

### 4.8 Logging &amp; observability

- Structured JSON logs to stdout (Docker captures)
- **Never** log purchase tokens raw — always `sha256(token).slice(0,16)` as `token_hash`
- **Never** log the digest body — only field count + period
- Request ID header (`X-Request-Id`) propagated through
- Basic Prometheus metrics at `/metrics` (internal only, not exposed via Caddy): request count, latency histogram, entitlement cache hit rate

---

## 5. Data Digest Builder

The single most important piece of client code for privacy compliance. Lives at `com.pesatrack.services.ai.DataDigestBuilder`.

### 5.1 Signature

```kotlin
@Singleton
class DataDigestBuilder @Inject constructor(
    private val expenseRepository: ExpenseRepository,
    private val budgetRepository: BudgetRepository,
    private val recurringExpenseService: RecurringExpenseService,
    private val incomeTransactionRepository: IncomeTransactionRepository,
    private val monthStartDayProvider: MonthStartDayProvider,
) {
    suspend fun buildForCurrentPeriod(): DataDigest
    suspend fun buildFor(period: YearMonth): DataDigest
}
```

### 5.2 Recipient anonymization (critical)

The digest contains `top_recipients_this_period[].id: "r1"`. These IDs are **ephemeral per request** — assigned at digest-build time by ranking recipients by absolute spend and calling them `r1..rN`. The client keeps an in-memory `Map<String, String>` (id → real recipient key) *for the duration of the request only*, used to re-hydrate names in the LLM response before rendering. **This map is never persisted.** **This map is never sent to the server.**

Enforcement:
- The map is a local `val` inside the `ViewModel.load()` scope; goes out of scope when the response renders
- Unit test asserts no `Repository` reference to this map from any persistence layer

### 5.3 Privacy lint rule (build-time enforcement)

Add a Detekt/Konsist rule (existing project already uses Detekt per Firebase telemetry PR):

```
Class `DataDigest` and any nested class MUST NOT contain fields of type:
  String  (except: name, label, period — explicit allow-list)
  Long    (except: amounts, counts, epoch millis, category ids — explicit allow-list)
  Any nested collection whose element type would violate the above.

Test: JVM unit test reflects over DataDigest, walks the field tree, rejects any
field name matching `phone|email|address|token|pin|password|sender|body|text|message|contact`.
```

Any PR that adds a raw string field to `DataDigest` fails the build.

### 5.4 Digest JSON serialization contract

- Moshi with `@JsonClass(generateAdapter = true)` on `DataDigest` and every nested class
- Field names snake_case matching backend Prisma/Zod
- Numeric fields: `Long` (KES cents? — decide before implementation; recommend KES as `Int` since Kenyan shillings don't use fractions in practice)
- Nullable fields: emit `null`, not omit — makes schema evolution safer

**Decision needed:** KES as integer (whole shillings) or long (cents)? Recommend **integer whole shillings** — the app already handles amounts as whole KES elsewhere and cents don't exist in Kenyan pricing. Document as an invariant.

---

## 6. Entitlement &amp; Auth Flow

### 6.1 Happy path (new subscription)

```
User: taps "Subscribe" in PesaTrackProScreen
   │
   ▼
Client: PlayBillingClient.launchBillingFlow(productId=monthly|annual)
   │
   ▼
Google Play: shows purchase sheet, user confirms
   │
   ▼
Client: PurchaseUpdatedListener fires with Purchase{token, productId}
   │
   ▼
Client: acknowledgePurchase(token) via BillingClient
   │
   ▼
Client: POST /billing/verify {token, productId}  (with Authorization: Bearer <token>)
   │
   ▼
Server: Google Play Developer API → verifies → upserts Entitlement
   │
   ▼
Server: 200 {entitled: true, expiresAt, autoRenewing}
   │
   ▼
Client: writes ProState to DataStore, flips pro_ai_enabled = true
   │
   ▼
Telemetry: pro_purchase_completed, pro_entitlement_gained
```

### 6.2 Re-verification cadence

- On every app cold-start (background job, non-blocking)
- On every `/ai/*` call — server's `entitlement middleware` reads cached `Entitlement` row, forces Google re-verify if `expiryTimeMillis - now < 6h`
- If server returns `entitled: false`, client sets `pro_ai_enabled = false`, drops the ProState token, telemetry `pro_entitlement_lost`, silent revert to free UX

### 6.3 Offline path

- Client's `ProEntitlementRepository` reads DataStore → returns cached `ProState`
- If `expiresAtEpochMs > now + 24h` — treat as entitled without re-verifying (grace)
- If `expiresAtEpochMs <= now + 24h` — attempt re-verify; on network failure, extend grace by 48h once, then downgrade to free
- User never sees an error toast; they just see (or don't see) AI features

### 6.4 Restore purchase flow

Settings → PesaTrack Pro → "Restore purchase":

```kotlin
billingClient.queryPurchasesAsync(QueryPurchasesParams.newBuilder()
    .setProductType(BillingClient.ProductType.SUBS).build())
```

For each returned purchase → `POST /billing/verify`. Silent success; toast on 0-purchases-found.

### 6.5 Server-side auth middleware (Node)

```js
// src/middleware/entitlement.js
export async function requireEntitlement(req, res, next) {
  const token = extractBearerToken(req);
  if (!token) return res.status(401).json({error: 'no_token'});

  const ent = await db.entitlement.findUnique({ where: { purchaseToken: token } });
  if (!ent) return res.status(401).json({error: 'unknown_token'});

  if (ent.expiryTimeMillis <= Date.now()) {
    return res.status(403).json({error: 'expired'});
  }

  // Force revalidation if within 6h of expiry
  if (ent.expiryTimeMillis - Date.now() < 6 * 3600 * 1000) {
    const fresh = await playBilling.verify(token, ent.productId);
    if (!fresh.entitled) return res.status(403).json({error: 'revoked'});
    await db.entitlement.update({ where: { purchaseToken: token }, data: fresh });
  }

  req.entitlement = ent;
  next();
}
```

Applied to `/ai/*` and `/billing/entitlement`. NOT applied to `/billing/verify` (initial verify has no prior state) or `/health`.

---

## 7. Privacy Policy &amp; Play Console Updates

Non-negotiable before shipping to any track above closed testing.

### 7.1 `docs/privacy-policy.html` additions

New section §7 "PesaTrack Pro &amp; AI Features". Content should mirror `ai-pro-plan.md` §7. Key points:

- Pro subscription state is stored via Google Play Billing; no PesaTrack account
- When AI features are used, an anonymized spending summary is sent to our servers at `pesatrack-api.jmumo.com`
- Our servers forward the summary to OpenAI (SOC 2 compliant, US-based)
- **What is sent:** category totals, budget totals, recurring-expense summary, opaque merchant IDs (real merchant names never leave the device)
- **What is NEVER sent:** SMS text, individual transactions, phone number, name, contacts, location
- Subscription refunds/cancellation via Google Play; entitlement revocation is honored within 24h

Add link from Settings → Privacy row to the anchor `#pro-ai-privacy`.

### 7.2 Play Console Data Safety declaration

Add to existing (v1.5.2) declaration:

- **Financial info → Purchase history:** Yes / Not shared / **Required** / for App functionality (billing) / encrypted in transit / user *can* request deletion (by cancelling subscription — Play handles this)
- **Update "Data collected" from `App activity` sub-section:** Add note that AI-feature usage sends anonymized spending summaries; still under "App interactions" bucket.

### 7.3 Play Console: subscription products

Two SKUs to create in Play Console → Monetize → Subscriptions:

| SKU ID | Name | Base plan | Price | Free trial |
|---|---|---|---|---|
| `pesatrack_pro_monthly` | PesaTrack Pro (Monthly) | `monthly-autorenew` | KES 299 | 7 days |
| `pesatrack_pro_annual` | PesaTrack Pro (Annual) | `annual-autorenew` | KES 2,400 | 7 days |

(Prices are the suggested defaults from `ai-pro-plan.md` §15; owner confirms before creation.)

Geo-restriction: **Kenya only** at launch. Add other markets after 3 months of data.

---

## 8. Test Plan

### 8.1 Unit tests

**Client:**
- `DataDigestBuilder` — for a fixture Room DB, produces expected JSON. **Includes** the privacy-reflection test that asserts no PII field.
- `ProEntitlementRepository` — DataStore round-trip, grace-period logic, expiry handling
- `AiFallbackPolicy` — given a schema-invalid or deny-listed response, returns fallback

**Backend:**
- `playBilling.verify` — mocked Google client, tests package-name mismatch, expired token, valid path
- `entitlement middleware` — cache hit, forced re-verify, 401 on unknown token, 403 on expired
- Rate-limit middleware — 429 on burst

### 8.2 Integration tests

- Docker Compose spins up `pesatrack-api` + Postgres in CI
- Test: `POST /billing/verify` with a fixture Play response mock returns correct entitlement
- Test: `POST /ai/echo` with a fixture entitled token round-trips a digest
- Test: `POST /ai/echo` with an expired token returns 403

### 8.3 Manual E2E

On a real device with a Play Console test account:

1. Fresh install → Settings → PesaTrack Pro → subscribe monthly (test card) → verify entitlement in Prisma
2. Kill app → cold start → entitlement persists, no re-verify needed within 24h
3. Change device clock forward 24h → app re-verifies, still entitled
4. Change device clock forward 30 days → app forces re-verify, Play still says entitled (auto-renewed)
5. Cancel subscription in Play Store → within 24h client downgrades → Pro screen shows "Expired"
6. Restore purchase → resubscribing test account restores entitlement
7. `POST /ai/echo` from device with real entitlement round-trips a real digest

### 8.4 Privacy tests (blocking)

- **Wireshark / mitmproxy test:** Set up interception, use every code path that hits `/ai/*` — assert no request body contains: raw SMS text, full phone numbers matching KE format `254...`, contact names from the address book, any string matching known merchant names outside the anonymized ID scheme.
- **Log review:** Run backend for 24h with the Detekt-equivalent Node lint rule, assert no log line contains raw purchase tokens or digest bodies.

---

## 9. Rollout &amp; Feature Flag

### 9.1 Ship order

1. Backend deploys to Hetzner first (`pesatrack-api.jmumo.com` responding to `/health`)
2. Play Console subscriptions created + activated in test track
3. Android build with `pro_ai_enabled = false` ships to **closed testing** track
4. Internal team (owner + 2 testers) subscribes with test cards; validates §8.3
5. Internal team enables `pro_ai_enabled = true` via a hidden Settings → Developer toggle (added for this phase only, removed in Phase 2 release)
6. Validate `/ai/echo` round-trip on entitled accounts
7. Beta users (10–20 alpha testers) invited; still `/ai/echo`-only
8. **Phase 2 development begins** — Phase 1 stays in closed testing until Phase 2 cutover
9. Combined Phase 1 + Phase 2 release ships to production

### 9.2 Kill switches

- **Remote kill via backend:** Set `ENABLE_AI_ENDPOINTS=false` in `.env` and redeploy; all `/ai/*` return 503. Client falls back to template UX silently.
- **Remote kill via app config:** Not built in Phase 1. If needed, use Firebase Remote Config (already integrated) with a `pro_ai_master_off` flag.
- **Local kill via feature flag:** `pro_ai_enabled = false` in `AppPreferences` disables all client-side AI paths.

---

## 10. Definition of Done

- [ ] Backend deployed at `pesatrack-api.jmumo.com`, `/health` returns 200 over TLS
- [ ] `POST /billing/verify` verified against a real Play Console test purchase
- [ ] `POST /ai/echo` round-trips a digest for an entitled test account
- [ ] Android app builds green, all unit tests pass, Detekt privacy rule passes
- [ ] Manual E2E scenarios in §8.3 all pass on one physical device
- [ ] `docs/privacy-policy.html` updated and reviewed
- [ ] Play Console Data Safety declaration updated (draft, not published — waits for Phase 2 combined release)
- [ ] `_docs/implementation-status.md` updated per AGENTS.md auto-update rule
- [ ] Website sync-check answered in the PR description per AGENTS.md
- [ ] Phase 2 spec (Coach Insights) reviewed and unblocked
