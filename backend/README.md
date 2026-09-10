# PesaTrack API

Backend service for **PesaTrack AI Pro**. Ships as a Node.js + Express + Prisma + Postgres app, deployed via Docker Compose on Hetzner alongside StockUp and MaliScope.

> **Status:** Phase 1 scaffold (v0.1.0). Public endpoints: `/health`, `/billing/verify`, `/billing/entitlement`, `/ai/echo`. Real AI endpoints (`/ai/coach-insight`, `/ai/ask`, `/ai/categorize`) ship in Phase 2 and Phase 4.

## What lives here

- `src/index.js` — Express bootstrap
- `src/config/` — env → typed config (`zod`-validated)
- `src/middleware/` — request-id, structured JSON logger, PII scrub, rate limits, entitlement auth, error handlers
- `src/routes/` — `health`, `billing`, `ai`
- `src/services/`
  - `prisma.js` — client singleton
  - `playBilling.js` — Google Play Developer API verification + token hashing
  - `ai/` — `AiProvider` interface + `OpenAiProvider` (real) + `Groq`/`Gemini` stubs
- `src/legacy/` — archived Daraja/M-PESA v1 code (never wired; deletable after Phase 2 + 90d)
- `prisma/schema.prisma` — `Entitlement` and `AuditEvent` tables. **No `User` table by design** — the Play purchase token is the identity.

## Design promises

1. **No PII anywhere.** No emails, phone numbers, names, addresses, raw SMS text, or M-PESA transaction bodies are accepted, logged, or stored.
2. **Purchase tokens are never persisted raw.** Only `sha256(token)` is stored. Logs show only the first 16 chars of the hash.
3. **Request bodies are never logged.** The access log captures method, path, status, and duration only.
4. **Fake billing is production-forbidden.** `DEV_FAKE_BILLING=true` when `NODE_ENV=production` causes the process to refuse to boot.
5. **AI endpoints are feature-flagged.** Phase 1 exposes only `/ai/echo` (no LLM call). Real endpoints unlock in Phase 2 via `ENABLE_AI_ENDPOINTS=true`.

Full principles: [../plans/ai-pro-plan.md](../plans/ai-pro-plan.md) §7–§8. Phase 1 spec: [../plans/ai-pro-phase1-spec.md](../plans/ai-pro-phase1-spec.md).

## Local development

Requires **Node.js ≥ 20** and Docker (for the local Postgres).

```powershell
cd backend
cp .env.example .env
# Edit .env: set POSTGRES_PASSWORD, keep DEV_FAKE_BILLING=true for local

npm install
docker compose up -d db          # start Postgres only
npm run db:migrate               # create tables
npm run dev                      # nodemon on port 3000
```

Smoke test:

```powershell
# Health
curl.exe http://localhost:3000/health

# Verify a fake purchase (DEV_FAKE_BILLING mode)
curl.exe -X POST http://localhost:3000/billing/verify `
  -H "Content-Type: application/json" `
  -d '{"purchaseToken":"fake-test-token-12345","productId":"pesatrack_pro_monthly"}'

# Echo a digest (auth required — use the fake purchase token as bearer)
curl.exe -X POST http://localhost:3000/ai/echo `
  -H "Content-Type: application/json" `
  -H "Authorization: Bearer fake-test-token-12345" `
  -d '{"digest":{"period":"2026-09","totals":{"spent":42800}}}'
```

## Production deploy (Hetzner, existing host)

The pattern is identical to StockUp — see [`C:\Eng\StockUp\deploy\README.md`](../../StockUp/deploy/README.md) §4 "Deploy another app".

1. **On the Hetzner host** (`2.28.75.21`):

   ```bash
   cd ~
   git clone <repo> pesatrack-repo   # or pull latest
   cd pesatrack-repo/backend
   cp .env.example .env
   # Edit .env:
   #   NODE_ENV=production
   #   POSTGRES_PASSWORD=$(openssl rand -hex 24)
   #   DEV_FAKE_BILLING=false
   #   OPENAI_API_KEY=<real key>
   #   GOOGLE_PLAY_SERVICE_ACCOUNT_JSON=/run/secrets/play_service_account.json
   ```

2. **Mount the Play Developer service-account JSON** as a Docker secret (path referenced above), or edit `docker-compose.yml` to inject it via `secrets:`.

3. **Bring the stack up:**

   ```bash
   docker compose up -d --build
   docker compose logs -f web
   ```

4. **Add the Caddy block** on the host: copy `Caddyfile.snippet` into `~/proxy/Caddyfile` and reload Caddy.

5. **Verify:**

   ```bash
   curl https://pesatrack-api.jmumo.com/health
   ```

## Endpoints (v0.1.0)

| Method | Path | Auth | Purpose |
|---|---|---|---|
| GET  | `/health` | none | Liveness. Used by Caddy + monitoring. |
| POST | `/billing/verify` | none (rate-limited by token) | Register / re-verify a Play purchase. Returns normalized entitlement. |
| GET  | `/billing/entitlement` | `Bearer <purchaseToken>` | Current entitlement snapshot. Force-revalidates against Google if within 6h of expiry. |
| POST | `/ai/echo` | `Bearer <purchaseToken>` | Pipeline smoke test. Accepts a `DataDigest` JSON body and echoes field count + period. **Never invokes the LLM.** |
| any  | `/ai/*` (other) | — | Returns 501 in Phase 1. Unlocks in Phase 2. |

## Rate limits (v0.1.0)

- `/billing/verify` — 20 / hour per token
- `/billing/entitlement` — 60 / hour per token
- `/ai/echo` — 100 / day per token

Per-endpoint AI limits from [ai-pro-plan.md §8.5](../plans/ai-pro-plan.md) land with Phase 2.

## Testing

```powershell
npm test
```

## What's not here yet

- Actual OpenAI calls (Phase 2)
- SSE streaming (Phase 3 `/ai/ask`)
- Circuit breaker + digest-hash cache (Phase 2)
- Real Play Developer API calls in dev (use `DEV_FAKE_BILLING=true` until you have a service account)
- Automated tests (add during Phase 2 when logic gets non-trivial)
