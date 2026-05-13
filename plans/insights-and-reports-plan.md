# PesaTrack — Insights & Reports Plan

> **Status:** Draft (v0.2 — decisions resolved). Not yet scheduled.
> **Owner:** TBD.
> **Related:** [AGENTS.md](../AGENTS.md) · [plans/product-principles.md](product-principles.md) · [plans/yoy-analytics-plan.md](yoy-analytics-plan.md) · [plans/analytics-charts-plan.md](analytics-charts-plan.md) · [plans/forecasting-plan.md](forecasting-plan.md)

---

## Why this exists

Today PesaTrack provides **features** (categorize, budget, review). This plan shifts the product toward providing **insights** — short, comparison-rich pieces of information the user could not easily produce themselves. Insights are how we justify the features:

- Categorization is justified by category insights.
- Budgets are justified by burn-down and pace insights.
- Reviews are justified by weekly, monthly, quarterly and yearly reports.
- The mission (better spending and investment culture) is served by every report mentioning investment headroom where honest.

**Rule of thumb:** every number we show carries a comparison (vs. last period, vs. average, vs. budget, vs. % of income) or a unit. Numbers without context are data; numbers with comparison are information.

---

## Mission alignment (Feature Decision Filter)

| Question | Answer |
|---|---|
| Which principle(s)? | 1 Awareness before action · 3 Save and invest by default · 5 Honest numbers · 6 Local-first |
| Behavior change? | Awareness → categorization → budgeting → saving/investing |
| Honest downside? | Wrong comparisons when history is thin (<4 weeks); we must label "limited data" and degrade gracefully. |
| Observable success? | User can answer: "How did this week / month / quarter / year compare?", "What's my biggest leak?", "How much could I have saved or invested?" — without leaving the app. |

---

## Scope

### Reports (cadences)

| Report | Cadence | Primary question it answers | Mentions investment framing? |
|---|---|---|---|
| **Weekly review** | **Thursday evening** (default Thursday 18:00 local) | "What just happened this week (past 7 days)?" | Optional (headroom only, if income set) |
| **Monthly review** | 1st of the following month | "How did last month land?" | **Yes** — savings/investing headroom |
| **Quarterly review** | 1st of new quarter | "Are my habits changing?" | **Yes** — quarter-over-quarter savings momentum |
| **Year-in-review ("Your Year")** | Late Dec / early Jan | "What did this year look like?" | **Yes** — annual savings + "what if invested" illustration |
| **On-demand reports** | User-triggered | "How much on X over Y window?" | Where relevant |

> **Why Thursday?** People are winding up the week and have time to go through reports. The "week" covers the **past 7 days** (rolling window ending on notification day), not a fixed Thu–Wed calendar window.
>
> **Removed from earlier brainstorm:** the daily snapshot. It conflicts with Principle 2 ("nudge, don't nag") and adds noise without enough signal.

### Insight cards (used inside reports and on Home)

Each card is a tiny, self-contained insight. Cards are reusable: the same `WeekHeadroomCard` may appear in the Weekly Review *and* on Home.

Initial card set (v1):

1. **Period Total Card** — total spent for the period + delta vs. previous period + daily average.
2. **Top 5 Categories Card** — *not the full list* — top 5 by spend with KES, % of period, and delta.
3. **Biggest Change Card** — single category with the largest absolute change vs. previous period.
4. **Quiet Leak Card** — high-frequency low-value category (count × avg). The "death by a thousand cuts" insight.
5. **Fees Paid Card** — total in category 606 for the period, with trend.
6. **Headroom Card** — committed spend so far vs. expected income for the period; framed as "available to save / invest."
7. **Pace Card** (month-only) — projected end-of-month total based on current daily run-rate, with last-month comparison.
8. **Savings/Investment Illustration Card** (monthly+) — for discretionary spend, *illustrative* "what if invested at X% annual" with all assumptions visible. Never a recommendation.
9. **Categorization Nudge Card** — surfaced only when uncategorized share > threshold; ties feature back to insight value.
10. **Budget Burn-Down Card** — for users with budgets; days until each category runs out at current pace.

---

## Where & how insights are surfaced

This is the surfacing matrix. Each insight has a **home surface** and may appear on **secondary** surfaces.

### Surfaces

| Surface | Purpose | Cadence of refresh |
|---|---|---|
| **Home screen** | At-a-glance "where am I now?" — a small set of high-signal cards. | On open |
| **Insights section (new, within Analytics)** | Sub-tab ("Insights" toggle alongside "Charts") within the existing Analytics screen. Browseable feed of recent insights and reports. | On open + on data change |
| **Analytics screen — Charts (existing)** | Deep charts and breakdowns (already mostly built; see [analytics-charts-plan.md](analytics-charts-plan.md), [yoy-analytics-plan.md](yoy-analytics-plan.md)). | On open |
| **Report screens (new)** | Full-screen Weekly / Monthly / Quarterly / Year-in-Review screens. Shareable as image (local-only). | On generation |
| **Notification** | One nudge per cadence at most. Tapping deep-links to the relevant Report screen. | Per report cadence |
| **Empty states** | Featureless screens (e.g., empty Budget screen) show one explanatory insight as motivation. | Static |
| **Category / expense detail** | Contextual mini-insights (e.g., "This merchant: 14 visits this month, KES 1,120 total"). | On open |

### Surfacing matrix (card × surface)

> **Home stays as it is in v1.** No new Insight cards are added to the Home screen — we don't want to crowd it before we've proven the value of cards on the Insights section and inside the Report screens. The Home column below is reserved for a possible future iteration once the Insights section sees usage.

| Card | Home (future) | Insights section | Weekly | Monthly | Quarterly | Yearly | Notification |
|---|---|---|---|---|---|---|---|
| Period Total | (future) | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ summary line |
| Top 5 Categories | (future) | ✓ | ✓ | ✓ | ✓ | ✓ | — |
| Biggest Change | — | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ headline |
| Quiet Leak | — | ✓ (when present) | ✓ | ✓ | — | — | — |
| Fees Paid | (future) | ✓ | ✓ | ✓ | ✓ | ✓ | — |
| Headroom | (future) | ✓ | — | ✓ | ✓ | ✓ | — |
| Pace | (future) | ✓ | — | — | — | — | ✓ if off-pace |
| Savings/Investment Illustration | — | ✓ | — | ✓ | ✓ | ✓ | — |
| Categorization Nudge | (future) | ✓ | ✓ | — | — | — | — |
| Budget Burn-Down | (future) | ✓ | ✓ | ✓ | — | — | ✓ if any category runs out |

### Surfacing rules

- **At most one proactive notification per cadence.** Weekly default ON (Thursday evening); Monthly default ON; Quarterly default ON; Yearly default ON. All user-toggleable. No daily.
- **Home is untouched in v1.** The current Home composition stays. Insight cards live in the Insights section (within Analytics) and inside Report screens. Re-evaluate adding Home cards in a later milestone, based on usage of the Insights section.
- **Limited-data label.** Any card with <4 weeks of history shows a small "limited data" tag and suppresses comparisons rather than fabricating them.
- **Dismissibility.** Every nudge card has a small "Not useful" affordance (stored locally — no upload). Used to tune ranking only.
- **No streaks, no badges, no marketing copy.** Per [product-principles.md](product-principles.md).

---

## Report anatomies

### Weekly Review (no investment framing)

```
Week of <start> – <end>

KES X spent · KES X/day average
↑/↓ N% vs. last week (KES X)

Biggest change
<Category>: KES X (↑/↓ KES X vs. last week)

Where it went — Top 5
1. <Cat>  KES X  P%
2. <Cat>  KES X  P%
3. <Cat>  KES X  P%
4. <Cat>  KES X  P%
5. <Cat>  KES X  P%
(<N> others: KES X)

Quiet leak
<Category>: <count> transactions, KES X total

Fees you paid this week
KES X

Headroom (this month)
KES X still in your <Month> budget · <D> days remaining
```

### Monthly Review (mentions investments)

```
<Month> <Year>

KES X spent · KES X/day average
↑/↓ N% vs. <previous month>

Biggest change
<Category>: KES X (↑/↓ KES X)

Where it went — Top 5
1–5 as above (others rolled up)

Fees you paid
KES X (↑/↓ KES X vs. previous month)

Quiet leak
<Category>: <count> · KES X

Pace
Final total: KES X (vs. KES X last month)

Headroom
KES X available after committed spend.

What this could have been
If KES X (your discretionary spend on <category(ies)>) were invested
at an illustrative N% annual return, in 12 months that's ~KES X.
Assumptions: rate=N% APY, compounded monthly. Not a recommendation.
```

### Quarterly Review (mentions investments)

```
Q<N> <Year>  ·  <Months>

KES X spent across <D> days · KES X/month average
↑/↓ N% vs. previous quarter

Where it went — Top 5 categories for the quarter
1–5 with KES, % of quarter, and ↑/↓ vs. previous quarter
(<N> others: KES X)

Biggest mover
<Category>: ↑/↓ KES X vs. previous quarter

Fees you paid this quarter
KES X  (↑/↓ KES X vs. previous quarter)

Savings momentum
Your quarterly headroom: Q-1 KES X · Q-2 KES X · Q-3 KES X · this Q KES X
Trend: ↑/↓ KES X per quarter

Investment framing (illustration)
At your current quarterly headroom, contributing the same amount each
quarter at an illustrative N% APY would compound to ~KES X over Y years.
Assumptions visible. Not a recommendation.
```

### Year-in-Review — "Your Year" (mentions investments)

```
<Year> in review

KES X spent across <D> days · KES X/month average
↑/↓ N% vs. <previous year>

Where it went — Top 5 categories for the year
1–5 with KES, % of year, and ↑/↓ vs. last year
(<N> others: KES X)

Biggest mover (year-over-year)
<Category>: ↑/↓ KES X

Fees you paid this year
KES X total · KES X/month average · ↑/↓ vs. last year

Quiet leaks (year)
Top 3 high-frequency low-value categories with totals

Your savings story
Months in headroom: N of 12
Best month: <Month> (KES X)
Total headroom across the year: KES X

What it could have been (illustration)
If KES X had been invested monthly at an illustrative N% APY,
in <H> years that's ~KES X. Assumptions visible. Not a recommendation.

Goals
<If a goal is set:> progress toward <KES X> goal: <P>%
```

> All four reports respect the Copy & UX Writing Guidelines in [AGENTS.md](../AGENTS.md): neutral framing, opportunity language, no fear, all assumptions visible.

---

## "Where it went" rule

For every report and the Top Categories card:

- Show **Top 5** by total spend for the period.
- Roll up the remainder as a single line: **"(N others: KES X)"**.
- Tapping the card / list opens the full breakdown in the **Charts** sub-tab of the Analytics screen.

This keeps the report scannable, preserves discoverability of the long tail, and avoids walls of text that violate Principle 2.

---

## Investment framing — rules

Required wherever a Monthly, Quarterly, or Yearly report appears.

- Framed as **illustration**, never recommendation.
- The annual rate is **fixed at 10% APY** (proxy for Kenya 91-day T-bill rate). Not user-configurable — keeps the UI simple and avoids implying specific investment advice.
- All assumptions visible inline: rate, compounding cadence, horizon, base amount.
- Never references specific securities, brokers, funds, or guaranteed returns.
- Never appears in the Weekly Review (window too short to be meaningful).

---

## Data sources

All required data already exists in Room. No schema changes required for v1; we may add **derived/cached aggregates** later for performance.

| Data | Source |
|---|---|
| Transactions, categories, amounts, timestamps | Existing `ExpenseEntity` + `CategoryEntity` |
| Transaction fees | Existing — category 606 |
| Recurring detections | Existing recurring service |
| Income (for headroom) | Existing income tracking / inferred from inbound M-PESA, with user override in Settings. **Headroom card only shown when income > 0.** |
| Budgets | Existing budget entities |
| Goals | New (light schema) — defer or piggyback off existing settings/preferences |
| Investment rate | Hard-coded constant: `INVESTMENT_ILLUSTRATION_RATE = 0.10` (10% APY). No DataStore needed. |

Performance: aggregate queries are bounded (12 months max for yearly view). Use existing repository APIs; add light caching only if profiling shows a need.

---

## Architecture

Follows existing patterns (MVVM + Hilt + Compose, see [AGENTS.md](../AGENTS.md)).

```
data sources → InsightsRepository → Insight Generators → InsightsViewModel(s) → Report Screens / Home / Insights Tab
```

- **`InsightsRepository`** (new, `@Singleton`) — pure read façade over existing DAOs; produces `PeriodAggregate` value objects.
- **`InsightGenerators`** (new, package `domain/insights/`) — small, individually-testable pure functions: `weeklyReview()`, `monthlyReview()`, `quarterlyReview()`, `yearlyReview()`, plus per-card generators (`topCategoriesCard`, `feesPaidCard`, `headroomCard`, `quietLeakCard`, `paceCard`, `biggestChangeCard`, `investmentIllustrationCard`).
- **`InsightsViewModel`** — exposes `StateFlow<InsightsUiState>` consumed by Home and the Insights tab.
- **`*ReportViewModel`** — one per report type (Weekly/Monthly/Quarterly/Yearly), each exposing its own UiState.
- **Notifications** — `WorkManager` periodic workers (one per cadence) generate the report, store the snapshot, post a single notification linking to the report screen.
- **Report storage** — New Room entity `ReportSnapshotEntity` (cadence, period start/end, generated timestamp, JSON payload of card data). Each Report screen queries stored snapshots for the "Previous reports" list. Notifications deep-link by snapshot ID.

### Testing strategy

- **Insight generators are pure functions** — unit-testable with fixed `PeriodAggregate` inputs. Each card generator gets its own test file (e.g., `QuietLeakCardGeneratorTest.kt`).
- **Repository layer** — tested via Room in-memory database.
- **ViewModels** — tested with fake repositories.

Detection of when a report should be generated:
- Weekly: every **Thursday 18:00 local**. The window is a **rolling past 7 days** ending on notification day.
- Monthly: on the 1st of the new month, 09:00 local.
- Quarterly: on the 1st of Apr/Jul/Oct/Jan, 09:00 local.
- Yearly: on Dec 28, 18:00 local (and again on Jan 2 if not viewed).

---

## UI surfaces — concrete changes

| Area | Change |
|---|---|
| **Home screen** | **Unchanged in v1.** No new Insight cards added. (Revisit after Insights section usage data.) |
| **Analytics screen — Insights section (new)** | New toggle/tab within the existing Analytics screen ("Insights" alongside "Charts"). Feed of generated reports + cards. No new bottom-nav entry. |
| **Report screens (new)** | 4 new screens: `WeeklyReviewScreen`, `MonthlyReviewScreen`, `QuarterlyReviewScreen`, `YearInReviewScreen`. Each follows `*Screen + *ViewModel + *UiState` pattern. Each report screen has a **"Previous reports"** section at the bottom listing stored past reports for that cadence. |
| **Analytics screen — Charts (existing)** | Unchanged in v1. Becomes the "deep dive" destination linked from cards. |
| **Settings** | New section: *Reports & Insights* — toggle each cadence's notification, set income (if not inferred). Investment rate is not configurable (fixed 10%). |
| **Notifications** | One channel per cadence (`weekly_review`, `monthly_review`, `quarterly_review`, `yearly_review`). Default ON, user can disable per channel. See *Notification anatomy* below. |
| **Empty states** | Budget screen empty state shows a stub Pace/Burn-Down card teaser. Categorize screen empty state shows a stub Categorization Nudge teaser. |

Navigation: each card has a primary tap target (deep link into the relevant Report or Analytics screen) and a secondary "Not useful" affordance.

---

## Notification anatomy

Notifications are the **only proactive channel** in v1 and live entirely under [Principle 2 ("nudge, don't nag")](product-principles.md). They are short, factual, and always deep-link into the relevant Report screen.

### Shared rules

- **One per cadence, never combined.** Maximum frequency per cadence: Weekly ~52/year, Monthly 12/year, Quarterly 4/year, Yearly 1/year.
- **Quiet hours respected.** No posts outside 08:00–21:00 local.
- **Channel per cadence.** Each can be muted independently in Android settings and in app Settings.
- **Style:** standard Android notification, small icon = PesaTrack mono icon, no big image in v1, no action buttons in v1 (tap to open). Group key shared so multiple cadences collapse if posted same day.
- **Title** is the cadence label. **Body** is one factual sentence with one comparison. **Subtext** (where shown) is the headline number.
- **Currency formatting** always `KES X,XXX`. Arrows: `↑` / `↓`. No emoji.
- **No marketing copy.** Ever.

### Mockups (visual layout)

#### Weekly review (Thursday evening)

```
┌───────────────────────────────────────────────┐
│  [icon] PesaTrack  · now                       │
│  Your week in review                            │
│  KES 6,840 spent this week ↑ 8% vs last week.   │
│  Biggest change: Transport ↑ KES 540.           │
└─────────────────────────────────────────────────┘
```
Tap target: `WeeklyReviewScreen`.

#### Monthly review (1st of month)

```
┌───────────────────────────────────────────────┐
│  [icon] PesaTrack  · now                       │
│  April in review                                │
│  KES 38,420 spent in April ↓ 4% vs March.       │
│  Headroom this month: KES 6,180.                │
└─────────────────────────────────────────────────┘
```
Tap target: `MonthlyReviewScreen`. The screen, not the notification, carries the investment illustration.

#### Quarterly review (1st of quarter)

```
┌───────────────────────────────────────────────┐
│  [icon] PesaTrack  · now                       │
│  Q1 2026 in review                              │
│  KES 112,300 spent across Jan–Mar ↑ 6% vs Q4.   │
│  Quarterly headroom: KES 18,420.                │
└─────────────────────────────────────────────────┘
```
Tap target: `QuarterlyReviewScreen`.

#### Year-in-review (Dec 28 / re-post Jan 2 if unviewed)

```
┌───────────────────────────────────────────────┐
│  [icon] PesaTrack  · now                       │
│  Your 2026 is ready                             │
│  KES 468,200 spent this year. Top category:     │
│  Food (24%). See your story.                    │
└─────────────────────────────────────────────────┘
```
Tap target: `YearInReviewScreen`.

#### Conditional: budget burn-down (only when a category will run out)

```
┌───────────────────────────────────────────────┐
│  [icon] PesaTrack  · now                       │
│  Transport budget pace                          │
│  At today's pace your transport budget runs     │
│  out on the 22nd (8 days early).                │
└─────────────────────────────────────────────────┘
```
Fires at most once per category per month, only if `projected_overrun >= 3 days`. Tap target: that category's budget detail.

### Copy templates (notifications)

Tokens in `{}` are filled at generation time.

- **Weekly title:** `Your week in review`
- **Weekly body:** `KES {total} spent this week {arrow} {pct}% vs last week. Biggest change: {category} {arrow} KES {delta}.`
- **Monthly title:** `{monthName} in review`
- **Monthly body:** `KES {total} spent in {monthName} {arrow} {pct}% vs {prevMonthName}. Headroom this month: KES {headroom}.`
- **Quarterly title:** `{quarterLabel} {year} in review`
- **Quarterly body:** `KES {total} spent across {monthsLabel} {arrow} {pct}% vs {prevQuarterLabel}. Quarterly headroom: KES {headroom}.`
- **Yearly title:** `Your {year} is ready`
- **Yearly body:** `KES {total} spent this year. Top category: {category} ({pct}%). See your story.`
- **Budget burn-down title:** `{category} budget pace`
- **Budget burn-down body:** `At today's pace your {category} budget runs out on the {dayOrdinal} ({daysEarly} days early).`

### Failure modes & guards

- If history is insufficient for the cadence, **skip the notification** rather than show comparisons against zero.
- If two cadences land on the same day (e.g., monthly + quarterly on Jan 1), post only the **longer-horizon** one and link to both reports from inside that screen.
- If the user opens the corresponding Report screen on their own before the scheduled time, **suppress** that notification.

---

## Copy templates (excerpt)

Copy must follow [AGENTS.md → Copy & UX Writing Guidelines](../AGENTS.md). Tokens in `{}` are filled at generation time. All amounts use `KES X,XXX` formatting.

- **Period Total:** `KES {total} spent · KES {avgPerDay}/day · {arrow} {pct}% vs. {prevLabel}`
- **Biggest Change:** `{category}: {arrow} KES {delta} vs. {prevLabel}`
- **Top 5 line:** `{rank}. {category}  KES {amount}  {pct}%`
- **Others rollup:** `({n} others: KES {amount})`
- **Quiet Leak:** `{category}: {count} transactions, KES {total} total`
- **Fees Paid:** `KES {total} in fees this {period}` (+ optional `· {arrow} KES {delta} vs. {prevLabel}`)
- **Headroom:** `KES {amount} available to save or invest`
- **Pace:** `At today's pace, you'll end {period} at KES {projected} ({arrow} KES {delta} vs. {prevLabel})`
- **Investment Illustration:** `If KES {amount} were invested at an illustrative {rate}% APY, in {horizon} that's ~KES {future}. Assumptions: {rate}% APY, monthly compounding. Illustration only — not a recommendation.`
- **Categorization Nudge:** `{pct}% of your spend is uncategorized. Categorizing unlocks category insights.`
- **Limited Data tag:** `Limited data — comparisons resume once you have {weeks} weeks of history.`

---

## Out of scope (v1)

- Daily snapshot (intentionally removed).
- Peer-cohort comparisons (privacy; deferred).
- Specific instrument / broker recommendations (forbidden by principles).
- Export to PDF/CSV (later — Year-in-Review may get a local-only "share as image").
- Cloud sync of insights or reports.
- Real-time push during the day (only cadence-bound notifications).

---

## Resolved decisions

1. **Investment illustration rate.** Fixed at **10% APY** (proxy for Kenya 91-day T-bill). Not user-configurable. Hard-coded constant.
2. **Income detection.** Both — auto-infer from inbound M-PESA + user override in Settings. Headroom card only shown when income > 0.
3. **"Quiet leak" threshold.** ≥8 transactions in the period AND average ≤ KES 300. Stored as named constants, tunable after dogfood.
4. **Insights placement.** Sub-tab within the existing Analytics screen ("Insights" | "Charts"). No new bottom-nav entry.
5. **Limited-data threshold.** 4 weeks. Show "limited data" badge and suppress delta comparisons.
6. **Report storage.** Reports are stored as lightweight snapshots (Room entity or JSON in Room) so notifications deep-link to the exact generated report. Each Report screen shows a "Previous reports" section at the bottom. Re-compute only on explicit user refresh.

---

## Rollout (suggested order)

1. **v1.0** — Weekly Review (screen + Thursday-evening notification + report storage + "Previous reports" list). Home stays unchanged. Investment framing **not yet** included.
2. **v1.1** — Monthly Review (screen + notification + storage) + Investment Illustration card on the Monthly Review screen.
3. **v1.2** — Insights section within Analytics + Categorization Nudge + Pace card + Quiet Leak card.
4. **v1.3** — Quarterly Review + Budget Burn-Down card + budget burn-down notification.
5. **v1.4** — Year-in-Review ("Your Year") + Investment Illustration at yearly horizon + optional share-as-image.
6. **v1.5 (revisit)** — Evaluate adding selected Insight cards to Home, based on Insights section usage and user feedback.

Each milestone independently shippable. Each milestone re-runs the Feature Decision Filter at PR time.

---

## Auto-update reminder

Per [AGENTS.md](../AGENTS.md), every implemented milestone of this plan **must** update [_docs/implementation-status.md](../_docs/implementation-status.md):
- New screens, routes, ViewModels, repositories.
- New notification channels.
- New Settings entries.
- New DataStore keys (report cadence toggles, income override).
- Any new derived/cached aggregates if added later.
