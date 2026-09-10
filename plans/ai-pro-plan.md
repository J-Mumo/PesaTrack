# PesaTrack AI Pro — Strategic Plan

> **Created:** 2026-09-11
> **Status:** Approved — ready for phase specs
> **Owner:** TBD
> **Supersedes:** [`plans/ai-features-plan.md`](ai-features-plan.md) (2026-03-22 planning doc), and the AI-related portions of [`plans/pro-launch-plan.md`](pro-launch-plan.md) (2026-XX Pro v1 template-based positioning). Both are retained for historical context.

## Table of Contents

1. [Strategic Thesis](#1-strategic-thesis)
2. [Confirmed Decisions](#2-confirmed-decisions)
3. [What "Smarter Spending Advice" Actually Means](#3-what-smarter-spending-advice-actually-means)
4. [Free vs Pro Split](#4-free-vs-pro-split)
5. [The Six Pro AI Features](#5-the-six-pro-ai-features)
6. [Architecture](#6-architecture)
7. [Data Digest &amp; Privacy Contract](#7-data-digest--privacy-contract)
8. [LLM Guardrails](#8-llm-guardrails)
9. [Provider Choice — Why OpenAI](#9-provider-choice--why-openai)
10. [Cost Model](#10-cost-model)
11. [Rollout Phases](#11-rollout-phases)
12. [Success Metrics](#12-success-metrics)
13. [Explicit Non-Goals &amp; Rejected Items](#13-explicit-non-goals--rejected-items)
14. [Feature Decision Filter](#14-feature-decision-filter)
15. [Open Decisions (Remaining)](#15-open-decisions-remaining)

---

## 1. Strategic Thesis

Users are drowning in *numbers* — PesaTrack already ships charts, forecasts, recurring detection, MoM/YoY, budget pace, quiet-leak detection. The consistent piece of user feedback (documented internally, also matches the pattern in the Play Store reviews) is: **"give me smarter spending advice."** That's a request for *judgment*, not more analytics — and it's the one gap statistical features fundamentally cannot close.

**Pro = the app has an opinion. Free = the app has facts.**

- Free tier stays fully capable at tracking, categorizing, budgeting, forecasting, recurring detection, and the analytics feed we've already shipped. Nothing already released to Play Store users gets moved behind a paywall.
- Pro tier adds a **Financial Coach** layer that reads the user's (aggregated, anonymized) data via a Hetzner-hosted backend and produces *advice*, *simulations*, and *conversation*.

This is defensible against the free tier because advice is a genuinely different product from analytics, and defensible against competitors because it's grounded in the user's own M-PESA history rather than generic finance-blog copy.

---

## 2. Confirmed Decisions

| # | Question | Decision | Locked on |
|---|---|---|---|
| 1 | Billing rail | **Play Billing direct** (no RevenueCat) | 2026-09-11 |
| 2 | Pricing structure | **Monthly + annual** (annual ≈ 8× monthly, target 33% discount for annual commitment) | 2026-09-11 |
| 3 | AI provider primary | **OpenAI** (`gpt-4.1-mini`-class workhorse). Backend written provider-agnostic so Groq/Gemini can slot in later. | 2026-09-11 |
| 4 | Chat scope | **Read-only advisory.** AI never mutates data. User acts on suggestions. | 2026-09-11 |
| 5 | Free-tier AI teaser | **None.** Clean paywall, single discoverable Pro CTA. | 2026-09-11 |
| 6 | Backend | **Hetzner CPX21 multi-app VM** at `2.28.75.21`, subdomain `pesatrack-api.jmumo.com`, on the shared `web` Docker network + Caddy proxy per [`C:\Eng\StockUp\deploy\README.md`](../../StockUp/deploy/README.md). | 2026-09-11 |
| 7 | Ask Your Money scope | **Q&A + short-term memory** (last 10 turns). Default from author recommendation — user has ability to override. | 2026-09-11 (tentative — flagged in §15) |
| 8 | Actual model | `gpt-4.1-mini` or current OpenAI mini-class workhorse at ship time. Pinned by exact model ID (never `-latest`). | 2026-09-11 |

**Not yet decided** (see §15): exact Pro pricing in KES, annual free-trial length, precise Ask-Your-Money memory-window turn count, chat streaming vs single-shot.

---

## 3. What "Smarter Spending Advice" Actually Means

Broken down into concrete jobs-to-be-done, ranked by how often users ask them in feedback:

| # | User question | Today's answer (free) | Pro answer |
|---|---|---|---|
| 1 | "Am I doing OK this month?" | Numbers + pace card | Narrative: what's normal, what changed, one thing to try |
| 2 | "Why is X so high?" | User has to dig | Coach explains: "12 Java House visits, avg KES 700, up 163% vs March" |
| 3 | "How do I save KES 20k?" | Not answered | Goal plan: cut Y by Z%, redirect to savings; here's the trade-off |
| 4 | "What if I stopped doing X?" | Not answered | What-if simulator: 12-month projection with visible assumptions |
| 5 | "Where's my money leaking?" | Quiet Leak Card (heuristic) | Ranked list with recipient-level context + one nudge each |
| 6 | "What should I invest?" | Not answered | Illustration only — "You could redirect KES X/mo; at 10% p.a. that's KES Y in 5yrs" (assumptions shown, no specific securities) |
| 7 | "This new merchant — what is it?" | Uncategorized | AI categorization for unknowns (rules engine handles knowns) |

Every job above is a **read-only advisory** job. The AI never acts on the user's behalf — it suggests, user acts. This keeps the surface area small and the liability contained.

---

## 4. Free vs Pro Split

**Stays free — non-negotiable, already promised in the Play Store listing and locked in by [`plans/pro-launch-plan.md`](pro-launch-plan.md):**

- SMS parsing, categorization via `KeywordRulesEngine` + user rules
- Budgets, forecast projection, recurring detection
- All charts, Analytics Grid (v1.5.2), Insights template cards (Weekly/Monthly/Quarterly Review workers)
- CSV export, database backup/restore, PIN + biometric
- Excel and PDF statement import
- Opt-in Firebase Analytics (shipped in 1.5.2 — separate from AI)

**Pro-only (new — this plan):**

| Feature | Anchors which product principle | Rough effort |
|---|---|---|
| **P1. Coach Insights** — 1 AI-generated narrative card/day on Home replacing today's template summaries for Pro users | Awareness before action; Save & invest by default | M (Phase 2) |
| **P2. Ask Your Money** — chat, grounded in aggregated data, Q&A + short-term memory | Awareness; Honest numbers | L (Phase 4) |
| **P3. What-If Simulator** — "if I cut X by Y% → save Z/mo → invested at r% → …" with visible assumptions | Save & invest by default | M (Phase 3) |
| **P4. Goal Planner** — user sets a savings goal, AI drafts a plan from their actual spend patterns | Save & invest by default | M (Phase 5) |
| **P5. Recipient-level Coach** — tap any recipient → "You spent X here, up Y% vs baseline, here's context" | Awareness | S (Phase 3) |
| **P6. Smart Categorization (unknowns only)** — cloud fallback for merchants the rules engine can't classify | (utility) | S (Phase 5) |

**Deferred from `pro-launch-plan.md` (Pro v1 template-based features — still valid but demoted from anchor):**
The template-based "Actionable Spending Recommendations" and "Deep Insights and Financial Coaching" sections of `pro-launch-plan.md` are now the *free-tier* Insights feed (already shipped). Pro replaces them with AI-generated versions of the same UX slots for Pro users. Same UI, better content.

---

## 5. The Six Pro AI Features

### P1. Coach Insights (Phase 2 — anchor)

**User-visible surface:** One card on the Home screen per day, replacing the template summary for Pro users only. Card contains: 1 headline, 1 body paragraph (≤ 3 sentences), 1 optional action button (deep-links into the app — e.g. "Open Food & Dining budget"), and an "assumptions" expander.

**When it runs:** Once per day, kicked off by the same `WeeklyReviewWorker` / `MonthlyReviewWorker` cadence but through a new `CoachInsightWorker`. Cached for 24 hours so the same insight is served on subsequent opens.

**What it's grounded in:** The `DataDigest` (§7). No individual transactions, no raw SMS.

### P2. Ask Your Money (Phase 4 — highest effort, ships last of the anchors)

**User-visible surface:** New tab on the Analytics screen (or new bottom-nav entry, TBD in phase 4 spec). Chat UI with the user's typed question and streamed assistant response. Last 10 turns kept in context; older turns evicted.

**Scope:** Read-only advisory — the AI can *suggest* the user open a screen or make a change; it cannot execute anything.

**Grounding:** Fresh `DataDigest` each turn (recomputed on-device) plus the last 10 turns of dialogue. No transaction bodies.

### P3. What-If Simulator (Phase 3)

**User-visible surface:** Off Analytics or Budget screen — "What if…?" button. User picks a category and a change (e.g. "cut by 30%"). AI returns a projected month-end delta, an annualized figure, and an **illustration-only** compound-growth line if the user invested the delta at a chosen return rate. Assumptions block mandatory.

### P4. Goal Planner (Phase 5)

**User-visible surface:** New "Goals" section in Settings or Analytics. User sets a KES target + timeframe. AI drafts a plan grounded in their real spending: which categories to trim, expected monthly saving, feasibility flag (e.g. "This goal requires cutting rent, which our data flags as recurring — likely infeasible without a lifestyle change").

### P5. Recipient-level Coach (Phase 3 — cheap add-on)

**User-visible surface:** Long-press or tap on any recipient in Expenses / Merchants → "Coach" action. AI returns 2–3 sentences: current-period spend, delta vs 3-month baseline, ranked context (e.g. "You spent more at this merchant this month than at any grocery store"). Optional single nudge.

### P6. Smart Categorization for Unknowns (Phase 5)

**User-visible surface:** No new UI. When a new SMS arrives and both the user-rules engine and `KeywordRulesEngine` return `UNCATEGORIZED`, an in-flight call to `/ai/categorize` returns a category ID. Falls back silently to `UNCATEGORIZED` if offline or rate-limited.

**Why bring this back after removal:** The removal noted in `_docs/implementation-status.md` (Gemini-based, free-tier, hit token caps) is what forced us to abandon it. With OpenAI paid backend + Pro-tier gating, cost per user is bounded and rate caps are structural, not surprise. The failure mode that killed it in v1 is now solved.

---

## 6. Architecture

```
┌─────────────────────── Android app (Kotlin, local-first) ────────────────────────┐
│                                                                                   │
│  ┌──────────────────────────────┐   ┌──────────────────────────────┐             │
│  │  Existing Free Layer          │   │  Pro AI Layer                │             │
│  │  • KeywordRulesEngine         │   │  • CoachInsightService       │             │
│  │  • Recurring detection        │   │  • AskYourMoneyService       │             │
│  │  • Budget forecast            │   │  • WhatIfSimulator           │             │
│  │  • Template summaries         │   │  • GoalPlanner               │             │
│  │  • Firebase analytics (opt-in)│   │  • SmartCategorizeUnknown    │             │
│  └──────────────────────────────┘   │  • RecipientCoach            │             │
│                                     └──────────────┬───────────────┘             │
│                                                    │                              │
│                    ┌───────────────────────────────┴──────────┐                  │
│                    │  DataDigestBuilder                       │                  │
│                    │  (aggregates local Room data →           │                  │
│                    │   privacy-safe JSON payload)             │                  │
│                    └───────────────────────────────┬──────────┘                  │
│                                                    │                              │
│                    ┌───────────────────────────────┴──────────┐                  │
│                    │  PesaTrackAiClient                       │                  │
│                    │  • Auth via Play Billing purchase token  │                  │
│                    │  • No API key in client                  │                  │
│                    │  • Provider-agnostic interface           │                  │
│                    └───────────────────────────────┬──────────┘                  │
└────────────────────────────────────────────────────┬──────────────────────────────┘
                                                    │ HTTPS
                                                    ▼
        ┌───────────── pesatrack-api.jmumo.com (Hetzner CPX21, shared VM) ─────────┐
        │                                                                          │
        │  Docker Compose project `pesatrack-api` on the `web` network             │
        │  Caddy proxy terminates TLS, reverse-proxies to pesatrack-api-web-1:3000 │
        │                                                                          │
        │  Node 20 + Express + Prisma                                              │
        │                                                                          │
        │  ┌──────────────────────────────────────────────────────────────────┐   │
        │  │  POST /ai/coach-insight     ← daily narrative from digest        │   │
        │  │  POST /ai/ask               ← chat, streaming, digest + history  │   │
        │  │  POST /ai/what-if           ← constrained simulation             │   │
        │  │  POST /ai/goal-plan         ← constrained plan generator         │   │
        │  │  POST /ai/categorize        ← unknown merchant → category ID     │   │
        │  │  POST /ai/recipient-coach   ← recipient-level advice             │   │
        │  │  POST /billing/verify       ← Play Billing token verification    │   │
        │  │  GET  /billing/entitlement  ← current Pro state for the caller   │   │
        │  └──────────────────────────────────────────────────────────────────┘   │
        │                                                                          │
        │  Cross-cutting:                                                          │
        │  • Auth middleware: Play Billing purchase token → cached entitlement    │
        │  • Rate-limit per user (see §8)                                          │
        │  • Digest hash cache (identical digest = free cache hit, no LLM call)   │
        │  • Provider adapter: OpenAiProvider (primary), stub for Groq/Gemini     │
        │  • Structured Outputs enforced — JSON schema per endpoint               │
        │  • Post-processing deny-list (securities, brokers, guaranteed returns)  │
        │  • Zero PII stored — logs only anonymized event metrics                 │
        └──────────────────────────────────────────────────────────────────────────┘
```

**Why proxy through backend (not client-direct to OpenAI):**

1. API key stays server-side, rotatable, unrevealable from a decompiled APK
2. Entitlement verification (Play Billing) can't be spoofed by client tampering
3. We can rate-limit, cache, and swap providers without shipping an app update
4. Cost visible & controllable — one OpenAI account, one dashboard
5. The Hetzner host already exists for StockUp / MaliScope / PesaTrack website — this reactivates the pattern for a real use case

**Why reuse the existing `backend/` folder in this repo (not delete + recreate):**

The `backend/` folder is already Node + Express + Prisma. The v1 Daraja/M-PESA STK code (never consumed by the Play Store app per AGENTS.md) is ripped out and replaced with the `/ai/*` and `/billing/*` routers. This preserves the folder-structure convention in AGENTS.md and reactivates a repo asset for a real use case. Deployment path: Hetzner Docker Compose per [`C:\Eng\StockUp\deploy\README.md`](../../StockUp/deploy/README.md) §4 ("Deploy another app").

---

## 7. Data Digest &amp; Privacy Contract

This is the linchpin. Nothing goes to the LLM except a **DataDigest** — aggregated, anonymized numbers.

### Digest schema (v1)

```jsonc
{
  "period": "2026-09",
  "month_start_day": 25,          // from user settings
  "days_elapsed": 17,
  "days_total": 30,
  "totals": {
    "spent": 42800,
    "spent_last_period": 38200,
    "spent_3mo_avg": 39500,
    "income_est": 85000,
    "invested_this_period": 6000
  },
  "categories": [
    {
      "id": 7,                    // stable category ID from app
      "name": "Food & Dining",    // display name — non-sensitive
      "spent": 12400,
      "budget": 10000,            // null if no budget set
      "3mo_avg": 9800,
      "cv": 0.18                  // coefficient of variation
    }
    // ... top 10 by absolute spend
  ],
  "recurring": [
    {
      "label": "Rent",            // user-set or inferred; non-sensitive
      "amount": 25000,
      "period": "MONTHLY",
      "confidence": 0.98
    }
    // ... top 5
  ],
  "top_recipients_this_period": [
    // Recipient NAMES are anonymized to opaque IDs on-device before sending.
    // Device keeps the id→name map locally; only the id is transmitted.
    // Rendered display re-hydrates the name from local map after LLM response.
    {
      "id": "r1",
      "spent": 8400,
      "count": 12,
      "category_id": 7,
      "3mo_avg": 3200
    }
    // ... top 8
  ],
  "anomalies_this_week": [
    { "type": "category_spike", "category_id": 16, "delta_pct": 68 }
  ]
}
```

### What is NEVER in the digest

- Raw SMS text (of any kind)
- Full recipient names (device maps `r1` → "Java House" *after* the LLM response, on-render)
- Individual transaction records
- Phone number, name, email, device ID, Google account ID
- Location, contacts, biometric data
- Any PII

### Privacy contract (surfaced to user)

Public copy for the Pro onboarding / privacy policy:

> **When you use PesaTrack AI, we send an anonymized spending summary to our AI provider (OpenAI) via our own servers. This summary contains:**
> - Category names and totals (e.g. "Food & Dining: KES 12,400")
> - Merchant *labels* replaced with opaque IDs — the actual merchant name never leaves your device
> - Your budget totals and recurring-expense summary
>
> **What we never send:**
> - The text of any SMS
> - Individual transaction details
> - Your phone number, name, or contacts

This can be honestly stated because the `DataDigestBuilder` is auditable code, not a marketing claim. See Phase 1 spec for the enforcement (a lint rule + unit test verifies no PII field is ever added to the digest struct).

---

## 8. LLM Guardrails

Because the LLM is producing advice about money, we need hard rails at four layers.

### 8.1 Prompt-level (soft)

- System prompt injects a small style guide: *"Neutral, factual. Frame savings as opportunity, never as shame. Second person, present tense. KES with thousands separator. Never recommend specific securities, brokers, or guarantee returns."*
- Prompt includes the current AGENTS.md copy guidelines verbatim in a `## Style` section.

### 8.2 Structured Outputs (hard — server-enforced)

Every endpoint requires OpenAI Structured Outputs strict-mode JSON matching a schema. Free-form prose is rejected server-side before it reaches the client. Example schema for Coach Insight:

```jsonc
{
  "type": "object",
  "required": ["title", "body", "assumptions"],
  "additionalProperties": false,
  "properties": {
    "title":            { "type": "string", "maxLength": 60 },
    "body":             { "type": "string", "maxLength": 400 },
    "action_label":     { "type": ["string", "null"], "maxLength": 40 },
    "action_deeplink":  { "type": ["string", "null"], "pattern": "^pesatrack://" },
    "saveable_amount_kes": { "type": ["integer", "null"], "minimum": 0 },
    "assumptions":      { "type": "array", "items": { "type": "string" }, "maxItems": 5 }
  }
}
```

Any response with a projection (`saveable_amount_kes` non-null OR body containing % or "would" or projected KES figures) triggers a **mandatory `assumptions[]` check** — empty assumptions array with a projection field present ⇒ reject and fall back.

### 8.3 Deny-list post-processing (hard — server-enforced)

Server regex-checks the assembled response for:
- Currency codes other than KES / K.Sh / KSh / Ksh / Kenyan shilling references
- Specific ticker symbols, broker names (SafariCom Sacco, Faulu, Britam, etc. — extendable list)
- Guarantee/return-promise language ("guaranteed return", "risk-free", "definitely will", "certainly grow")

Match ⇒ reject with fallback code, log, alert.

### 8.4 Fail-safe (never leak an error to the user)

- OpenAI 5xx / timeout / schema-reject / deny-list hit ⇒ server returns HTTP 200 with `{ "fallback": true }`
- Client, on `fallback: true`, silently renders the pre-existing template summary card instead
- User never sees "AI Coach is down." Pro value degrades gracefully to the free-tier UX. On repeated fallback, we surface an in-Settings-only "AI features degraded" indicator.

### 8.5 Rate limits

| Endpoint | Per-user daily cap | Per-user burst |
|---|---|---|
| `/ai/coach-insight` | 3 | 1/min (usually 1/day) |
| `/ai/ask` | 200 | 6/min |
| `/ai/what-if` | 30 | 3/min |
| `/ai/goal-plan` | 10 | 2/min |
| `/ai/recipient-coach` | 100 | 6/min |
| `/ai/categorize` | 500 (statement-import burst headroom) | 20/min |

Global circuit breaker: if OpenAI 5xx rate exceeds 5% over a 1-minute window, backend flips to "fallback" mode for all endpoints for 5 minutes. Post-mortem alert.

---

## 9. Provider Choice — Why OpenAI

Recorded here so future decisions have context. Full discussion in the 2026-09-11 planning chat.

### The Gemini token-exhaustion incident

A single user's statement import triggered enough categorization calls to hit Gemini's daily project-level token cap on the free tier. From the user's perspective this manifested as "the app stopped categorizing my transactions." This wasn't a bug we could fix in code — it was structural to Gemini's tier design at the time. That failure mode is unacceptable for a paid product.

### Why OpenAI wins for this product

1. **Structured Outputs strict mode.** All six Pro endpoints define JSON schemas. OpenAI *guarantees* schema conformance in strict mode. Gemini and Groq both offer JSON output but neither is as reliable at forced schema adherence, and our guardrails (assumption disclosure, deny-list) key off schema fields.
2. **Instruction-following for tone.** The mini-class models hold to a strict style guide better than Llama-family and Gemini Flash in practice. Tone matters more here than benchmark scores because the differentiator *is* the tone.
3. **Predictable paid-tier reliability.** No surprise daily caps, per-model rate limits scale with usage tier automatically, highest sustained uptime among the four. Fewer "AI Coach is down" support tickets.
4. **Cost is not a differentiator at this scale.** OpenAI is more expensive per token but still trivial per Pro user (see §10). Not worth chasing pennies at the cost of reliability.

### Why we still keep the backend provider-agnostic

- Future price/reliability shifts can be responded to without an app update
- If Groq's rate limits improve, we could route chat streaming to Groq for latency and keep OpenAI for structured JSON
- Anthropic sometimes wins on nuanced tone — worth being able to A/B

Concrete deliverable: a `AiProvider` interface in the backend (Phase 1) with `OpenAiProvider` implemented, `GroqProvider` and `GeminiProvider` as `TODO` stubs.

### Model choice

**Primary:** `gpt-4.1-mini` (or the current OpenAI "mini" workhorse at ship time), pinned by exact model ID — never `-latest`. Rationale:

- Sufficient reasoning for the six endpoints (none require frontier-class reasoning)
- ~1/10 the cost of frontier models
- Fast enough for chat streaming
- Strict Structured Outputs support

Escalation path if `-mini` proves insufficient for a specific endpoint (e.g. Goal Planner is nuanced): route only that endpoint to `gpt-4.1` (full) with cost tracked separately. Do not escalate globally.

---

## 10. Cost Model

Per Pro user per month, assuming a moderate use pattern.

| Endpoint | Calls/mo | ~In tokens | ~Out tokens |
|---|---:|---:|---:|
| Coach Insight | 30 | 500 | 400 |
| Ask (chat turns) | 10 | 1500 | 500 |
| What-If | 3 | 800 | 600 |
| Goal Plan | 1 | 1000 | 800 |
| Recipient Coach | 15 | 400 | 200 |
| Categorize (unknowns) | 20 | 200 | 20 |
| **Total per user/mo** |   | **~48K** | **~19K** |

At OpenAI `gpt-4.1-mini`-class pricing (illustrative, ~$0.15/1M in, $0.60/1M out — verify at implementation):
- Input: ~$0.007
- Output: ~$0.011
- **~$0.02 per user/month ≈ KES 2.60**

Power user at 10× usage: ~KES 26/user/month. Any monthly Pro price ≥ KES 200 has ≥ 87% gross margin at the extreme; ≥ 98% at typical usage. Hetzner CPX21 (~€6/month, already sunk cost for StockUp) hosts the backend at zero marginal.

**Hard monthly cap:** Set an OpenAI dashboard alert at 3× projected spend (based on active Pro seat count). Circuit-breaker + engineering alert on breach.

---

## 11. Rollout Phases

**Phase 0 — REJECTED.** Originally proposed free-tier upgrades (anomaly detection, fuzzy matching, P75 budget suggestions). Rejected 2026-09-11 by owner to focus effort on the Pro tier. Items moved to `plans/ai-features-plan.md` "Won't Do" section.

**Phase 1 — Pro plumbing (no user-visible AI).** Play Billing integration, entitlement store, `DataDigestBuilder`, backend scaffolding on Hetzner, `AiProvider` interface with `OpenAiProvider`, INTERNET permission + privacy policy update. Ships behind a hidden feature flag. **Spec:** [`plans/ai-pro-phase1-spec.md`](ai-pro-phase1-spec.md).

**Phase 2 — Coach Insights (P1).** The anchor. AI-generated Home card for Pro users, replacing template summary. Full prompt, schema, fallback path. **Spec:** [`plans/ai-pro-phase2-spec.md`](ai-pro-phase2-spec.md).

**Phase 3 — Recipient Coach (P5) + What-If Simulator (P3).** High-value quick wins after P1. Both share the digest & guardrail infra from Phases 1–2.

**Phase 4 — Ask Your Money (P2).** Full chat surface. Ships after 1–3 prove the digest+guardrail architecture works. Highest effort of the six.

**Phase 5 — Goal Planner (P4) + Smart Categorize Unknowns (P6).** Round out the Pro tier.

Each phase ships to closed testing → open testing → production and its metrics get 2 weeks of observation before starting the next.

---

## 12. Success Metrics

Per AGENTS.md product-principle #6 ("features must have observable success"):

| Feature | Success signal | Anti-metric (kill switch) |
|---|---|---|
| Coach Insights | % of Pro users who read the daily card ≥ 3 days/wk | AI-content flagged "not useful" > 30% |
| Ask Your Money | Median chat sessions/week ≥ 2; D30 retention lift vs non-chat Pro users | > 5% of responses fall back to template |
| What-If | % of simulations that end with the user opening Budget screen ≥ 25% | User complaints about "confusing math" |
| Goal Planner | % of goals still on-track at month 1 ≥ 50% | Goals set → abandoned within 24h > 40% |
| Recipient Coach | Tap-through rate on recipient rows ≥ 15% | (none, low downside) |
| Categorize (unknowns) | % of unknown SMS successfully categorized ≥ 70% | Category-correction rate > 30% |
| **Overall Pro** | Free → trial conversion, trial → paid conversion, monthly churn | Refund rate > 5%; support ticket rate > baseline × 2 |

Kill-switch triggers:
- Any privacy incident (PII leak in a request, log line, or LLM response) ⇒ immediate feature freeze, provider swap, public disclosure
- Cost/Pro-user exceeds 5× projection ⇒ throttle + investigate before scaling paying users
- Support-ticket volume from AI issues > support-ticket volume from all other issues ⇒ pause new-feature ships

---

## 13. Explicit Non-Goals &amp; Rejected Items

Recorded so we don't relitigate.

### Rejected on 2026-09-11 (owner decision)
- **Phase 0 free-tier improvements** (anomaly detection, fuzzy matching in `KeywordRulesEngine`, P75 budget suggestions). Owner chose to focus effort on Pro tier. Not "wrong" ideas — just deprioritized.
- **RevenueCat / paywall abstraction library.** Play Billing direct is simpler and avoids an ongoing 2% cut.
- **Free-tier AI teaser** (weekly free Coach Insight for non-Pro users). Owner opted for a clean paywall.

### Rejected on architectural grounds (not up for reconsideration)
- **On-device LLMs** (Gemini Nano, MediaPipe LLM, Llama on-device). Still 2–4 GB downloads, still device-fragmentation-hostile at `minSdk 26`.
- **Client-direct calls to OpenAI/any LLM.** API keys in an APK are recoverable; entitlement can be spoofed. Backend proxy is non-negotiable.
- **BYOK (bring-your-own-key).** Friction kills conversion. Considered as a post-v1 optional add-on, not v1.
- **AI-initiated writes.** The AI never mutates data. It suggests; user acts. Chat scope is read-only advisory (§2, decision #4).
- **Ads.** Never. PesaTrack has never shown an ad and never will. Cited in `_docs/releases.md` §1.5.2 telemetry rationale.

### Ruled out by product principles (AGENTS.md)
- Specific-security recommendations, guaranteed-return language, broker names
- Streaks or gamification-for-transactions
- Fear-framed copy ("you're losing money", "you overspent")
- Push notifications beyond the user's configured cadence
- Sending SMS text or transaction bodies to any server

---

## 14. Feature Decision Filter

Per AGENTS.md, every feature must answer these. Answered here at the strategic level; each phase spec re-answers per feature.

**1. Which principle does this serve?**
Primarily principles #1 (Awareness before action) and #3 (Save & invest by default). Every Pro AI feature must produce output that either surfaces a spending fact the user didn't know, or reframes a discretionary spend as a saveable/investable opportunity.

**2. What user behavior does it change?**
Awareness → Saving. The theory of change: users who see specific, personalized advice (not template numbers) are more likely to modify a specific spending pattern than users who see aggregate numbers. We validate via §12 metrics.

**3. What is the honest downside or failure mode?**
The LLM produces confident-sounding but wrong or unhelpful advice; the user acts on it and regrets it. Mitigations: mandatory assumption disclosure (§8.2), read-only advisory scope (never auto-action), fallback path to template summaries (§8.4), user complaint feedback loop (§12 anti-metrics).

**4. How is success observable to the user?**
The user can answer questions they couldn't answer before ("why is my food spending high?"). At the aggregate level, Pro-tier savings-rate lift is measurable via existing analytics.

---

## 15. Open Decisions (Remaining)

| # | Decision | Suggested default | Deadline |
|---|---|---|---|
| A | Exact monthly Pro price in KES | KES 299/mo, KES 2,400/yr (33% off; roughly 8 months) | Before Phase 1 code |
| B | Free trial length | 7 days at first launch, revisit after 3 months of data | Before Phase 1 code |
| C | Ask Your Money memory window | Last 10 turns (author rec); may drop to 6 if token cost surprises | Phase 4 |
| D | Chat streaming vs single-shot response | Streaming (better UX, standard SSE) | Phase 4 |
| E | Where the Pro settings screen lives | Settings → "PesaTrack Pro" | Phase 1 |
| F | Restore-purchase UX (user reinstalls app) | Auto-restore on first launch via BillingClient.queryPurchasesAsync + surfaced in Settings | Phase 1 |
| G | Whether to expose an in-app "AI is degraded" status when circuit breaker trips | Yes, small text-only banner in Settings > PesaTrack Pro, no home-screen intrusion | Phase 2 |
| H | Whether Pro is available in currencies other than KES at launch | No — KE-only at launch. Play Console geo-restricted. | Before Phase 1 code |

---

## Related Documents

- [`plans/ai-pro-phase1-spec.md`](ai-pro-phase1-spec.md) — Phase 1 detailed spec (Pro plumbing)
- [`plans/ai-pro-phase2-spec.md`](ai-pro-phase2-spec.md) — Phase 2 detailed spec (Coach Insights)
- [`plans/pro-launch-plan.md`](pro-launch-plan.md) — Historical (Pro v1, template-based, superseded by this plan)
- [`plans/ai-features-plan.md`](ai-features-plan.md) — Historical (2026-03-22 planning doc, superseded by this plan)
- [`plans/product-principles.md`](product-principles.md) — Long-form principles source
- [`plans/account-identity-policy.md`](account-identity-policy.md) — Identity roadmap (Pro v1 doesn't need identity; may revisit for Pro v2 cloud backup)
- [`plans/business-transition-plan.md`](business-transition-plan.md) — Broader business direction that this plan advances
- [`C:\Eng\StockUp\deploy\README.md`](../../StockUp/deploy/README.md) — Hetzner multi-app deployment reference
- [`AGENTS.md`](../AGENTS.md) — Agent instructions & product principles
