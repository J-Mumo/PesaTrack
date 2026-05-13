# PesaTrack — Product Principles

> **Status:** Living document. Update via PR. Referenced by [AGENTS.md](../AGENTS.md).
>
> **Audience:** Every contributor and AI agent working on PesaTrack.

---

## Mission

Help people improve their finances by building a better **spending and investment culture**.

PesaTrack exists to turn passive M-PESA transaction noise into **awareness**, then into **better habits** around what users spend, save, and invest. The app earns its place on the user's phone only if, over time, the user has more clarity and more money than they would have without it.

---

## The Six Principles

These are decision tiebreakers. When two implementations are both technically valid, pick the one that better serves the principles. When a principle conflicts with a short-term metric (engagement, retention, install count), the principle wins.

### 1. Awareness before action
Surface facts the user didn't know before suggesting what to do about them.

- A new feature should first answer *"what is happening with my money?"* before *"what should I do?"*
- Show data the user can verify against their own SMS / M-PESA app. Trust is built by being correct on small things first.

### 2. Nudge, don't nag
At most one proactive insight per session. Never dark patterns. Never fear framing.

- Notifications are a budget, not a channel. If we wouldn't send it to a friend, we don't send it to the user.
- The user's attention is the scarcest resource we touch. Treat it as such.

### 3. Save and invest by default
Every analytics surface should, where honest, include a "what you could save / invest" framing.

- Dashboards default to showing not just *spend*, but *headroom*: money available after committed spend.
- Recurring spend is a savings opportunity until proven otherwise.
- Investment framing is **illustrative, not prescriptive** — never a recommendation of a specific instrument.

### 4. Privacy is non-negotiable
Local-first. No PII leaves the device without explicit, revocable consent.

- SMS bodies, transaction details, and any derived insights stay on the device by default.
- If a future feature requires upload, it must be opt-in, scoped, and explained in plain language at the point of opt-in.
- Logs must never contain raw SMS bodies or full account identifiers.

### 5. Honest numbers
No projections without surfacing assumptions. No "savings" that ignore costs.

- Forecasts, "what-if invested" screens, and goal projections must show the assumed rate, horizon, and base period.
- Transaction fees (currently category 606) are real money out — never bury them inside discretionary totals.
- When data is incomplete (e.g., only 3 weeks of history), say so on the surface that uses it.

### 6. Local-first
Features must degrade gracefully offline.

- The shipped app does not depend on the backend. New features inherit this constraint until explicitly authorized otherwise.
- If a feature needs connectivity for full value, it must still provide partial value offline (or not ship).

---

## Feature Decision Filter

Every new feature (and any non-trivial change) must answer these — in the plan, the PR description, or both:

1. **Which principle(s) does this serve?** If none, reconsider.
2. **What user behavior does it change?** (awareness / spending / saving / investing / none)
3. **What is the honest downside or failure mode?** What if the underlying model is wrong?
4. **How is success observable to the user?** Even if it's just *"the user can answer X question they couldn't answer before."*

Agents proposing features from `_docs/brainstorm.md` should run them through this filter before scheduling.

---

## Mission-Aligned Defaults

The mission is expressed mostly through **defaults**, not toggles. Users should not have to find the "make me better with money" switch.

| Surface | Neutral default (avoid) | Mission-aligned default |
|---|---|---|
| Home / dashboard | Total spent this month | Spend + **available headroom** + comparison to last month |
| Category drill-down | List of expenses | List + **% of income** + savings opportunity if recurring |
| Transaction costs (cat 606) | Hidden as a line item | Surfaced as **"fees paid this month"** with trend |
| Forecasting | Predicted spend | Predicted spend **+ predicted leftover to save/invest** |
| Budgets | Limit per category | Limit + **suggested savings target** with rationale |
| Recurring detection | List of recurring expenses | List framed as **"subscriptions to review"** |
| Onboarding | Permissions + categories | + brief **goal capture** (save / cut / invest) |
| Annual / monthly review | (none) | **"Your Year/Month"** screen: top categories, fees paid, wins, leaks |

---

## Investment Culture Without a Brokerage

We don't move money. We change how the user thinks about money they already move.

Acceptable, local-only investment-culture features:

- **Savings opportunities feed** derived from recurring detection.
- **"What if you'd invested it?"** counterfactual: user-configurable annual return, applied to selected discretionary categories. Always shown with assumptions.
- **Local round-up tracker:** virtual round-ups on M-PESA transactions; running "would-be-saved" total. No funds movement.
- **Goal screen:** user-defined KES savings/investment goal; monthly progress from net-of-spend estimate.
- **Annual review ("Your Year")** that highlights fees paid, biggest leaks, and biggest wins.

Not acceptable (without an explicit, separate plan):

- Recommending specific securities, brokers, funds, or guaranteed returns.
- Moving money or initiating payments on the user's behalf.
- Sharing user financial data with third parties.

---

## Copy & UX Writing Rules (summary)

Full version lives in [AGENTS.md](../AGENTS.md) under *Copy & UX Writing Guidelines*. Short version:

- Neutral and factual, never shaming.
- Opportunity framing over fear framing.
- Numbers always carry context (comparison or unit).
- KES with thousands separators; second person; present tense.
- No streaks/badges that reward more transactions.
- Investment math is **illustration**, not advice.

---

## Anti-Patterns (explicit list)

Refusing these defines the product as much as accepting the principles:

- Engagement-for-its-own-sake (streaks, daily-open rewards).
- Gamifying spending volume.
- Hidden defaults that favor the app's interests over the user's.
- Marketing copy inside spending-insight notifications.
- Projections without assumptions.
- Treating transaction fees as ordinary discretionary spend.
- Connectivity requirements for previously-offline behavior.
- Any third-party SDK that exfiltrates transaction data.

---

## Process Hooks

How the principles show up in day-to-day work:

- **PR description** answers the Feature Decision Filter (4 questions).
- **Commit scope tags** (recommended): `feat(awareness):`, `feat(savings):`, `feat(investing):`, `feat(privacy):`, alongside the usual `fix:` / `chore:` / `refactor:`.
- **Release notes** ([_docs/releases.md](../_docs/releases.md)) describe the **user-facing behavior change**, not just the technical one.
- **Brainstorm triage** ([_docs/brainstorm.md](../_docs/brainstorm.md)) tags each idea with the principle it serves before scheduling.

---

## Measurement (without telemetry)

Because we are local-first, we don't ship analytics. We measure the mission via:

- **In-app "Your Month / Your Year" screens** — the user sees the mission working for them. This *is* the loop.
- **Optional, opt-in, on-device feedback** — a "Was this insight useful?" thumbs after surfaced insights, stored locally only, used to tune on-device ranking. No upload.

---

## Open Questions

Tracked here so principles evolve with evidence, not by drift:

- What is the right default annual-return assumption for the "what if invested" counterfactual in the Kenyan context? (T-bill? MMF? User input only?)
- How aggressive should subscription-review prompts be without becoming nagging?
- When (if ever) is opt-in cloud sync compatible with Principle 4? See [plans/cloud-sync-playstore-impact.md](cloud-sync-playstore-impact.md).

---

_This document is a living contract between the product, the user, and the agents that build the product. Update it deliberately._
