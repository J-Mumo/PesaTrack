---
title: Coach Insights
dek: One AI-generated observation about your spending each day, grounded in your own M-PESA activity. Ships with PesaTrack Pro from v1.7.0.
order: 95
principle: awareness-before-action
doesDo:
  - Show a single, specific spending observation on the Home screen each day when you're a Pro subscriber
  - Ground every observation in your own aggregated numbers — no generic "spend less on coffee" advice
  - Include an "assumptions" expander whenever the insight projects a saveable amount, so you can see the math
  - Fall back silently to the free-tier Home content when the AI service is unavailable — no error toast, no "AI unavailable" text ever
  - Cache the current day's insight on-device for 24 h so the same digest is not billed twice against your entitlement
doesNotDo:
  - Send raw SMS content, individual transaction rows, real merchant names, phone numbers, or account details off the device
  - Recommend specific stocks, brokers, SACCOs, or funds — server-side guardrails reject any response that does
  - Claim guaranteed returns or use fear framing — server-side guardrails reject any response that does
  - Show more than three Coach Insights in any 24-hour window — rate limits guarantee this
  - Send any digest before you subscribe, or after your subscription lapses
---

## What ships in v1.7.0

Every day, one Coach Insight card appears on your Home screen when
you're subscribed to **PesaTrack Pro** — a compact card with:

- A one-line title stating the observation.
- A 2–3 sentence body with at least one KES figure grounded in your
  own recent spending.
- An optional saveable-amount pill ("Could save ~ KES 1,200") — always
  paired with a "Show assumptions" expander so you can see what the
  model took as given.
- An optional action button ("See Food breakdown", "See recurring
  costs") that deep-links to the existing screen where the point lives.

If the AI service is unavailable, the network is offline, the response
fails our content guardrails, or your subscription lapses, the card
silently disappears and the free-tier Home content renders unchanged.
No error toast, no "AI unavailable" copy — the goal is that you never
see a broken feature, only a present or absent one.

## The digest we send

Only an *anonymised* spending summary leaves the device. Every AI
request contains:

- The period label (e.g. `2026-09`) and how far into it you are
  (day 15 of 30).
- Aggregated category totals for the current period, the previous
  period, and a three-month rolling average — whole KES only, category
  names are the app's own labels (e.g. "Food & Dining"), never
  personal.
- Opaque recipient IDs (`r1`, `r2`, … through `r8`) with per-id
  totals, transaction counts, and best-guess category. **IDs are
  assigned locally on your device per request** — the same merchant
  can receive different IDs on different days, so the backend cannot
  correlate a merchant across users, sessions, or requests.
- Detected recurring expense summaries (label, cycle, amount,
  confidence) plus current-period totals for income and money moved
  into savings.

What we *never* send: raw SMS content, individual transaction rows,
real merchant/recipient names, phone numbers, account numbers, contacts,
device IDs, or Google account details. Name substitution happens on
your device after the AI response returns; the ID-to-name map is held
in memory only for the duration of a single request.

See [/privacy §5.8](/privacy#coach-insights) for the full breakdown.

## Server-side guardrails

Every response is validated on three layers before it reaches your
device:

1. **OpenAI Structured Outputs strict mode** enforces the response
   schema at generation time — every field must match a hand-written
   contract.
2. **Post-validate** cross-checks the response against your digest:
   any reference to a recipient you didn't include, any foreign
   currency mention (USD/EUR/GBP), or any projected saving without
   listed assumptions is rejected.
3. **Deny-list scrub** rejects any response mentioning guaranteed
   returns, specific broker names, or "buy XYZ shares" patterns —
   maintained as a JSON file that ops can edit without a code deploy.

Any rejection sends a silent fallback envelope to your device; the
free-tier Home card renders.

## Rate limits and cost

- **3 requests / day per subscriber** — headroom over the once-daily
  intent for the case where you pull-to-refresh or generate a fresh
  insight after categorising a batch of new transactions.
- **1 request / minute** — burst guard against double-tap flooding.
- **24 h per-digest cache** — if a fresh insight would use the same
  aggregated numbers as an earlier request, we return the cached
  response instead of making a new AI call.

Cancel your PesaTrack Pro subscription in Google Play to stop all AI
feature usage from your device. Existing cached insights are cleared
when the subscription entitlement lapses.
