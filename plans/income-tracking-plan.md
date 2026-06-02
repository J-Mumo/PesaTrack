# Income Tracking — Implementation Plan

## 1. Why now

Today PesaTrack is single-sided: it sees money leaving. Income exists only as a manually-entered monthly budget number on the [Budget screen](../android/app/src/main/java/com/pesatrack/presentation/screens/budget/BudgetScreen.kt), and SMS parsers explicitly drop every income-shaped message (`Funds received from`, `Salary Payment from`, `Business Payment from`, `Offnet B2C Transfer`, `M-Shwari Withdraw`, deposits, reversal credits — see [MpesaSmsParser.kt#L57](../android/app/src/main/java/com/pesatrack/utils/parsers/MpesaSmsParser.kt#L57) and [MpesaStatementParser.kt#L73](../android/app/src/main/java/com/pesatrack/utils/parsers/MpesaStatementParser.kt#L73)).

That choice was correct for the v1 mission ("track spending") but it now blocks the v2 mission stated in [AGENTS.md](../AGENTS.md): _"Help people improve their finances by building a better spending and investment culture."_ Without income, every "% of income", savings rate, headroom, investment illustration, and lifestyle-creep cue is either approximated, faked from a manual override, or hidden.

### Feature Decision Filter

1. **Principle served:** P1 awareness (where does the money come from?), P3 save-and-invest-by-default (savings rate needs a numerator AND denominator), P5 honest numbers (today's "% of income" depends on a user-typed override that's often stale).
2. **Behaviour change:** the user can see their real income alongside spend, so the savings-rate framing becomes credible enough to act on.
3. **Honest downside:** SMS-derived income is noisy — refunds, peer-to-peer transfers, M-Shwari withdrawals, loan disbursements, and same-account top-ups all look like income. If we count them blindly we _inflate_ income and make savings rates look better than they are. Reconciliation against a user-set baseline is mandatory.
4. **Observable success:** at the end of any month the user can answer "How much came in this month and from where?" — which they cannot answer in-app today.

---

## 2. Scope

In:
- Detect income from M-PESA + supported bank SMS and statement imports.
- Persist income as first-class transactions, not just a monthly aggregate.
- Surface income on Home, Budget, Monthly/Quarterly/Year-in-Review, Analytics.
- Onboarding capture of typical monthly income (optional, skippable).
- Source tagging (salary / freelance / refund / family / interest / transfer-in / other).
- Honest reconciliation between auto-detected income and the user's manual override.

Out (explicit non-goals):
- No goal-tracking, savings buckets, or "envelope" budgeting — those are separate plans.
- No projecting future income — Principle 5 forbids projections without surfaced assumptions, and irregular income makes a projection card too easy to lie with. Revisit only after we have 3+ months of detected data.
- No income forecasting / lifestyle-creep alerting in this plan — landed in Phase 5 as a follow-on.
- No editing of historical SMS-derived income amounts (re-source/re-categorize only). Amounts are evidence, like expenses.
- No backend / cloud sync. Local-only, per AGENTS.md.

---

## 3. Naming clarification (to avoid confusion mid-plan)

The current `IncomeEntity` is a misnomer — it's actually a **monthly income budget / override**, one row per month. This plan introduces a second, transaction-level concept. To prevent future confusion:

| Concept | Today | After this plan |
|---|---|---|
| Manual monthly income budget (one number per month) | `IncomeEntity`, `IncomeDao` | renamed `MonthlyIncomeBudgetEntity`, `MonthlyIncomeBudgetDao` |
| Individual income transaction (one per SMS) | does not exist | new `IncomeTransactionEntity`, `IncomeTransactionDao` |
| The number used by analytics for "your income this month" | `monthlyIncome = budget?.amount` | `monthlyIncome = max(sumOfTxns, budget?.amount)` _with reconciliation rules — see §6.4_ |

The rename is a Room migration; the table name on disk doesn't have to change (Room cares about column shape, not entity class name). Keep the on-disk table name `income` and just rename the Kotlin types.

---

## 4. Data model

### 4.1 Schema (Migration v16 → v17)

```sql
CREATE TABLE IF NOT EXISTS income_transactions (
    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    transactionId TEXT NOT NULL,        -- M-PESA confirmation code; for bank credits use bankRef
    amount REAL NOT NULL,
    timestamp INTEGER NOT NULL,         -- epoch ms
    source TEXT NOT NULL,               -- enum name: SALARY, BUSINESS, TRANSFER_IN, REFUND, INTEREST, FAMILY, OTHER, UNCATEGORIZED
    sender TEXT,                        -- raw counterparty name from SMS, nullable
    rawSms TEXT,                        -- the full SMS body, for audit and re-parsing
    parserSource TEXT NOT NULL,         -- "MPESA" | "NCBA" | "KCB" | "EQUITY" | "MANUAL" | "STATEMENT_IMPORT"
    note TEXT,
    isExcluded INTEGER NOT NULL DEFAULT 0,  -- mirrors expenses.isExcluded — for "pass-through" income the user wants ignored
    isCategorized INTEGER NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX IF NOT EXISTS index_income_transactions_transactionId ON income_transactions(transactionId);
CREATE INDEX IF NOT EXISTS index_income_transactions_timestamp ON income_transactions(timestamp);
CREATE INDEX IF NOT EXISTS index_income_transactions_source ON income_transactions(source);
```

The `transactionId` unique index gives us free dedupe across re-parses, statement re-imports, and notification race conditions — same contract as the `expenses` table.

### 4.2 Domain types

New files under `domain/models/`:

```kotlin
data class IncomeTransaction(
    val id: Long,
    val transactionId: String,
    val amount: Double,
    val timestamp: Long,
    val source: IncomeSource,
    val sender: String?,
    val parserSource: String,
    val note: String?,
    val isExcluded: Boolean,
    val isCategorized: Boolean,
)

enum class IncomeSource(val displayName: String, val isInflow: Boolean) {
    SALARY("Salary", true),
    BUSINESS("Business income", true),
    REFUND("Refund", true),
    INTEREST("Interest / dividends", true),
    FAMILY("Family / gift", true),
    TRANSFER_IN("Transfer in", false),  // self-transfers; counted in totals but excluded from savings-rate denominator
    OTHER("Other", true),
    UNCATEGORIZED("Uncategorized", true),
}
```

`isInflow=false` for `TRANSFER_IN` is the key honesty lever: same-account top-ups (M-Shwari withdraw, bank-to-M-PESA) inflate raw income; counting them as inflow would make savings rate lie. We default the riskier classes (`M-Shwari Withdraw`, `Offnet B2C Transfer` when sender matches own number/name) to `TRANSFER_IN` and let the user override.

### 4.3 Repository

New `IncomeRepository` (`@Singleton`, Hilt) — _not_ folded into `BudgetRepository`. The budget repo keeps the manual override; the income repo owns transaction-level data.

```kotlin
class IncomeRepository @Inject constructor(
    private val incomeTransactionDao: IncomeTransactionDao,
    private val monthlyIncomeBudgetDao: MonthlyIncomeBudgetDao,
) {
    suspend fun insertIfNew(tx: IncomeTransaction): Long?     // returns null if duplicate
    fun observeForMonth(yearMonth: String): Flow<List<IncomeTransaction>>
    suspend fun sumForRange(startMs: Long, endMs: Long, includeTransfers: Boolean = false): Double
    suspend fun sourceBreakdown(startMs: Long, endMs: Long): List<IncomeSourceTotal>
    suspend fun updateSource(id: Long, source: IncomeSource)
    suspend fun setExcluded(id: Long, excluded: Boolean)

    /** The number analytics should use. See §6.4 for reconciliation rules. */
    suspend fun effectiveMonthlyIncome(yearMonth: String): EffectiveIncome
}

data class EffectiveIncome(
    val value: Double?,
    val source: EffectiveIncomeSource,   // DETECTED | MANUAL_OVERRIDE | DETECTED_BELOW_OVERRIDE | NONE
    val detectedAmount: Double,
    val manualAmount: Double?,
)
```

`EffectiveIncomeSource` lets every UI surface label _where_ the income number came from — Principle 5.

---

## 5. SMS / statement detection

### 5.1 Parser surface

Introduce a sealed result type in `utils/parsers/`:

```kotlin
sealed class ParsedSms {
    data class ExpenseResult(val expense: Expense, val fee: Expense?) : ParsedSms()
    data class IncomeResult(val income: IncomeTransaction) : ParsedSms()
    data object NotARelevantMessage : ParsedSms()
}
```

Change `SmsParserStrategy.parse(...)` to return `ParsedSms` instead of `Expense?`. Every existing parser returns `ExpenseResult` or `NotARelevantMessage`; new income paths return `IncomeResult`. This avoids a parallel "income parser registry" and keeps one dispatch site in `SmsReceiver`.

### 5.2 M-PESA income patterns

Patterns to detect (in order, first match wins):

| Pattern | Default `IncomeSource` | Rationale |
|---|---|---|
| `(?i)Salary Payment from` | `SALARY` | Already in `MpesaStatementParser.INCOME_PATTERNS`. |
| `(?i)Business Payment from` | `BUSINESS` | Same. |
| `(?i)You have received KES ([\d,.]+) from ([A-Z ]+) ([\d]{10,12})` | `UNCATEGORIZED` | Peer-to-peer receive — too ambiguous to auto-source. User picks. |
| `(?i)Funds received from` | `UNCATEGORIZED` | Reverse B2C; let user source. |
| `(?i)M-Shwari Withdraw` to M-PESA | `TRANSFER_IN` | Self-transfer; do not inflate savings-rate denominator. |
| `(?i)Offnet B2C Transfer` | `UNCATEGORIZED` | Could be salary from a non-M-PESA payroll or a refund. |
| `(?i)You have deposited KES ([\d,.]+)` | `TRANSFER_IN` | Agent deposit; usually you putting your own cash in. |
| `(?i)reversed` / `(?i)Reversal` | `REFUND` only if direction is credit; otherwise still skip. |

For bank SMS (NCBA, KCB, Equity), detect "credited" patterns mirroring the existing debit patterns in each bank parser. Bank credit defaults to `UNCATEGORIZED` — too varied to auto-source.

### 5.3 Auto-source heuristics

After the pattern matches, run a learned-mapping step (mirrors the existing recipient learning in [KeywordRulesEngine](../android/app/src/main/java/com/pesatrack/utils/auto/) for expenses):

1. If the same `sender` has been user-classified ≥2 times to the same `IncomeSource`, auto-apply that source on future receipts.
2. Sender name contains "SAFARICOM PLC", "EQUITY BANK", recognizable employer name (learned) → SALARY.
3. Same number as the user's own M-PESA (captured in onboarding §8) → TRANSFER_IN.

Persist learned mappings in a new lightweight table `income_sender_rules (sender TEXT PK, source TEXT, learnedAt INTEGER)`.

### 5.4 Existing skip logic — handle with care

The current parsers explicitly _drop_ income. We will keep the drop, but route it to the new income path instead. Two failure modes to avoid:

- **Don't double-count expense reversals as income.** A "Reversal" SMS that credits is paired with an expense entry that already exists. The honest behaviour: mark the original expense `isExcluded=true` rather than insert an income row. Track this in the SMS receiver dispatch logic.
- **Don't count Fuliza repayment debits as income.** Already filtered as expenses; not a concern here.

---

## 6. Integration points

### 6.1 SmsReceiver

After `SmsParserRegistry.parse(...)`:

```kotlin
when (val result = registry.parse(body, sender, timestamp)) {
    is ParsedSms.ExpenseResult -> expenseRepository.insertIfNew(result.expense)
    is ParsedSms.IncomeResult -> {
        val newId = incomeRepository.insertIfNew(result.income)
        if (newId != null && result.income.source == IncomeSource.UNCATEGORIZED) {
            notificationHelper.postIncomeCategorizationPrompt(newId, result.income)
        }
    }
    ParsedSms.NotARelevantMessage -> Unit
}
```

A new `income_received` notification channel (IMPORTANCE_LOW — not nag-grade) tapping deep-links to a `Screen.CategorizeIncome` source picker. Mirrors the existing expense categorization notification flow.

### 6.2 Home screen

Add a single secondary line under the existing month-to-date spend total:

```
KES 84,200 spent     ← existing primary
KES 110,000 received · 24% saved  ← new secondary line, alpha 0.65
```

- "Received" reads from `incomeRepository.sumForRange(monthStart, now, includeTransfers = false)`.
- "Saved %" = `(received - spent) / received` clamped to `[-100, 100]`, hidden when received <= 0.
- Tap navigates to the new Income screen (§6.5).
- If `EffectiveIncome.source == MANUAL_OVERRIDE` (no detected income), the secondary line falls back to today's existing "% of budget" framing instead — don't show "0 received" when really we just couldn't detect anything.

### 6.3 Budget screen

Income card on Budget screen ([BudgetScreen.kt#L263-L361](../android/app/src/main/java/com/pesatrack/presentation/screens/budget/BudgetScreen.kt#L263)) gains:

- A small "Detected this month: KES X" sub-line above the existing override field.
- A one-tap "Use detected" button that pre-fills the override.
- A footer chip showing which source the analytics layer is currently using (`Using detected income` / `Using your override` / `Using override — KES X higher than detected`).

The "Edit Income" dialog gains a help-text line: _"This is what the app should treat as your income for budgeting. Detected SMS income is shown for reference and not changed by edits here."_

### 6.4 Reconciliation rules (used by `effectiveMonthlyIncome`)

| Detected | Manual override | `EffectiveIncome.value` | `.source` | UI label |
|---|---|---|---|---|
| 0 / null | null | null | `NONE` | "Set your income to see savings rate" |
| 0 / null | X | X | `MANUAL_OVERRIDE` | "Using your set income (KES X)" |
| D > 0 | null | D | `DETECTED` | "Detected from SMS" |
| D > 0 | X, abs(D−X)/X ≤ 0.10 | D | `DETECTED` | "Detected — matches your set income" |
| D > 0 | X, X > D × 1.10 | X | `DETECTED_BELOW_OVERRIDE` | "Some income may not be detected — using your set income" |
| D > 0 | X, X < D × 0.90 | D | `DETECTED` | "Detected income exceeds your set income" |

The rules favour detected data when it looks complete, fall back to the user's override when detection is suspiciously low, and always disclose which one is in use. Principle 5.

### 6.5 New Income screen

`Screen.Income` (and `IncomeScreen` + `IncomeViewModel` + `IncomeUiState`) — list view, same pattern as `ExpenseListScreen`:

- Header card: month total + source breakdown bar (stacked: salary / business / family / refund / interest / transfer-in / other).
- Period switcher (month / quarter / year), defaulting to current month.
- List rows: amount · source · sender · date.
- Tap row → `CategorizeIncomeScreen` (single dialog, eight source chips + exclude toggle + note).
- FAB → manual income entry (`parserSource = "MANUAL"`).
- Empty state: explains how detection works, links to "Set monthly income manually" if SMS permission is missing.

### 6.6 Bottom nav

No new tab. Income is a destination from Home secondary line, Budget detected-line, Analytics → Insights ("Income this month" card), and Settings. Adding a fifth bottom tab right now is dilution.

### 6.7 Analytics / Insights

- `MonthlyReviewGenerator`: replace `monthlyIncome` parameter source with `IncomeRepository.effectiveMonthlyIncome(...)`. Investment illustration logic from the [recent fix](income-tracking-plan.md#) keeps working unchanged.
- `QuarterlyReviewGenerator`: fix the "first month's income as quarterly proxy" issue ([QuarterlyReviewGenerator.kt#L121](../android/app/src/main/java/com/pesatrack/domain/insights/QuarterlyReviewGenerator.kt#L121)) — sum the three months' effective incomes.
- `YearInReviewGenerator`: sum 12 months of effective incomes; expose `annualIncome` and `averageMonthlyIncome` on the snapshot.
- New Insights cards:
  - **Savings Rate** — `(received − spent) / received` for current month, with last-3-month average for context. Tapping shows assumptions.
  - **Income vs spend mini-chart** — two lines, 12 months. Read-only awareness, no projection.
- Investment illustration's `EffectiveIncome` source is shown in the disclaimer line ("Based on detected income of KES X").

### 6.8 Onboarding

Insert a new page between current pages 3 (SMS permission) and 4 (Import) — _Set your monthly income (optional)_:

- KES input with comma formatting.
- Helper text: "We use this to show how much of your income you keep. You can skip this — if you grant SMS access we'll try to detect your income from your salary message automatically."
- Two buttons: "Save" (writes a `MonthlyIncomeBudget` row for the current month) and "Skip for now" (advances).
- Skipping is not a dead-end: the Home screen and Budget screen both expose the same input later.

Per the existing onboarding pattern, this is _not_ a hard requirement.

### 6.9 Categories

No income categories on the Category table. Source is a separate enum on the income transaction itself — keeps the existing 100+ expense categories untouched and avoids polluting the category picker.

---

## 7. Reports / export / backup

- Add `income_transactions` and `income_sender_rules` to the existing CSV export (see [live-readiness-plan.md](live-readiness-plan.md)).
- Add to backup/restore pipeline ([database-backup-restore-plan.md](database-backup-restore-plan.md)).
- Excel import ([excel-import-plan.md](excel-import-plan.md)): add an "Income" sheet schema (Date, Amount, Source, Sender, Note); rows land in `income_transactions` with `parserSource = "EXCEL_IMPORT"`.

---

## 8. Privacy & local-first

Per AGENTS.md Principle 4:

- All income data stays on device. Same posture as expenses today — _no_ INTERNET permission added, _no_ payload to backend.
- Manifest already declares SMS read for expense parsing; income parsing reuses the same permission with no new prompt.
- Privacy policy ([docs/privacy-policy.html](../docs/privacy-policy.html)) needs an updated sentence: "PesaTrack reads SMS messages on your device to identify both outgoing and incoming M-PESA / bank transactions. Nothing leaves your phone." Update copy in About screen too.
- Income is _more_ sensitive than expenses (salary is identifying). PIN-lock plan ([pin-lock-plan.md](pin-lock-plan.md)) already gates the whole app — no extra gating needed, but the new Income screen should respect the same `requireUnlock` guard.

---

## 9. Phased delivery

Each phase is shippable on its own.

### Phase 1 — Data foundation _(no user-visible change)_
- Rename current `IncomeEntity`/`IncomeDao` to `MonthlyIncomeBudgetEntity`/`Dao`. Table name on disk stays `income`.
- Add `IncomeTransactionEntity` + DAO + Migration v16 → v17.
- Add `IncomeSource` enum + `IncomeTransaction` domain model + `IncomeRepository` (with stubs for `sumForRange`, `effectiveMonthlyIncome`).
- Unit tests: DAO upsert/dedupe, repository reconciliation rules table-driven (§6.4 ⇒ ~6 cases).
- Bug fix in the same phase: `QuarterlyReviewGenerator` first-month-only proxy (call `effectiveMonthlyIncome` for each month).

### Phase 2 — SMS detection
- Add `ParsedSms` sealed type; refactor `SmsParserStrategy` and all existing parsers to return it.
- Implement income patterns in `MpesaSmsParser`, `MpesaStatementParser`, `NcbaBankParser`, `KcbBankParser`, `EquityBankParser`.
- Wire `SmsReceiver` to dispatch `IncomeResult` into `IncomeRepository`.
- Income notification channel + `CategorizeIncomeScreen` (single screen, light).
- Migration: add `income_sender_rules` table (Migration v17 → v18, only if Phase 1 already shipped).
- Tests: regex coverage per parser, dedupe via `transactionId`, reversal-as-exclude-not-income rule.

### Phase 3 — Surfaces
- Home secondary "received / saved %" line.
- Budget screen detected-income surfacing + reconciliation chip.
- New `IncomeScreen` + viewmodel + navigation.
- Income source breakdown on Monthly Review screen.
- Update copy in About + privacy policy.

### Phase 4 — Onboarding & Analytics polish
- Onboarding optional-income page.
- Savings Rate insight card.
- Income vs spend mini-chart on Analytics.
- Investment illustration disclaimer cites the income source.

### Phase 5 — Follow-ons (separate plans when scoped)
- Lifestyle creep detection (income vs spend trend) — feeds [pro-launch-plan.md](pro-launch-plan.md).
- Irregular-income smoothing for budgeting (rolling 3-month average as headroom denominator).
- Source-level insights ("Your freelance income covered 38% of discretionary spend this quarter").

---

## 10. Risks & how we mitigate

| Risk | Mitigation |
|---|---|
| Inflated income from self-transfers (M-Shwari withdraw, agent deposit, bank-to-M-PESA) — fakes savings rate. | Default these to `TRANSFER_IN` with `isInflow=false`; exclude from savings-rate denominator. |
| Refunds counted as fresh income, double-counting. | When a refund's `transactionId` correlates with a past expense, prefer marking the expense `isExcluded` rather than inserting income. Phase 2 task. |
| Loan disbursements (Fuliza, KCB M-PESA, Mshwari loan) look like income. | Add specific exclusion patterns; default to `TRANSFER_IN` with explicit "this looks like a loan" disclaimer in the categorization prompt. |
| Bank SMS formats drift (NCBA history shows this twice already — see Bug Fix #11 in [implementation-status.md](../_docs/implementation-status.md#completed-bug-fixes)). | Same parser strategy + unit-test-per-format approach already used for expense parsing. |
| Detected vs manual mismatch confuses users. | Always disclose source in UI via `EffectiveIncomeSource` label. Reconciliation rules in §6.4 are deterministic and table-tested. |
| Income data feels invasive — could increase uninstall. | Income capture is optional everywhere (onboarding skip, manual override fallback, no scary copy). Re-use the "primer pattern" learnings from the [recent onboarding fix](../_docs/implementation-status.md#recent-features). |
| Schema rename of `IncomeEntity` breaks existing call sites. | Phase 1 is a rename PR only — no behaviour change. Verify by `./gradlew lint test assembleDebug` before Phase 2 starts. |

---

## 11. Acceptance

The feature is "done" (post-Phase 4) when:

- [ ] User can grant SMS permission, receive a salary SMS, and within seconds see "received KES X" reflected on Home — without any manual entry.
- [ ] On the Monthly Review of any month with detected income, "% of income" lines cite the detected number and the disclaimer says so.
- [ ] User can override the auto-detected income via the Budget screen; the UI clearly says which value analytics is using.
- [ ] Savings Rate insight card on Analytics shows current month + 3-month average, with assumptions visible on tap.
- [ ] Quarterly and Year-in-Review reports compute income as the sum of each constituent month's effective income, not first-month proxy.
- [ ] CSV export contains income rows; restoring a backup restores income; no INTERNET permission added.
- [ ] All new unit tests pass; `./gradlew lint test` clean; `_docs/implementation-status.md` updated at the end of each phase.
