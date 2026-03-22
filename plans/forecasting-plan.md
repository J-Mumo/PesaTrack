# Forecasting Plan — Budget Burn Rate & Spending Projections

> **Status:** ⏳ Deferred — revisit after budgets are tested with real users
> **Depends on:** M7 (Category-Based Budgets) ✅, Recurring Expense Detection ⏳
> **Estimated effort:** ~9–11 hours total (Phase A alone: ~2–3 hours)

---

## Problem Statement

Budgets tell users what they *planned* to spend. Budget alerts tell them when they've *already* overspent. There's a gap: **nobody warns them mid-month that they're on track to overshoot**.

> "At your current pace, you'll exhaust your Food & Dining budget by March 25th."

Forecasting bridges backward-looking analytics and forward-looking budget control.

---

## Prerequisites & Why We're Deferring

1. **Budget adoption is untested** — M7 budgets haven't shipped to real users yet. If nobody sets budgets, there's nothing to forecast against.
2. **Needs data maturity** — Linear projections need ≥5 days in a period; seasonal models need ≥3 months of history. New users get garbage.
3. **Recurring expense detection (pending)** — A KES 35,000 rent payment on day 1 makes projections absurd for the rest of the month without knowing it's a one-time fixed cost.
4. **The MVP is trivially quick** — ~2–3 hours when we're ready. No rush to build speculatively.

**Trigger to revisit:** When real users have been using budgets for ≥1 month and recurring expense detection is in progress.

---

## Existing Building Blocks

Everything needed for the MVP forecast already exists in the codebase:

| Asset | Location | What It Gives Us |
|-------|----------|-----------------|
| Daily spending totals | [`ExpenseDao.getDailyTotalsForMonth()`](../android/app/src/main/java/com/pesatrack/data/local/database/dao/ExpenseDao.kt:311) | Day-by-day spend pattern within a month |
| Monthly trend (6 months) | [`ExpenseRepository.getMonthlyTotals()`](../android/app/src/main/java/com/pesatrack/data/repository/ExpenseRepository.kt:17) | Historical monthly totals for regression |
| Category monthly trends | [`ExpenseDao.getCategoryMonthlyTotals()`](../android/app/src/main/java/com/pesatrack/data/local/database/dao/ExpenseDao.kt:373) | Per-category monthly history |
| CV-based volatility (μ, σ, CV) | [`AnalyticsViewModel.buildCategoryTrend()`](../android/app/src/main/java/com/pesatrack/presentation/screens/analytics/AnalyticsViewModel.kt:429) | Already computes mean/stddev per category |
| Budget limits + progress | [`BudgetRepository.getBudgetProgressList()`](../android/app/src/main/java/com/pesatrack/data/repository/BudgetRepository.kt:158) | Budget amount, spent so far, % used |
| Period range helpers | [`BudgetRepository.getCurrentPeriodRange()`](../android/app/src/main/java/com/pesatrack/data/repository/BudgetRepository.kt:90) | Start/end of current week/month/year |
| Avg daily spend | [`AnalyticsViewModel.loadMonthData()`](../android/app/src/main/java/com/pesatrack/presentation/screens/analytics/AnalyticsViewModel.kt:246) | Already computed for current month |
| Group spending in range | [`ExpenseDao.getGroupSpendingInRange()`](../android/app/src/main/java/com/pesatrack/data/local/database/dao/ExpenseDao.kt:509) | Per-group budget spending query |

**No new database tables needed. No schema migration. Pure computation on existing data.**

---

## Forecasting Models

### Model 1: Linear Burn Rate (MVP)

The simplest useful forecast. Zero new DAO queries — just math on top of existing `BudgetProgress`.

**Algorithm:**
```
daysElapsed = today - periodStart
dailyBurnRate = spent / daysElapsed
projectedEndOfPeriodSpend = dailyBurnRate × totalDaysInPeriod
exhaustionDate = periodStart + (budgetAmount / dailyBurnRate)  // null if on track
safeDailyBudget = (budgetAmount - spent) / daysRemaining
```

**Example:**
- Budget: KES 15,000/month for Food & Dining
- Spent so far (day 21): KES 12,600
- Daily burn rate: KES 600/day
- Projected month-end: KES 18,600 (124%)
- Exhaustion date: day 25 → **"Budget runs out ~March 25th"**
- Safe daily budget: (15,000 − 12,600) / 10 = **KES 240/day**

**Pros:** Dead simple, no new data queries needed
**Cons:** Assumes uniform spending (ignores salary-day spikes, weekend patterns)

---

### Model 2: Weighted Recent Days

Weight recent days more heavily to catch acceleration/deceleration.

**Algorithm:**
```
last7DayAvg = sum(last 7 days spending) / 7
last14DayAvg = sum(last 14 days spending) / 14
weightedDailyRate = 0.6 × last7DayAvg + 0.4 × last14DayAvg
projectedRemaining = weightedDailyRate × daysLeftInPeriod
projectedTotal = spent + projectedRemaining
```

**Why it's better:** If spending accelerated in the last week, Model 1 under-estimates; this catches it.

**New DAO query needed:** Windowed variant of `getGroupSpendingInRange()` for last-7-day and last-14-day totals.

---

### Model 3: Day-of-Week Seasonal Pattern

People spend differently on weekdays vs weekends, and around pay day (typically 25th–1st in Kenya).

**Algorithm:**
1. Build a 7-slot day-of-week profile from last 3 months of daily totals
2. For each remaining day in the period, use that weekday's historical average
3. Sum to get projected end-of-period total

**New DAO query needed:**
```sql
SELECT
    strftime('%w', timestamp / 1000, 'unixepoch', 'localtime') AS dayOfWeek,
    AVG(dailyTotal) AS avgDailySpend
FROM (
    SELECT date(timestamp / 1000, 'unixepoch', 'localtime') AS spendDate,
           SUM(amount) AS dailyTotal, timestamp
    FROM expenses WHERE isExcluded = 0 AND timestamp >= :sinceMs
    GROUP BY spendDate
)
GROUP BY dayOfWeek
```

**Why it's better:** Captures weekend restaurant splurges, Monday grocery runs, end-of-month pay-day spikes.

---

## Domain Model

```kotlin
data class BudgetForecast(
    val budget: Budget,
    /** Actual spending so far in this period. */
    val spent: Double,
    /** Computed daily burn rate (Model 1: linear; Model 2+: weighted). */
    val dailyBurnRate: Double,
    /** Date the budget is projected to be exhausted. Null if on track to stay under. */
    val exhaustionDate: Long?,
    /** Projected total spend at end of period. */
    val projectedTotal: Double,
    /** Projected % of budget at end of period. */
    val projectedPercentage: Double,
    /** Remaining budget divided by remaining days. */
    val safeDailyBudget: Double,
    /** Days remaining in the budget period. */
    val daysRemaining: Int,
    /** Days elapsed in the budget period. */
    val daysElapsed: Int
) {
    val isProjectedOverBudget: Boolean
        get() = projectedPercentage > 100.0

    val isExhaustionImminent: Boolean
        get() = exhaustionDate != null && daysRemaining > 0 &&
                (exhaustionDate - System.currentTimeMillis()) < 5 * 86_400_000L
}
```

---

## Architecture

```
┌─────────────────────────────────────────────────────┐
│                    ForecastService                   │
│         (pure Kotlin, Hilt @Singleton)               │
│                                                      │
│  Input:  BudgetRepository + ExpenseRepository        │
│  Output: List<BudgetForecast>                        │
│                                                      │
│  Methods:                                            │
│   • getForecastsForActiveBudgets(): List<BudgetForecast>  │
│   • getForecastForBudget(budget): BudgetForecast     │
│   • shouldShowForecastAlert(budget): Boolean          │
├─────────────────────────────────────────────────────┤
│  No new DB tables. No schema migration.              │
│  Pure computation on top of existing queries.        │
└─────────────────────────────────────────────────────┘
         │                    │                  │
         ▼                    ▼                  ▼
   HomeViewModel       AnalyticsViewModel   BudgetService
   (forecast card)     (projection line)    (forecast notifs)
```

**File placement:** `services/ForecastService.kt` — alongside existing `BudgetService.kt`.

---

## UI Surfaces

### 1. Home Screen — Forecast Card

Below the existing budget summary card on `HomeScreen`:

```
┌──────────────────────────────────────────┐
│  🔮 March Forecast                       │
│                                          │
│  Projected month-end:    KES 87,200      │
│  Budget:                 KES 80,000      │
│  ⚠️ ~KES 7,200 over budget               │
│                                          │
│  Safe daily spend:       KES 1,760/day   │
│  Days remaining:         10              │
│                                          │
│  🔴 Food — runs out ~March 25th          │
│  🟡 Transport — on track (85%)           │
│  🟢 Shopping — comfortable (62%)         │
└──────────────────────────────────────────┘
```

**Show conditions:**
- User has ≥1 active budget
- ≥5 days elapsed in the current period (projections before day 5 are unreliable)

**New UI state fields on `HomeUiState`:**
```kotlin
val budgetForecasts: List<BudgetForecast> = emptyList()
val showForecastCard: Boolean = false
```

### 2. Analytics Screen — Projection Line

On the existing monthly daily spending chart, overlay a **dashed projection line** from today to end-of-month, plus a horizontal **budget ceiling line**.

The solid line is actual cumulative spending through today. The dashed portion (tomorrow → end of month) is the linear projection. Where the projection crosses the budget line is visually obvious.

### 3. Budget Screen — Per-Budget Forecast

Each budget card in `BudgetScreen` gets a subtitle:
- "On track — projected 72% by month-end"
- "⚠️ Projected 118% — runs out ~March 25th"
- "Safe spend: KES 340/day for 8 remaining days"

### 4. Proactive Notifications

Extend `BudgetService` to check forecasts after each expense save:

| Trigger | Notification |
|---------|-------------|
| Projected ≥110% with ≥7 days remaining | "📊 Food & Dining: On track for KES 18,600 (124%). KES 240/day to stay on budget." |
| Exhaustion < 5 days away | "⏰ Food & Dining budget runs out in ~4 days. KES 2,400 remaining." |

**Throttle:** Max 1 forecast notification per budget per day. Don't fire if the actual 80%/100% alert already fired today.

---

## Implementation Phases

| Phase | Scope | New Files | Modified Files | Effort |
|-------|-------|-----------|----------------|--------|
| **A** (MVP) | Linear burn rate → `BudgetForecast` + `ForecastService` + Home forecast card | `ForecastService.kt`, `ForecastModels.kt` | `HomeViewModel.kt`, `HomeUiState.kt`, `HomeScreen.kt`, `AppModule.kt` | ~2–3h |
| **B** | Projection line overlay on Analytics daily chart + budget ceiling line | — | `AnalyticsScreen.kt`, `AnalyticsViewModel.kt`, `AnalyticsUiState.kt` | ~2h |
| **C** | Proactive forecast notifications (daily throttle) | — | `BudgetService.kt`, `NotificationHelper.kt`, `AppPreferences.kt` | ~1–2h |
| **D** | Weighted-recent-days model (7d/14d weighting) | — | `ForecastService.kt`, `ExpenseDao.kt` (+1 query) | ~1h |
| **E** | Day-of-week seasonal model | — | `ForecastService.kt`, `ExpenseDao.kt` (+1 query) | ~2h |
| **F** | Budget screen per-card forecast subtitles | — | `BudgetScreen.kt`, `BudgetViewModel.kt`, `BudgetUiState.kt` | ~1h |

**Phase A is the only must-have. Phases B–F are progressive enhancements.**

---

## Edge Cases & Guardrails

| Edge Case | Handling |
|-----------|---------|
| **< 5 days into period** | Don't show forecast card. Linear extrapolation from 1–4 data points is noise. Fall back to "Not enough data yet." |
| **Zero spending so far** | Show "No spending recorded yet this period" instead of a projection. |
| **Large one-time expense (rent)** | Without recurring detection, this inflates the burn rate. Mitigation: show "including KES 35,000 one-time transaction" disclaimer, or offer to exclude from projection. Full fix requires recurring expense detection. |
| **Category forecast vs Total forecast** | If both exist, show Total forecast first, then per-category breakdowns sorted by projected overspend. |
| **User changes budget mid-period** | Recompute forecast with new amount. Spent stays the same. No special handling needed. |
| **Investment group in Total budget** | Exclude group 18 (Investment & Savings) from Total budget forecasts to prevent MMF transfers from skewing projections. Consistent with M6 design philosophy. |
| **Weekly budgets** | Same algorithm, just shorter period. Forecast becomes usable by Wednesday. |

---

## Open Questions (For Future Decision)

1. **Confidence intervals?** Using existing CV/σ from `CategoryTrend`, we could show "Projected: KES 16,000–19,200" instead of a point estimate. More honest, but adds UI complexity.

2. **Recurring expense awareness?** When recurring detection ships, forecasts should factor in known upcoming fixed costs (rent, subscriptions). E.g., "KES 35,000 rent expected on 1st" is added to the projection regardless of burn rate.

3. **Forecast accuracy tracking?** Save end-of-period projections and compare to actuals. Over time, show "forecast accuracy: 87%" to build user trust. Nice-to-have.

4. **Quick action from forecast?** E.g., forecast card has a "Reduce spending" button that opens a tips view or adjusts budget. Probably over-engineered for now.

---

## Summary

Forecasting is the **natural Phase 2 of budgets** — it transforms budgets from reactive alerts into proactive spending guidance. The MVP (Phase A) requires no new database tables, no schema migration, and ~2–3 hours of work. It's ready to build whenever budget adoption is validated with real users.
