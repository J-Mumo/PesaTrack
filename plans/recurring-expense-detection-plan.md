# Recurring Expense Detection Plan

## Problem Statement

Users have recurring expenses (rent, subscriptions, SACCO contributions, loan repayments, utilities) that follow predictable patterns — same recipient, similar amount, regular interval. PesaTrack currently treats every expense as independent. Detecting recurring patterns enables:

1. **Better budget forecasting** — Large recurring expenses (rent on day 1) distort linear burn rate projections
2. **Analytics insight** — Show fixed vs discretionary spending split
3. **Upcoming expense reminders** — "Rent (KES 35,000) is due in 3 days"
4. **Missing expense alerts** — "Your KPLC payment usually happens by the 15th — did you miss it?"

## Kenyan M-PESA Context

Recurring expenses in Kenya have specific patterns:

| Expense | Typical Pattern | Amount Variance |
|---------|----------------|-----------------|
| Rent | Monthly, fixed date (1st–5th) | Exact same amount |
| KPLC Electricity (Prepaid) | Monthly, variable date | Variable amount (±30%) |
| WiFi (Safaricom Home) | Monthly, fixed date | Fixed amount |
| SACCO contribution | Monthly, around salary date | Fixed amount |
| Loan repayment (M-Shwari, KCB, Fuliza) | Monthly, fixed date | Fixed or decreasing |
| DSTV/Showmax/Netflix | Monthly, fixed date | Fixed amount |
| Church tithe | Weekly or monthly, variable | Variable (% of income) |
| Gym membership | Monthly | Fixed |
| Insurance premium | Monthly | Fixed |
| NSSF/NHIF | Monthly | Fixed |
| Savings (MMF, T-Bill) | Monthly, around salary date | Fixed or variable |

**Key observation:** Most recurring M-PESA expenses are identified by the **recipient** (paybill/till/phone) being the same, with amounts that are either exactly equal or within a tolerance band.

---

## Scope (Revised)

### ✅ In Scope

| Phase | Description |
|-------|-------------|
| **Phase A** | Detection engine + domain models (core algorithm) |
| **Phase B** | Analytics integration — recurring vs one-time spending split |
| **Phase C** | Forecast improvement — recurring-aware budget projections |
| **Phase D** | Notifications — upcoming/overdue reminders |

### ❌ Out of Scope (Deferred)

| Surface | Reason |
|---------|--------|
| Home Screen recurring summary card | Adds complexity to an already busy Home screen |
| Dedicated Recurring Expenses screen | Can be added later when users request it |

---

## Architecture Decision: No New Database Tables

Following the pattern established by [`ForecastService`](../android/app/src/main/java/com/pesatrack/services/ForecastService.kt:25) — **pure computation on existing data, no schema migration required.**

The detection algorithm queries existing expenses from [`ExpenseDao`](../android/app/src/main/java/com/pesatrack/data/local/database/dao/ExpenseDao.kt:11), groups by recipient, analyzes temporal patterns, and produces `RecurringExpense` domain objects. Results are cached in memory (`@Singleton` service) and refreshed periodically.

**Why no new table?**
- Recurring patterns are derived, not user-declared (at least for v1)
- Detection parameters may change as the algorithm improves
- Avoids DB migration v14→v15 for a feature that's purely analytical
- Follows ForecastService precedent (pure Kotlin service, Hilt-injected)

**Future option:** If users want to manually mark/unmark expenses as recurring, or override detected patterns, a `recurring_expenses` table can be added later (v15 migration).

---

## Detection Algorithm

### Phase 1: Recipient Grouping

```
All expenses (last 6 months, non-excluded)
  → GROUP BY COALESCE(recipientName, recipient)
  → Filter: ≥ 3 occurrences
  → For each group: collect timestamps and amounts
```

### Phase 2: Interval Detection

For each recipient group with ≥3 expenses:

1. **Sort by timestamp** (ascending)
2. **Compute intervals** between consecutive expenses (in days)
3. **Detect dominant interval** using a tolerance-based clustering:

```
Intervals: [29, 31, 30, 28, 32]  →  Cluster around 30 days  →  MONTHLY
Intervals: [7, 7, 6, 8, 7]       →  Cluster around 7 days   →  WEEKLY
Intervals: [14, 13, 15, 14]      →  Cluster around 14 days  →  BIWEEKLY
Intervals: [365, 362]            →  Cluster around 365 days →  YEARLY
```

**Tolerance windows:**

| Cycle | Expected Days | Tolerance |
|-------|--------------|-----------|
| WEEKLY | 7 | ±2 days |
| BIWEEKLY | 14 | ±3 days |
| MONTHLY | 30 | ±5 days |
| YEARLY | 365 | ±15 days |

4. **Confidence score** = (intervals matching dominant pattern) / (total intervals)
   - ≥ 0.7 → high confidence (show as recurring)
   - 0.5–0.69 → medium confidence (show with "possible" qualifier)
   - < 0.5 → not recurring

### Phase 3: Amount Pattern Analysis

For each detected recurring group:

- **Fixed amount:** Standard deviation < 5% of mean → `AmountPattern.FIXED`
- **Variable amount:** SD 5–30% of mean → `AmountPattern.VARIABLE`
- **Highly variable:** SD > 30% → `AmountPattern.UNPREDICTABLE`

### Phase 4: Next Occurrence Prediction

Using the detected interval and the most recent expense timestamp:

```kotlin
nextExpectedDate = lastExpenseTimestamp + dominantIntervalDays * MS_PER_DAY
```

For monthly expenses, use day-of-month from the most common occurrence day rather than a fixed interval (handles months with different lengths).

---

## Data Model

### Domain Models

```kotlin
// New file: domain/models/RecurringExpense.kt

data class RecurringExpense(
    val recipientKey: String,           // Normalized recipient identifier
    val recipientDisplayName: String,   // Human-readable name
    val categoryId: Long?,              // Category if consistently categorized
    val categoryName: String?,          // Display name
    val cycle: RecurrenceCycle,         // WEEKLY, BIWEEKLY, MONTHLY, YEARLY
    val averageAmount: Double,          // Mean amount across occurrences
    val lastAmount: Double,             // Most recent amount
    val amountPattern: AmountPattern,   // FIXED, VARIABLE, UNPREDICTABLE
    val confidence: Double,             // 0.0–1.0
    val occurrenceCount: Int,           // Total times detected
    val lastOccurrence: Long,           // Timestamp of most recent
    val nextExpected: Long,             // Predicted next date
    val expectedDayOfMonth: Int?,       // For MONTHLY: typical day (1-31)
    val paymentType: PaymentType,       // SEND_MONEY, PAY_BILL, etc.
    val isOverdue: Boolean              // nextExpected < now
)

enum class RecurrenceCycle {
    WEEKLY,     // ~7 days
    BIWEEKLY,   // ~14 days
    MONTHLY,    // ~30 days
    YEARLY;     // ~365 days

    fun displayName(): String = when (this) {
        WEEKLY -> "Weekly"
        BIWEEKLY -> "Every 2 weeks"
        MONTHLY -> "Monthly"
        YEARLY -> "Yearly"
    }

    val expectedDays: Int get() = when (this) {
        WEEKLY -> 7
        BIWEEKLY -> 14
        MONTHLY -> 30
        YEARLY -> 365
    }

    val toleranceDays: Int get() = when (this) {
        WEEKLY -> 2
        BIWEEKLY -> 3
        MONTHLY -> 5
        YEARLY -> 15
    }
}

enum class AmountPattern {
    FIXED,          // SD < 5% of mean — exact same amount each time
    VARIABLE,       // SD 5-30% — similar but not identical
    UNPREDICTABLE;  // SD > 30% — highly variable

    fun displayName(): String = when (this) {
        FIXED -> "Fixed amount"
        VARIABLE -> "Varies slightly"
        UNPREDICTABLE -> "Variable"
    }
}

data class RecurringExpenseSummary(
    val totalMonthlyRecurring: Double,    // Sum of monthly-equivalent amounts
    val fixedMonthlyTotal: Double,        // Only FIXED amount recurring
    val discretionaryEstimate: Double,    // totalSpending - fixedMonthlyTotal
    val recurringExpenses: List<RecurringExpense>,
    val upcomingThisWeek: List<RecurringExpense>,
    val overdueExpenses: List<RecurringExpense>
)

/**
 * Recurring expense info for a specific budget period.
 * Used by ForecastService for recurring-aware projections.
 */
data class RecurringPeriodInfo(
    val paidThisPeriod: Double,      // Recurring expenses already paid in current period
    val upcomingThisPeriod: Double   // Recurring expenses expected but not yet paid
)
```

---

## Service Layer

### RecurringExpenseService

```kotlin
// New file: services/RecurringExpenseService.kt

@Singleton
class RecurringExpenseService @Inject constructor(
    private val expenseDao: ExpenseDao,
    private val categoryDao: CategoryDao
) {
    // In-memory cache — refreshed on demand
    private var cachedResult: RecurringExpenseSummary? = null
    private var lastRefreshTime: Long = 0
    private val CACHE_TTL_MS = 30 * 60 * 1000L  // 30 minutes

    suspend fun getRecurringExpenses(forceRefresh: Boolean = false): RecurringExpenseSummary
    suspend fun getUpcomingExpenses(withinDays: Int = 7): List<RecurringExpense>
    suspend fun getOverdueExpenses(): List<RecurringExpense>
    fun getFixedMonthlyBaseline(): Double  // Quick access for forecast improvement

    /**
     * Get recurring expense info for a specific budget period.
     * Used by ForecastService for recurring-aware projections.
     */
    suspend fun getRecurringInfoForPeriod(
        periodStart: Long,
        periodEnd: Long
    ): RecurringPeriodInfo
}
```

**Dependencies on existing code:**
- [`ExpenseDao`](../android/app/src/main/java/com/pesatrack/data/local/database/dao/ExpenseDao.kt:11) — needs 1 new query (see below)
- [`CategoryDao`](../android/app/src/main/java/com/pesatrack/data/local/database/dao/CategoryDao.kt:11) — for resolving category names
- No new tables, no migration

### New DAO Query Required

```kotlin
// Added to ExpenseDao.kt — no schema migration needed (query-only change)

/**
 * Get all non-excluded expenses from last N months for recurring detection.
 * Returns raw rows ordered by recipient then timestamp for grouping.
 */
@Query("""
    SELECT
        COALESCE(recipientName, recipient) as recipientKey,
        recipient,
        recipientName,
        paymentType,
        categoryId,
        amount,
        timestamp
    FROM expenses
    WHERE isExcluded = 0
      AND timestamp >= :sinceTimestamp
    ORDER BY recipientKey, timestamp ASC
""")
suspend fun getExpensesForRecurrenceDetection(sinceTimestamp: Long): List<RecurrenceCandidate>

// New result class (in ExpenseDao.kt alongside other result classes)
data class RecurrenceCandidate(
    val recipientKey: String,
    val recipient: String,
    val recipientName: String?,
    val paymentType: String,
    val categoryId: Long?,
    val amount: Double,
    val timestamp: Long
)
```

---

## UI Surface: Analytics Integration (Phase B)

Add a "Recurring vs. One-time" breakdown to the monthly analytics tab:

```
┌──────────────────────────────────────────┐
│  🔄 Spending Breakdown                   │
│                                          │
│  Recurring  KES 70,800 (62%)             │
│  ████████████████████░░░░░░░░░░░         │
│                                          │
│  One-time   KES 43,200 (38%)             │
│  ████████████░░░░░░░░░░░░░░░░░░         │
│                                          │
│  Top recurring: Rent, SACCO, WiFi        │
└──────────────────────────────────────────┘
```

**Files to modify:**
- [`AnalyticsUiState.kt`](../android/app/src/main/java/com/pesatrack/presentation/screens/analytics/AnalyticsUiState.kt:1) — add `recurringTotal`, `oneTimeTotal`, `topRecurringNames` fields
- [`AnalyticsViewModel.kt`](../android/app/src/main/java/com/pesatrack/presentation/screens/analytics/AnalyticsViewModel.kt:1) — inject `RecurringExpenseService`, compute split
- [`AnalyticsScreen.kt`](../android/app/src/main/java/com/pesatrack/presentation/screens/analytics/AnalyticsScreen.kt:1) — add `RecurringBreakdownCard` composable

---

## Forecast Improvement (Phase C)

### The Problem

Current [`ForecastService.computeForecast()`](../android/app/src/main/java/com/pesatrack/services/ForecastService.kt:76) uses linear burn rate:

```
dailyBurnRate = totalSpent / daysElapsed
projectedTotal = dailyBurnRate × totalDaysInPeriod
```

Rent of KES 35,000 on day 1 of a 30-day month → projection of KES 1,050,000. Wildly wrong.

### The Fix

Split spending into recurring (known) and discretionary (extrapolated):

```kotlin
// In ForecastService.computeForecast():

if (recurringInfo != null) {
    val discretionarySpent = spent - recurringInfo.paidThisPeriod
    val discretionaryBurnRate = if (daysElapsed > 0) discretionarySpent / daysElapsed else 0.0

    projectedTotal = recurringInfo.paidThisPeriod +
                     recurringInfo.upcomingThisPeriod +
                     (discretionaryBurnRate * daysRemaining)
} else {
    // Fallback to current linear model (backward compatible)
    projectedTotal = dailyBurnRate * totalDays
}
```

**Example improvement:**

| Day | Current Linear Projection | Recurring-Aware Projection | Actual End-of-Month |
|-----|--------------------------|---------------------------|-------------------|
| 1 (after rent) | KES 1,050,000 ❌ | KES 49,500 ✅ | ~95,000 |
| 5 | KES 270,000 ❌ | KES 99,500 ✅ | ~95,000 |
| 15 | KES 140,000 | KES 70,000 ✅ | ~95,000 |

**Files to modify:**
- [`ForecastService.kt`](../android/app/src/main/java/com/pesatrack/services/ForecastService.kt:76) — add optional `RecurringPeriodInfo` parameter to `computeForecast()`; update callers
- [`BudgetService.kt`](../android/app/src/main/java/com/pesatrack/services/BudgetService.kt:24) — pass recurring info when checking forecasts
- [`HomeViewModel.kt`](../android/app/src/main/java/com/pesatrack/presentation/screens/home/HomeViewModel.kt:15) — pass recurring info when loading forecasts
- [`BudgetViewModel.kt`](../android/app/src/main/java/com/pesatrack/presentation/screens/budget/BudgetViewModel.kt:1) — pass recurring info when loading forecasts

---

## Notifications (Phase D)

### Upcoming Reminder
- **Trigger:** 1 day before `nextExpected` date
- **Content:** "📅 Rent (KES 35,000) is due tomorrow"
- **Channel:** New "Recurring Reminders" channel (medium importance)

### Overdue Alert
- **Trigger:** 2 days after `nextExpected` date (if no matching expense found)
- **Content:** "⚠️ KPLC payment usually happens by the 15th"
- **Channel:** Same "Recurring Reminders" channel

### Implementation
- Daily check via `WorkManager` `PeriodicWorkRequest` (once per day, morning)
- Worker queries `RecurringExpenseService.getUpcomingExpenses(withinDays=1)` and `getOverdueExpenses()`
- Throttle: max 1 notification per recurring expense per cycle (prevent spam)
- User can disable via Settings toggle

**Files to create:**
- `services/RecurringReminderWorker.kt` — WorkManager worker for daily checks

**Files to modify:**
- [`NotificationHelper.kt`](../android/app/src/main/java/com/pesatrack/services/NotificationHelper.kt:19) — add "Recurring Reminders" channel + `showRecurringReminderNotification()` + `showOverdueNotification()`
- [`MainActivity.kt`](../android/app/src/main/java/com/pesatrack/presentation/MainActivity.kt:48) — schedule `RecurringReminderWorker` on app start
- [`AppPreferences.kt`](../android/app/src/main/java/com/pesatrack/data/local/preferences/AppPreferences.kt:1) — recurring reminder enabled/disabled preference + per-expense throttle
- [`SettingsScreen.kt`](../android/app/src/main/java/com/pesatrack/presentation/screens/settings/SettingsScreen.kt:1) — add "Recurring reminders" toggle in notification section
- [`SettingsViewModel.kt`](../android/app/src/main/java/com/pesatrack/presentation/screens/settings/SettingsViewModel.kt:1) — manage the preference
- [`SettingsUiState.kt`](../android/app/src/main/java/com/pesatrack/presentation/screens/settings/SettingsUiState.kt:1) — add toggle state
- `build.gradle.kts` — add WorkManager dependency if not present

---

## Architecture Diagram

```mermaid
flowchart TD
    subgraph Existing Data
        ED[ExpenseDao<br/>+getExpensesForRecurrenceDetection]
        CD[CategoryDao]
    end

    subgraph New: Detection Engine - Phase A
        RES[RecurringExpenseService<br/>@Singleton - pure computation + 30min cache]
        DM[Domain Models<br/>RecurringExpense - RecurrenceCycle - AmountPattern]
    end

    subgraph Analytics Integration - Phase B
        AVM[AnalyticsViewModel<br/>recurring vs one-time split]
        AS[AnalyticsScreen<br/>RecurringBreakdownCard]
    end

    subgraph Forecast Improvement - Phase C
        FS[ForecastService<br/>recurring-aware projections]
        BS[BudgetService]
        HVM[HomeViewModel]
        BVM[BudgetViewModel]
    end

    subgraph Notifications - Phase D
        WM[RecurringReminderWorker<br/>WorkManager daily check]
        NH[NotificationHelper<br/>+Recurring Reminders channel]
        ST[SettingsScreen<br/>toggle on/off]
    end

    ED -->|RecurrenceCandidate rows| RES
    CD -->|category names| RES
    RES -->|RecurringExpenseSummary| AVM
    AVM --> AS
    RES -->|RecurringPeriodInfo| FS
    FS --> BS
    FS --> HVM
    FS --> BVM
    RES -->|upcoming + overdue lists| WM
    WM --> NH
    ST -.->|enable/disable| WM
```

---

## Edge Cases & Guards

| Scenario | Handling |
|----------|----------|
| New user with < 3 months data | Minimum 3 occurrences required; features hidden if no patterns detected |
| Recipient name variations | Use `COALESCE(recipientName, recipient)` — same as existing batch categorize logic |
| Expense excluded (`isExcluded = true`) | Excluded from detection (consistent with analytics) |
| Uncategorized expenses | Still detected as recurring; category shown as "Uncategorized" |
| Amount = 0 | Skip (transaction costs are separate expenses) |
| User stops a recurring expense | Confidence degrades naturally as pattern breaks; removed after 2 missed cycles |
| Same recipient, multiple amounts | Detected if interval is consistent; amount pattern = VARIABLE |
| Same recipient, multiple intervals | Only the dominant interval is used; low confidence if split |
| Rent on 31st (short months) | Use day-of-month matching with ±2 day tolerance |
| Bulk historical import | Works the same — algorithm is timestamp-based, not real-time |
| WorkManager battery optimization | Use `ExistingPeriodicWorkPolicy.KEEP` — does not restart if already scheduled |

---

## Performance Considerations

- **Query scope:** Last 6 months of expenses only (configurable)
- **Minimum occurrences:** ≥3 in the period (filters out noise)
- **Cache TTL:** 30 minutes in-memory (refresh on demand or after new expense)
- **Background detection:** Run on `Dispatchers.Default` (CPU-bound computation)
- **No Flow/LiveData for detection:** Snapshot-based, refreshed manually
- **Lazy loading:** Only compute when Analytics screen is visible or forecast is calculated
- **WorkManager:** Daily check is lightweight — queries cached results

---

## File Change Summary

### New Files

| File | Phase | Description |
|------|-------|-------------|
| `domain/models/RecurringExpense.kt` | A | Domain models: RecurringExpense, RecurrenceCycle, AmountPattern, RecurringExpenseSummary, RecurringPeriodInfo |
| `services/RecurringExpenseService.kt` | A | Detection algorithm + caching + period info for forecasts |
| `services/RecurringReminderWorker.kt` | D | WorkManager worker for daily upcoming/overdue checks |

### Modified Files

| File | Phase | Change |
|------|-------|--------|
| `data/local/database/dao/ExpenseDao.kt` | A | Add `getExpensesForRecurrenceDetection()` query + `RecurrenceCandidate` result class |
| `presentation/screens/analytics/AnalyticsUiState.kt` | B | Add recurring/one-time total fields |
| `presentation/screens/analytics/AnalyticsViewModel.kt` | B | Inject RecurringExpenseService, compute split |
| `presentation/screens/analytics/AnalyticsScreen.kt` | B | Add RecurringBreakdownCard composable |
| `services/ForecastService.kt` | C | Add optional RecurringPeriodInfo param to computeForecast() |
| `services/BudgetService.kt` | C | Pass recurring info when checking forecasts |
| `presentation/screens/home/HomeViewModel.kt` | C | Pass recurring info when loading forecasts |
| `presentation/screens/budget/BudgetViewModel.kt` | C | Pass recurring info when loading forecasts |
| `services/NotificationHelper.kt` | D | Add Recurring Reminders channel + notification methods |
| `presentation/MainActivity.kt` | D | Schedule RecurringReminderWorker |
| `data/local/preferences/AppPreferences.kt` | D | Recurring reminder preference + throttle |
| `presentation/screens/settings/SettingsScreen.kt` | D | Add recurring reminders toggle |
| `presentation/screens/settings/SettingsViewModel.kt` | D | Manage recurring reminder preference |
| `presentation/screens/settings/SettingsUiState.kt` | D | Add toggle state |
| `app/build.gradle.kts` | D | Add WorkManager dependency (if not present) |
