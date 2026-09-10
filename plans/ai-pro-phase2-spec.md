# AI Pro — Phase 2 Spec: Coach Insights (P1, the anchor)

> **Parent plan:** [`plans/ai-pro-plan.md`](ai-pro-plan.md)
> **Prerequisite:** [`plans/ai-pro-phase1-spec.md`](ai-pro-phase1-spec.md) shipped and validated
> **Status:** Ready for implementation after Phase 1 lands
> **Ships as:** Android v1.7.0 + backend v0.2.0 (combined release with Phase 1 to Play production)
> **User-visible AI:** **Yes** — this is the first Pro AI feature users see.

## Table of Contents

1. [Goal &amp; User Story](#1-goal--user-story)
2. [UX](#2-ux)
3. [End-to-End Flow](#3-end-to-end-flow)
4. [Coach Insight JSON Schema](#4-coach-insight-json-schema)
5. [Prompt Design](#5-prompt-design)
6. [Client Implementation](#6-client-implementation)
7. [Backend Implementation](#7-backend-implementation)
8. [Fallback &amp; Failure Modes](#8-fallback--failure-modes)
9. [Rate Limiting &amp; Caching](#9-rate-limiting--caching)
10. [Telemetry](#10-telemetry)
11. [Test Plan](#11-test-plan)
12. [Definition of Done](#12-definition-of-done)

---

## 1. Goal &amp; User Story

### Goal

Deliver the first user-visible Pro AI feature: a single narrative Coach Insight card on the Home screen that replaces the existing template summary card for Pro users. Content is generated once per day by OpenAI, grounded in the user's `DataDigest`, guardrailed by strict JSON schemas + a deny-list, and falls back silently to the template if anything goes wrong.

### User story

> As a Pro subscriber, when I open the app in the morning, I see a specific, actionable observation about my spending — not just numbers. The observation names a category, quotes a real KES figure, compares it to my baseline, and offers one thing I could do about it. If the observation talks about savings or investing, it shows me the assumptions it's making.

### Non-goals

- No chat (that's Phase 3 / Ask Your Money — see [`plans/ai-pro-phase3-spec.md`](ai-pro-phase3-spec.md))
- No multi-card feed (single card per day)
- No push notifications (Phase 1 telemetry showed users prefer opening the app to being pinged; also the notification cadence rule from AGENTS.md applies)
- No historic Insight browser (v2)

---

## 2. UX

### 2.1 The card

Location: Home screen, top card slot (currently rendering the Weekly/Monthly Review template card).

Layout:

```
┌────────────────────────────────────────────────┐
│  ⚡  Today's Insight            Wed 12 Sep     │  ← header (icon + date)
├────────────────────────────────────────────────┤
│  Food spending is climbing                     │  ← title (bold, 1 line, max 60 chars)
│                                                │
│  You've spent KES 12,400 on Food & Dining      │  ← body (regular, ≤ 3 sentences)
│  this month — 26% above your 3-month average.  │
│  Java House alone is KES 8,400 across 12       │
│  visits, up from a typical KES 3,200.          │
│                                                │
│  💡 Could save ~KES 5,200 this month           │  ← saveable pill (optional)
│                                                │
│  [ Open Food & Dining budget → ]               │  ← action (optional, deep link)
│                                                │
│  ▸ Assumptions (2)                             │  ← expander, shown only if present
└────────────────────────────────────────────────┘
```

### 2.2 States

| State | UI |
|---|---|
| **Loading (first fetch of the day)** | Show yesterday's cached insight (if any) with a small pulsing "Updating" chip. Never a blank card. |
| **Ready** | The card as above. |
| **Fallback** | The existing template Weekly/Monthly Review card renders unchanged. No "AI unavailable" text. |
| **Assumptions expanded** | Bulleted list of assumption strings (1–5). |
| **Not entitled (free user)** | This spec doesn't touch free-user UX. They keep seeing the template card. |

### 2.3 Copy guardrails (baked into the schema &amp; prompt)

- `title` ≤ 60 chars, no shame framing
- `body` ≤ 400 chars, ≤ 3 sentences, present tense, second person, KES with thousands sep
- `action_label` ≤ 40 chars, verb-first ("Open", "Review", "See")
- `action_deeplink` must start with `pesatrack://` and match a known route (server whitelist)
- `assumptions` mandatory if `saveable_amount_kes` is non-null OR body contains projection language

### 2.4 Accessibility

- Card content readable by TalkBack in order: title → body → saveable pill → action → assumptions expander
- Assumptions expander is a `Button` with `Role.DropdownList` semantics
- Minimum touch target for action button: 48 dp height

---

## 3. End-to-End Flow

```
                                    ┌─────────────────────┐
                                    │  User opens Home    │
                                    └──────────┬──────────┘
                                               │
                                               ▼
                         ┌──────────────────────────────────────┐
                         │  HomeViewModel.load()                │
                         │  is Pro entitled?                    │
                         └────┬──────────────────────┬──────────┘
                              │ no                    │ yes
                              ▼                       ▼
                       ┌──────────────┐     ┌────────────────────────────────┐
                       │  Template    │     │  CoachInsightRepository        │
                       │  Review Card │     │    .getForToday()              │
                       └──────────────┘     │  cache hit today?              │
                                            └────┬────────────────┬──────────┘
                                                 │ yes             │ no
                                                 ▼                 ▼
                                        ┌───────────────┐   ┌──────────────────────────┐
                                        │  Return       │   │  DataDigestBuilder        │
                                        │  cached JSON  │   │    .buildForCurrentPeriod│
                                        └───────────────┘   └────────────┬─────────────┘
                                                                         ▼
                                                              ┌──────────────────────┐
                                                              │  PesaTrackAiClient   │
                                                              │  POST /ai/coach-insight
                                                              └────────────┬─────────┘
                                                                           │
                                                                           ▼
                                                              ┌──────────────────────────┐
                                                              │  Backend                  │
                                                              │  1. entitlement mw       │
                                                              │  2. rate-limit mw        │
                                                              │  3. hash(digest) cache?   │
                                                              │  4. OpenAI Structured     │
                                                              │     Outputs strict       │
                                                              │  5. deny-list scrub      │
                                                              │  6. schema validate      │
                                                              └────────────┬─────────────┘
                                                                           │
                                                                           ▼
                                                              ┌──────────────────────────┐
                                                              │  Response:                │
                                                              │  { fallback: false,       │
                                                              │    insight: {title,...} } │
                                                              │  or                       │
                                                              │  { fallback: true }       │
                                                              └────────────┬─────────────┘
                                                                           │
                                                                           ▼
                                                              ┌──────────────────────────┐
                                                              │  Client caches for 24h    │
                                                              │  Renders card             │
                                                              │  Rehydrates recipient      │
                                                              │  IDs from local map        │
                                                              └──────────────────────────┘
```

---

## 4. Coach Insight JSON Schema

Full schema enforced by OpenAI Structured Outputs strict mode and re-validated server-side.

```jsonc
{
  "name": "coach_insight_v1",
  "strict": true,
  "schema": {
    "type": "object",
    "required": ["title", "body", "assumptions", "saveable_amount_kes", "action_label", "action_deeplink"],
    "additionalProperties": false,
    "properties": {
      "title": {
        "type": "string",
        "minLength": 8,
        "maxLength": 60,
        "description": "Neutral, factual, 1 line. Never shame-framed. Never a question."
      },
      "body": {
        "type": "string",
        "minLength": 40,
        "maxLength": 400,
        "description": "2-3 sentences. Second person, present tense. Include at least one specific KES figure. Never mention specific securities, brokers, or guaranteed returns."
      },
      "action_label": {
        "type": ["string", "null"],
        "maxLength": 40,
        "description": "Optional CTA text. Verb-first. Null if no useful action."
      },
      "action_deeplink": {
        "type": ["string", "null"],
        "pattern": "^pesatrack://(home|budgets|analytics|expenses|category/[0-9]+|recipient/r[0-9]+)$",
        "description": "Optional deep link. Must match one of the known routes. Null if no action."
      },
      "saveable_amount_kes": {
        "type": ["integer", "null"],
        "minimum": 0,
        "maximum": 1000000,
        "description": "If the body suggests savings, the estimated saveable amount in whole KES. Otherwise null."
      },
      "assumptions": {
        "type": "array",
        "maxItems": 5,
        "items": { "type": "string", "maxLength": 120 },
        "description": "REQUIRED if saveable_amount_kes is non-null. Each string is one assumption the model made (e.g. 'Assumes Food spending stays at current pace')."
      },
      "referenced_recipient_ids": {
        "type": "array",
        "maxItems": 3,
        "items": { "type": "string", "pattern": "^r[0-9]+$" },
        "description": "Any recipient IDs (from the digest) the model referenced by name in the body. Client rehydrates these to real names before render."
      }
    }
  }
}
```

**Server post-validation rules** (in addition to strict-mode conformance):

1. If `saveable_amount_kes != null` AND `assumptions.length == 0` → reject, return fallback
2. If body text (after recipient rehydration) contains any deny-list term → reject, return fallback
3. If `action_deeplink` references a `category/N` or `recipient/rN` that isn't in the current digest → strip both `action_label` and `action_deeplink` (soft-fix, don't fallback)
4. If `referenced_recipient_ids` contains any ID not in the current digest → reject, return fallback

---

## 5. Prompt Design

### 5.1 System prompt (pinned)

```
You are PesaTrack Coach, a financial insight generator for a Kenyan
personal-finance app. Your job is to produce ONE useful, specific
observation about the user's spending for today.

RULES
- Write in second person, present tense.
- Use KES with thousands separators (e.g. "KES 12,400"). Never other currencies.
- Never shame or use fear framing. Never say "you overspent", "you're losing money",
  "you shouldn't have". Frame savings as opportunity, never as failure.
- Never recommend specific securities, brokers, or funds by name.
  Never promise guaranteed returns.
- Never invent numbers. Every KES figure in your response must be derivable from
  the DataDigest provided.
- Cite at most 3 recipients by ID (r1, r2, ...). The client will replace these
  with real names before showing the user. Do not invent or transliterate names.
- Include an action ONLY if it's a real, useful next step. It's fine to omit.
- If you include a saveable_amount_kes, you MUST list the assumptions behind it.
- Aim for a specific behavioral observation the user probably didn't know:
  a category up unexpectedly, a leak, a recurring cost the user might reconsider,
  a savings opportunity grounded in what they already spend.

INPUT: a DataDigest JSON with aggregated spending numbers.
OUTPUT: a single JSON object matching the coach_insight_v1 schema.
```

### 5.2 User prompt (per request)

```
Today is {today_date}.
The user's current period is {period_start} to {period_end} (day {days_elapsed} of {days_total}).

DATA_DIGEST:
{digest_json}

Produce today's coach insight.
```

### 5.3 Model parameters

- `model`: `gpt-4.1-mini` (or current OpenAI mini workhorse — pinned in env `OPENAI_MODEL`)
- `temperature`: 0.4 (some variety turn-to-turn, but not creative)
- `max_output_tokens`: 800 (schema fits well under)
- `response_format`: Structured Outputs with `coach_insight_v1` schema (strict)
- `seed`: null (we want cache misses to produce fresh takes)

---

## 6. Client Implementation

### 6.1 New files

```
com.pesatrack.services.ai/
├── CoachInsightRepository.kt      ← main API for the ViewModel
├── CoachInsight.kt                ← data class matching schema
├── CoachInsightCache.kt           ← DataStore-backed 24h cache
└── (from Phase 1) DataDigestBuilder.kt, PesaTrackAiClient.kt, AiFallbackPolicy.kt

com.pesatrack.presentation.components/
└── CoachInsightCard.kt            ← Compose component

com.pesatrack.presentation.screens.home/
└── HomeViewModel.kt               ← MODIFIED to fetch insight when Pro
```

### 6.2 `CoachInsight` data class

```kotlin
@JsonClass(generateAdapter = true)
data class CoachInsight(
    val title: String,
    val body: String,
    @Json(name = "action_label") val actionLabel: String?,
    @Json(name = "action_deeplink") val actionDeeplink: String?,
    @Json(name = "saveable_amount_kes") val saveableAmountKes: Int?,
    val assumptions: List<String>,
    @Json(name = "referenced_recipient_ids") val referencedRecipientIds: List<String>
) {
    /** Applied by CoachInsightRepository after fetch, replaces r1/r2/... in title & body. */
    fun withRehydratedRecipients(map: Map<String, String>): CoachInsight = copy(
        title = replaceIds(title, map),
        body = replaceIds(body, map)
    )
}

private fun replaceIds(s: String, map: Map<String, String>): String =
    map.entries.fold(s) { acc, (id, name) -> acc.replace(id, name) }
```

### 6.3 `CoachInsightRepository`

```kotlin
@Singleton
class CoachInsightRepository @Inject constructor(
    private val digestBuilder: DataDigestBuilder,
    private val client: PesaTrackAiClient,
    private val cache: CoachInsightCache,
    private val entitlement: ProEntitlementRepository,
    private val fallback: AiFallbackPolicy,
    private val telemetry: TelemetryClient,
    private val clock: Clock,
) {
    /** Returns the insight to show today, or null to fall back to template. */
    suspend fun getForToday(): CoachInsight? {
        if (!entitlement.isEntitled.first()) return null

        cache.getIfFreshForToday(clock.today())?.let { return it }

        return try {
            val (digest, recipientMap) = digestBuilder.buildForCurrentPeriod()
            val response = client.coachInsight(CoachInsightRequest(digest))
            if (response.fallback) {
                telemetry.log("coach_insight_fallback", mapOf("reason" to response.reason.orEmpty()))
                return null
            }
            val hydrated = response.insight!!.withRehydratedRecipients(recipientMap)
            cache.putForToday(clock.today(), hydrated)
            hydrated
        } catch (e: Exception) {
            telemetry.log("coach_insight_fetch_failed", mapOf("reason" to fallback.classify(e).name))
            null
        }
    }
}
```

### 6.4 `CoachInsightCache`

DataStore-backed, stores yesterday's + today's insight as JSON:

```kotlin
data class CachedInsight(val date: LocalDate, val insight: CoachInsight)

// Key: coach_insight_today_v1  → JSON of CachedInsight
// getIfFreshForToday(today: LocalDate) returns insight only if cache.date == today
// On date rollover, yesterday's insight is kept as fallback content shown while today's is fetching
```

### 6.5 `HomeViewModel` integration

Existing `HomeViewModel.load()` gets a new step:

```kotlin
val insightDeferred = viewModelScope.async {
    coachInsightRepository.getForToday()
}
// ... existing loads in parallel ...
val insight = insightDeferred.await()
_uiState.update { it.copy(coachInsight = insight) }
```

`HomeUiState` gains `val coachInsight: CoachInsight?`. `HomeScreen` conditionally renders `CoachInsightCard(insight)` if non-null, otherwise the existing template card unchanged.

### 6.6 Deep link handling

`action_deeplink` values map to existing routes. No new routes added in Phase 2. Handled by the existing `NavGraph` deep-link intent filter (already wired for notification actions).

New deep links supported by Phase 2 schema:
- `pesatrack://home`
- `pesatrack://budgets`
- `pesatrack://analytics`
- `pesatrack://expenses`
- `pesatrack://category/{id}` — Analytics filtered to category
- `pesatrack://recipient/{r_id}` — client resolves the r_id via the current digest map to the merchant, opens Merchant detail screen

If the deep link references an r_id no longer in the current digest (edge case: cache from yesterday, digest has changed), the card renders without the action button (soft-fix rule §4).

---

## 7. Backend Implementation

### 7.1 New route: `POST /ai/coach-insight`

```
src/routes/ai.js
  router.post('/coach-insight',
    requireEntitlement,           // Phase 1 middleware
    perUserRateLimit('coach'),    // 3/day, 1/min
    coachInsightHandler
  );
```

### 7.2 Handler flow

```js
async function coachInsightHandler(req, res) {
  const { digest } = req.body;

  // 1. Validate digest shape via Zod (bounded, no PII)
  const parsed = DigestSchema.safeParse(digest);
  if (!parsed.success) return res.status(400).json({error: 'invalid_digest'});

  // 2. Cache key = sha256(digest JSON canonicalized)
  const digestHash = sha256Canonical(digest);
  const cached = await cache.get(`insight:${digestHash}`);
  if (cached) {
    audit('coach_insight.cache_hit', req);
    return res.json({ fallback: false, insight: cached });
  }

  // 3. Call OpenAI with strict Structured Outputs
  let raw;
  try {
    raw = await aiProvider.generate({
      model: config.openaiModel,
      systemPrompt: COACH_INSIGHT_SYSTEM_PROMPT,
      userPrompt: buildUserPrompt(digest),
      responseFormat: coachInsightV1Schema,
      maxOutputTokens: 800,
      temperature: 0.4,
    });
  } catch (e) {
    audit('coach_insight.provider_error', req, {reason: e.code});
    return res.json({ fallback: true, reason: 'provider_error' });
  }

  // 4. Post-validation (schema is already enforced by strict mode, but double-check)
  const insight = raw.parsed; // { title, body, ... }
  const issue = postValidate(insight, digest);
  if (issue) {
    audit('coach_insight.rejected', req, {reason: issue});
    return res.json({ fallback: true, reason: issue });
  }

  // 5. Deny-list scrub on assembled text
  const flat = `${insight.title}\n${insight.body}\n${(insight.assumptions||[]).join('\n')}`;
  if (denyListMatch(flat)) {
    audit('coach_insight.denylist_hit', req);
    return res.json({ fallback: true, reason: 'denylist' });
  }

  // 6. Cache
  await cache.set(`insight:${digestHash}`, insight, 24 * 3600);

  audit('coach_insight.ok', req);
  return res.json({ fallback: false, insight });
}
```

### 7.3 Deny-list (initial)

Case-insensitive substring matches:

```
guaranteed return, guaranteed profit, risk-free, definitely will grow,
certainly grow, will definitely make you, investment advice,
sacco recommendation, buy [any stock ticker regex], mmf recommendation
```

Plus a broker list to keep growing:
```
Faulu, KCB Capital, NCBA Investment Bank, Britam, Old Mutual Securities,
Sanlam Investments, ICEA Lion Asset Management, Cytonn, ...
```

**Deny-list is data, not code.** Stored in `src/config/denylist.json` — hot-reloadable via container restart.

### 7.4 `postValidate()` rules

```js
function postValidate(insight, digest) {
  // Rule 1: saveable + no assumptions
  if (insight.saveable_amount_kes != null &&
      (!insight.assumptions || insight.assumptions.length === 0)) {
    return 'saveable_no_assumptions';
  }

  // Rule 2: referenced recipient IDs must all exist in digest
  const digestRecipientIds = new Set((digest.top_recipients_this_period || []).map(r => r.id));
  for (const rid of (insight.referenced_recipient_ids || [])) {
    if (!digestRecipientIds.has(rid)) return 'unknown_recipient_id';
  }

  // Rule 3: title/body must not contain currency other than KES
  if (/\b(USD|EUR|GBP|KSH|shilling)\b/i.test(insight.title + insight.body)) {
    // KSh / shilling in body is fine; USD/EUR/GBP is not
    if (/\b(USD|EUR|GBP)\b/i.test(insight.title + insight.body)) return 'foreign_currency';
  }

  // Rule 4: category deeplink refers to a real category in the digest
  if (insight.action_deeplink) {
    const catMatch = insight.action_deeplink.match(/^pesatrack:\/\/category\/(\d+)$/);
    if (catMatch) {
      const catId = Number(catMatch[1]);
      const known = (digest.categories || []).some(c => c.id === catId);
      if (!known) {
        // Soft-fix: strip action, don't fallback
        insight.action_label = null;
        insight.action_deeplink = null;
      }
    }
  }

  return null; // ok
}
```

### 7.5 Provider abstraction

```js
// src/services/ai/AiProvider.js
class AiProvider {
  async generate({ model, systemPrompt, userPrompt, responseFormat, maxOutputTokens, temperature }) {
    throw new Error('abstract');
  }
}

// src/services/ai/OpenAiProvider.js — uses openai npm SDK with response_format = {type: 'json_schema', json_schema: {...}}
```

Groq / Gemini providers stay as TODO stubs from Phase 1 (unused).

### 7.6 Cost logging

Every OpenAI call logs (never with PII):

```
{
  "event": "openai.call",
  "endpoint": "coach_insight",
  "model": "gpt-4.1-mini",
  "input_tokens": 512,
  "output_tokens": 342,
  "latency_ms": 1240,
  "token_hash": "abc123...",
  "cached": false
}
```

Aggregated hourly and rolled up in Prometheus for spend forecasting.

---

## 8. Fallback &amp; Failure Modes

| Failure | Detection | User-visible effect |
|---|---|---|
| Client offline | OkHttp `IOException` | Yesterday's cached insight (if any); else template card |
| Backend 5xx | HTTP status | Template card; retry tomorrow |
| Backend 429 (rate limited) | HTTP 429 | Yesterday's cache; retry tomorrow |
| Backend returns `{fallback: true}` | JSON field | Template card |
| Schema validation fails on client (defensive) | Moshi throws | Template card |
| Deep link references unknown recipient/category | Client resolution | Card renders without action button (soft-fix) |
| OpenAI produces schema-valid but toxic content | deny-list post-scrub | Template card |
| Cost breach (backend circuit breaker) | Backend switches all `/ai/*` to fallback for 5 min | Template card |
| Play Billing entitlement lost mid-day | Client `pro_ai_enabled = false` | Yesterday's cache honored for 24h grace, then template |

**Golden rule:** the user never sees an error card, error toast, or "AI unavailable" text unless they explicitly navigate to Settings → PesaTrack Pro (§ 15G of `ai-pro-plan.md`).

---

## 9. Rate Limiting &amp; Caching

Client:
- One request per day per user (checked before hitting network)
- Cache invalidation: at local midnight, or when the user explicitly refreshes (pull-to-refresh on Home)

Server (per Phase 1 §4.7 + `ai-pro-plan.md` §8.5):
- `/ai/coach-insight`: **3/day per token**, 1/min burst (the extra 2 headroom for pull-to-refresh + a rare recompute)
- Digest-hash cache: 24h TTL. Two users with identical digests would share a response — vanishingly unlikely in practice but a nice free saving on shared-household edge cases

---

## 10. Telemetry

New allow-listed events (`TelemetryEvents.kt`):

| Event | Params |
|---|---|
| `coach_insight_shown` | `source` (`home_card` \| `home_card_from_cache`) |
| `coach_insight_action_tapped` | `deeplink_route` (bucketed enum: `budgets` \| `analytics` \| `expenses` \| `category` \| `recipient` \| `home`) |
| `coach_insight_assumptions_expanded` | (none) |
| `coach_insight_feedback` | `verdict` (`useful` \| `not_useful`) — from optional 👍/👎 chip |
| `coach_insight_fallback` | `reason` (bucketed: `not_entitled` \| `provider_error` \| `denylist` \| `schema` \| `rate_limit` \| `network` \| `unknown`) |
| `coach_insight_fetch_failed` | `reason` (bucketed) |

The 👍/👎 chip is small text-button under the card, not intrusive. Feedback informs the anti-metric in `ai-pro-plan.md` §12.

---

## 11. Test Plan

### 11.1 Unit tests

**Client:**
- `CoachInsightCache` — round-trip, date rollover behavior, corruption tolerance
- `CoachInsight.withRehydratedRecipients` — replaces `r1`, `r2`, ... but not `r10` when only r1 is in map (word-boundary correctness)
- `CoachInsightCard` (Compose test) — renders states: normal, no action, with assumptions expanded, without saveable pill

**Backend:**
- `postValidate` — every rule branch (saveable-no-assumptions, unknown recipient, foreign currency, soft-fix on unknown category deep link)
- `denyListMatch` — positive + negative for every deny-list category
- Schema-strict-mode invariant test: given a fixture OpenAI response, confirm all required fields present
- Cache hit/miss test with a mocked provider

### 11.2 Integration tests

- Mock OpenAI provider that returns a fixture valid response → E2E from `POST /ai/coach-insight` produces the correct client-consumable JSON
- Mock OpenAI provider that returns an invalid response (missing assumptions with saveable) → backend returns `{fallback: true, reason: 'saveable_no_assumptions'}`
- Mock provider that returns a deny-listed term → backend returns `{fallback: true, reason: 'denylist'}`

### 11.3 Prompt-quality evaluation (manual, blocking for prod)

Before shipping to production, run the "insight quality suite":

1. Assemble 30 fixture DataDigests covering: high-food-spend, quiet-leak, over-budget, on-track, under-budget, first-month-ever, near-zero-activity, high-recurring-share, etc.
2. Call `/ai/coach-insight` on each with the real model
3. Owner reviews each output for: tone compliance, specificity, honesty, assumption clarity
4. Ship threshold: ≥ 27/30 pass owner review

Fixtures + expected properties live in `backend/tests/fixtures/coach_insight/`.

### 11.4 Cost regression test

Nightly CI job (manual first, automated later): run the insight-quality suite, sum token usage, alert if avg per-request cost > 1.5× baseline.

---

## 12. Definition of Done

- [ ] `POST /ai/coach-insight` deployed to prod backend, verified via E2E test
- [ ] Insight-quality suite (§11.3) passes ≥ 27/30 owner review
- [ ] `CoachInsightCard` rendered on Home for entitled test users, template card unchanged for others
- [ ] All fallback paths (§8) verified end-to-end on device
- [ ] Deny-list scrub verified via fixture provider response
- [ ] Deep-link routing verified for every schema-allowed deep link
- [ ] Telemetry events fire correctly per §10 (verified in Firebase DebugView)
- [ ] Play Console release notes drafted (see below)
- [ ] `docs/privacy-policy.html` §7 updated to note Coach Insight sends the digest to OpenAI
- [ ] `_docs/implementation-status.md` updated
- [ ] Website sync-check answered in the PR (likely triggers `/features/` new page for "Coach")
- [ ] Cost tracking dashboard shows the first 24h of production traffic; total spend within projection

### Play Store release notes (draft)

```
What's New in PesaTrack Pro:
• Introducing Coach Insights — a daily observation about your spending, with
  specific numbers and one thing you could try. Grounded in your own M-PESA
  activity, never generic advice.
• Every projection shows its assumptions.
• Never sends your SMS or transactions. Only anonymized totals leave your device.

Coach Insights are part of PesaTrack Pro. Free features continue unchanged.
```
