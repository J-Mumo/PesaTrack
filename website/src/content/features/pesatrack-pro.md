---
title: PesaTrack Pro
dek: An optional subscription that will add AI-powered coaching. Your transactions never leave the device — only anonymised summaries do, and only when you're subscribed.
order: 90
principle: save-and-invest
doesDo:
  - Verify Google Play subscriptions against our own backend at pesatrack-api.jmumo.com over HTTPS
  - Store only a SHA-256 hash of your Play purchase token server-side (never the raw token, never your Google account)
  - Show the current tier and renewal / expiry date if you're subscribed
  - Give you a "Restore purchase" affordance so a fresh install picks up an existing subscription without buying again
  - Route every AI-Pro-backend request through a single audit-checked HTTPS host — pinned in the app's network security config
doesNotDo:
  - Require a subscription for any feature that was previously free (SMS tracking, budgets, categories, imports, analytics, reviews, PIN lock — all stay free)
  - Send raw SMS content, transaction descriptions, merchant names, phone numbers, or account numbers off the device
  - Auto-renew silently — Google Play handles cancellation and refunds in the standard way
  - Track advertising IDs, cross-app behaviour, or anything beyond what [/privacy](/privacy) already discloses
  - Show any subscribe button before the Play Store tiers are published — the screen self-adapts to a "Coming soon" state until then
---

## What ships in v1.6.0

The PesaTrack Pro subscription screen is live in **Settings → PesaTrack
Pro**, and the Monthly and Annual tiers are published on Google Play. If
you open the screen today, you'll see the current pricing — the app
reads it live from Google Play so you always see what you'll actually
pay at purchase.

Nothing that was free in v1.5.x becomes paid in v1.6.0 or v1.7.0.
Automatic SMS tracking, category budgets, imports, analytics, Weekly /
Monthly / Quarterly / Year-in-Review reports, PIN lock, and every other
existing feature stay in the free tier and will keep doing so.

## What Pro adds in v1.7.0 — Coach Insights

The paid tier funds a coaching layer: one AI-generated observation
about your spending each day, grounded in your own M-PESA activity.
The full write-up lives on the [Coach Insights](/features/coach-insights)
page — including the exact digest that leaves your device, the
server-side content guardrails, and the rate limits.

Two things that specifically **don't** happen: (1) the app never
recommends specific stocks, brokers, SACCOs, or funds; server-side
deny-lists reject any response that does; (2) every projected saving
ships with a "Show assumptions" expander, so you can see the math the
model used.

## How the privacy story holds

Every AI feature runs on our backend at `pesatrack-api.jmumo.com` by
sending an *anonymised monthly digest* — the period label (`2026-09`),
per-category totals, and top-N recipients renamed to opaque IDs
(`r1..r5`). The map from `r1..r5` back to real merchants stays on your
device and never leaves it. See [/privacy §5.7](/privacy#billing) for
the full breakdown.

The Google Play purchase token is the only piece of AI-Pro data our
server stores across sessions, and we store only its SHA-256 hash — not
the raw token, not your Google account, not any personally identifying
value. Server logs surface only the first 16 hex chars of that hash for
triage.

## Cost and cancellation

The subscription runs through Google Play. You can cancel any time in
the Play Store; you keep access until the end of the paid period.
Refunds go through Google's standard flow. Pricing is set in Google
Play — we don't quote it here so you always see the current live price
at the moment of purchase.
