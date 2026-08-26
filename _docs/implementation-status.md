# PesaTrack Implementation Status Report

## Overview

PesaTrack is a **passive M-PESA expense tracker** for Android. It intercepts incoming M-PESA SMS messages, parses transaction details, and saves them locally for categorization and review.

> **Architecture change:** STK Push (app-initiated payments via Daraja API) was removed in favour of SMS-only passive tracking. The backend server still exists on Railway but is **not used** by the Android app.

---

## Executive Summary

| Component | Status | Completion |
|-----------|--------|------------|
| **SMS Parsing (7 expense types)** | ✅ Complete | 100% |
| **Transaction Cost Auto-Tracking** | ✅ Complete | 100% |
| **Room Database (v18)** | ✅ Complete | 100% |
| **Category System (18 groups + custom)** | ✅ Complete | 100% |
| **Expense Management UI** | ✅ Complete | 100% |
| **Notifications** | ✅ Complete | 100% |
| **Runtime Permissions** | ✅ Complete | 100% |
| **Backend Server (unused)** | 🟡 Dormant | N/A |
| **Phase 2 M1: Historical SMS Import + Recipient Learning** | ✅ Complete | 100% |
| **Phase 2 M2: Bank SMS Tracking (NCBA)** | ✅ Complete | 100% |
| **Phase 2 M3: Smart Categorization (Rules Engine)** | ✅ Complete | 100% |
| **Excel Import (match + standalone)** | ✅ Complete | 100% |
| **M-PESA Statement PDF Import** | ✅ Complete | 100% |
| **Phase 2 M4: Manual Expense Entry** | ✅ Complete | 100% |
| **Phase 2 M5: Settings & Configuration** | ✅ Complete | 100% |
| **About Screen + Privacy Policy** | ✅ Complete | 100% |
| **Data Management (Export + Backup/Restore + Reset)** | ✅ Complete | 100% |
| **Expense Charts & Analytics** | ✅ Complete | 100% |
| **Year-over-Year Analytics** | ✅ Complete | 100% |
| **Phase 2 M6: Investment Category Deep-Dive** | ✅ Complete | 100% |
| **Phase 2 M7: Category & Sub-Category Budgets** | ✅ Complete | 100% |
| **Phase 2 M8: Custom Categories & Auto-Rules** | ✅ Complete | 100% |
| **PIN Lock + Biometric Unlock** | ✅ Complete | 100% |
| **Onboarding Flow** | ✅ Complete | 100% |
| **Budget Forecasting (4 phases)** | ❌ Removed | n/a (see 2026 cleanup note) |
| **Recurring Expense Detection** | ✅ Complete | 100% |
| **Insights & Reports v1.0 (Weekly Review)** | ✅ Complete | 100% |
| **Insights & Reports v1.1 (Monthly Review)** | ✅ Complete | 100% |
| **Insights & Reports v1.2 (Insights Section + Insight Cards)** | ✅ Complete | 100% |
| **Insights & Reports v1.3 (Quarterly Review + Budget Burn-Down)** | ✅ Complete | 100% |
| **Insights & Reports v1.4 (Year-in-Review + Share-as-Image)** | ✅ Complete | 100% |
| **Play Store Release (v1.1.0)** | ✅ Published | 100% |

---

## System Architecture (Current)

```
SMS Sources ──────────────────────────────────────────────────────────────────
│                                                                            │
│  M-PESA SMS ──► SmsReceiver ──► SmsParserRegistry ──► MpesaSmsParser       │
│  NCBA  SMS ──► SmsReceiver ──► SmsParserRegistry ──► NcbaBankParser        │
│  Historical ──► SmsImportService ──► SmsParserRegistry                     │
│  Excel .xlsx ──► ExcelImportService ──► ExcelParser + ExcelCategoryMapper  │
│                                        │                                   │
│                                  ParsedTransaction / ExcelExpenseRow       │
│                                   ├── expense (main)                       │
│                                   └── transactionCost (optional, SMS only) │
│                                        │                                   │
│                         Auto-Categorization                                │
│                          ├── KeywordRulesEngine (100+ business names)      │
│                          ├── PaymentType heuristics                        │
│                          ├── Recipient mapping (learned from user)         │
│                          └── Excel label→category mapping                  │
│                                        │                                   │
│                               ExpenseRepository                            │
│                                        │                                   │
│                                   Room Database                            │
│                                        │                                   │
│                    ┌───────────┬────────┼──────────┬──────────┐             │
│               HomeScreen  ExpenseList  Categorize  Batch   Settings        │
│                                                    ExcelImport             │
──────────────────────────────────────────────────────────────────────────────
```

**Key design decisions:**
- No backend communication — all data is local (Room + DataStore)
- SMS parsing + Excel import are the sources of expense data (M-PESA + bank SMS + Excel spreadsheets, plus future manual entry)
- Strategy pattern for SMS parsers — new banks are added as `SmsParserStrategy` implementations
- Transaction costs are auto-extracted and saved as separate expenses under category 606
- Non-expense SMS (Receive Money, Deposit, Reversal) are silently skipped
- Bank SMS deduplication via shared M-PESA transaction IDs (same uniqueness constraint)
- Bank tracking enabled by default — all supported banks are active out of the box (toggleable in Settings)

---

## Detailed Implementation Status

### 1. SMS Parsing & Transaction Detection

#### ✅ Implemented

| Feature | File | Description |
|---------|------|-------------|
| **Parser Strategy Interface** | [`SmsParserStrategy.kt`](../android/app/src/main/java/com/pesatrack/utils/parsers/SmsParserStrategy.kt:15) | Base interface for all SMS parsers |
| **Parser Registry** | [`SmsParserRegistry.kt`](../android/app/src/main/java/com/pesatrack/utils/parsers/SmsParserRegistry.kt:17) | Dispatches SMS to correct parser by sender ID |
| **M-PESA SMS Parser** | [`MpesaSmsParser.kt`](../android/app/src/main/java/com/pesatrack/utils/parsers/MpesaSmsParser.kt:31) | 8 M-PESA expense types |
| **NCBA Bank Parser** | [`NcbaBankParser.kt`](../android/app/src/main/java/com/pesatrack/utils/parsers/NcbaBankParser.kt:38) | 5 NCBA transaction types (Send, Till, Paybill, Card Payment) |
| SmsParser Facade | [`SmsParser.kt`](../android/app/src/main/java/com/pesatrack/utils/SmsParser.kt:20) | Backward-compatible facade delegating to registry |
| Send Money | [`MpesaSmsParser.kt`](../android/app/src/main/java/com/pesatrack/utils/parsers/MpesaSmsParser.kt:81) | Pattern: `"sent to NAME PHONE on"` |
| Buy Goods (Till) | [`MpesaSmsParser.kt`](../android/app/src/main/java/com/pesatrack/utils/parsers/MpesaSmsParser.kt:84) | Pattern: `"paid to SHOP. on"` |
| Pay Bill | [`MpesaSmsParser.kt`](../android/app/src/main/java/com/pesatrack/utils/parsers/MpesaSmsParser.kt:75) | Pattern: `"sent to COMPANY for account"` |
| Withdraw from Agent | [`MpesaSmsParser.kt`](../android/app/src/main/java/com/pesatrack/utils/parsers/MpesaSmsParser.kt:61) | Pattern: `"withdrawn ... from AGENT"` |
| Airtime (self) | [`MpesaSmsParser.kt`](../android/app/src/main/java/com/pesatrack/utils/parsers/MpesaSmsParser.kt:69) | Pattern: `"bought ... of airtime on"` |
| Airtime (other) | [`MpesaSmsParser.kt`](../android/app/src/main/java/com/pesatrack/utils/parsers/MpesaSmsParser.kt:65) | Pattern: `"bought ... of airtime for PHONE"` |
| M-PESA Card (Global) | [`MpesaSmsParser.kt`](../android/app/src/main/java/com/pesatrack/utils/parsers/MpesaSmsParser.kt:72) | Pattern: `"sent to M-PESA CARD for account"` |
| Fuliza | [`MpesaSmsParser.kt`](../android/app/src/main/java/com/pesatrack/utils/parsers/MpesaSmsParser.kt:78) | Pattern: `"Fuliza M-PESA amount sent to"` |
| NCBA Send Money | [`NcbaBankParser.kt`](../android/app/src/main/java/com/pesatrack/utils/parsers/NcbaBankParser.kt:63) | Pattern: `"MPESA transfer of KES to NAME (PHONE)"` |
| NCBA Till Payment (2 formats) | [`NcbaBankParser.kt`](../android/app/src/main/java/com/pesatrack/utils/parsers/NcbaBankParser.kt:85) | Format A: `"...to TILL_NUM NAME BANK REF..."`, Format B: `"...to NAME BANK REF..."` (no till number) |
| NCBA Paybill (3 formats) | [`NcbaBankParser.kt`](../android/app/src/main/java/com/pesatrack/utils/parsers/NcbaBankParser.kt:97) | Format A: `"...to NAME PAYBILL account..."`, Format B: `"...to NAME account number ACCT..."`, Format C: `"...to NAME BANK REF..."` (no account keyword) |
| Transaction cost extraction | [`MpesaSmsParser.kt`](../android/app/src/main/java/com/pesatrack/utils/parsers/MpesaSmsParser.kt:48) | Regex: `"Transaction cost,? Ksh..."` |
| Non-expense filtering | [`MpesaSmsParser.kt`](../android/app/src/main/java/com/pesatrack/utils/parsers/MpesaSmsParser.kt:56) | Skips Receive Money, Deposit, Reversal |
| NCBA self-transfer skip | [`NcbaBankParser.kt`](../android/app/src/main/java/com/pesatrack/utils/parsers/NcbaBankParser.kt:70) | Skips bank→own M-PESA transfers |
| NCBA Card Payment (approval + inbox lookup) | [`NcbaBankParser.kt`](../android/app/src/main/java/com/pesatrack/utils/parsers/NcbaBankParser.kt:71) | Card approval SMS triggers 2-min inbox lookup for paired debit to get KES amount + bank ref |
| NCBA generic debit skip | [`NcbaBankParser.kt`](../android/app/src/main/java/com/pesatrack/utils/parsers/NcbaBankParser.kt:62) | ALL generic debits skipped ("has been debited") — dedup handled via card approval inbox lookup |
| SMS Receiver | [`SmsReceiver.kt`](../android/app/src/main/java/com/pesatrack/services/SmsReceiver.kt:30) | Multi-source BroadcastReceiver with bank preference check |
| Multi-source Import | [`SmsImportService.kt`](../android/app/src/main/java/com/pesatrack/services/SmsImportService.kt:36) | Imports from M-PESA + enabled banks |
| Duplicate detection | [`SmsReceiver.kt`](../android/app/src/main/java/com/pesatrack/services/SmsReceiver.kt:30) | Checks transactionId before insert |

---

### 2. Android App — Data Layer

#### ✅ Implemented

| Feature | File | Description |
|---------|------|-------------|
| **Room Database** | | |
| Database Setup | [`PesaTrackDatabase.kt`](../android/app/src/main/java/com/pesatrack/data/local/database/PesaTrackDatabase.kt:16) | Version 18 with migrations |
| Migration 2→3 | [`PesaTrackDatabase.kt`](../android/app/src/main/java/com/pesatrack/data/local/database/PesaTrackDatabase.kt:34) | Moved Seed category to Faith & Giving |
| Migration 6→7 | [`PesaTrackDatabase.kt`](../android/app/src/main/java/com/pesatrack/data/local/database/PesaTrackDatabase.kt:469) | Added `isExcluded` column to expenses |
| Migration 7→8 | [`PesaTrackDatabase.kt`](../android/app/src/main/java/com/pesatrack/data/local/database/PesaTrackDatabase.kt:484) | Investment deep-dive: moved 6 sub-categories from Financial to new Investment & Savings group (18) |
| Migration 8→9 | [`PesaTrackDatabase.kt`](../android/app/src/main/java/com/pesatrack/data/local/database/PesaTrackDatabase.kt:776) | Category-based budgets: `budgets` table with unique index on (categoryGroupId, period) |
| Migration 9→10 | [`PesaTrackDatabase.kt`](../android/app/src/main/java/com/pesatrack/data/local/database/PesaTrackDatabase.kt:798) | User-defined auto-categorization rules: `category_rules` table |
| Migration 10→11 | [`PesaTrackDatabase.kt`](../android/app/src/main/java/com/pesatrack/data/local/database/PesaTrackDatabase.kt:834) | Beekeeping group converted from default to custom |
| Migration 11→12 | [`PesaTrackDatabase.kt`](../android/app/src/main/java/com/pesatrack/data/local/database/PesaTrackDatabase.kt:845) | Sub-category budgets: renamed `categoryGroupId` → `categoryId`, added `isGroupBudget` column |
| Migration 12→13 | [`PesaTrackDatabase.kt`](../android/app/src/main/java/com/pesatrack/data/local/database/PesaTrackDatabase.kt:895) | Added `income` table for manual monthly income tracking |
| Migration 13→14 | [`PesaTrackDatabase.kt`](../android/app/src/main/java/com/pesatrack/data/local/database/PesaTrackDatabase.kt:925) | Added `customStartDate` and `customEndDate` columns to budgets table (CUSTOM period support — later replaced by global month-start-day setting) |
| Migration 14→15 | [`PesaTrackDatabase.kt`](../android/app/src/main/java/com/pesatrack/data/local/database/PesaTrackDatabase.kt:938) | Added "Family & Friends Support" (id=507) sub-category under Faith & Giving (group 5) |
| Migration 15→16 | [`PesaTrackDatabase.kt`](../android/app/src/main/java/com/pesatrack/data/local/database/PesaTrackDatabase.kt:970) | Added `report_snapshots` table for Insights & Reports (Weekly/Monthly/Quarterly/Yearly snapshots) |
| Migration 16→17 | [`PesaTrackDatabase.kt`](../android/app/src/main/java/com/pesatrack/data/local/database/PesaTrackDatabase.kt:1) | Added `income_transactions` table for transaction-level income (income tracking Phase 1) |
| Migration 17→18 | [`PesaTrackDatabase.kt`](../android/app/src/main/java/com/pesatrack/data/local/database/PesaTrackDatabase.kt:1) | Added `income_sender_rules` table for learned sender→source mappings (income tracking Phase 2 — SMS detection) |
| Expense Entity | [`ExpenseEntity.kt`](../android/app/src/main/java/com/pesatrack/data/local/database/entities/ExpenseEntity.kt:11) | Full schema with FK to categories + isExcluded flag |
| Category Entity | [`CategoryEntity.kt`](../android/app/src/main/java/com/pesatrack/data/local/database/entities/CategoryEntity.kt:12) | Hierarchical categories with parent-child |
| Category Rule Entity | [`CategoryRuleEntity.kt`](../android/app/src/main/java/com/pesatrack/data/local/database/entities/CategoryRuleEntity.kt:1) | User-defined auto-categorization rules (pattern, matchType, categoryId, priority) |
| Budget Entity | [`BudgetEntity.kt`](../android/app/src/main/java/com/pesatrack/data/local/database/entities/BudgetEntity.kt:1) | Budget limits per category group, sub-category, or total, with period + isActive + isGroupBudget |
| Monthly Income Budget Entity | [`MonthlyIncomeBudgetEntity.kt`](../android/app/src/main/java/com/pesatrack/data/local/database/entities/MonthlyIncomeBudgetEntity.kt:1) | Manual monthly income override (amount, yearMonth unique, note). Table name `income` retained on disk. Renamed from `IncomeEntity` in income tracking Phase 1 |
| Income Transaction Entity | [`IncomeTransactionEntity.kt`](../android/app/src/main/java/com/pesatrack/data/local/database/entities/IncomeTransactionEntity.kt:1) | Transaction-level income row (one per detected SMS / statement / manual entry). Unique `transactionId` for free dedupe; `source` stores `IncomeSource` enum name |
| Income Sender Rule Entity | [`IncomeSenderRuleEntity.kt`](../android/app/src/main/java/com/pesatrack/data/local/database/entities/IncomeSenderRuleEntity.kt:1) | Learned `sender → IncomeSource` mapping (sender PK, source enum name, learnedAt). Applied automatically on insert for incoming UNCATEGORIZED income (income tracking Phase 2) |
| Default Categories | [`CategoryEntity.kt`](../android/app/src/main/java/com/pesatrack/data/local/database/entities/CategoryEntity.kt:57) | 17 groups, 95+ sub-categories |
| Expense DAO | [`ExpenseDao.kt`](../android/app/src/main/java/com/pesatrack/data/local/database/dao/ExpenseDao.kt:10) | CRUD + month queries + duplicate check + budget spending queries (total, group, sub-category) |
| Category DAO | [`CategoryDao.kt`](../android/app/src/main/java/com/pesatrack/data/local/database/dao/CategoryDao.kt:11) | CRUD + search + default seeding + expense count queries + group management |
| Category Rule DAO | [`CategoryRuleDao.kt`](../android/app/src/main/java/com/pesatrack/data/local/database/dao/CategoryRuleDao.kt:1) | Rule CRUD + active rules query |
| Budget DAO | [`BudgetDao.kt`](../android/app/src/main/java/com/pesatrack/data/local/database/dao/BudgetDao.kt:1) | Budget CRUD + active budget queries + affected budget lookups (group + sub-category) |
| Monthly Income Budget DAO | [`MonthlyIncomeBudgetDao.kt`](../android/app/src/main/java/com/pesatrack/data/local/database/dao/MonthlyIncomeBudgetDao.kt:1) | Manual income override upsert + getByYearMonth + observe as Flow (renamed from `IncomeDao` in income tracking Phase 1) |
| Income Transaction DAO | [`IncomeTransactionDao.kt`](../android/app/src/main/java/com/pesatrack/data/local/database/dao/IncomeTransactionDao.kt:1) | Insert-if-new by `transactionId`, range queries, source/exclude updates, `sumForRange` + `sumForRangeBySources` for savings-rate denominators |
| Income Sender Rule DAO | [`IncomeSenderRuleDao.kt`](../android/app/src/main/java/com/pesatrack/data/local/database/dao/IncomeSenderRuleDao.kt:1) | Upsert / getBySender / deleteBySender for learned sender→source mappings (income tracking Phase 2) |
| **Preferences** | | |
| AppPreferences | [`AppPreferences.kt`](../android/app/src/main/java/com/pesatrack/data/local/preferences/AppPreferences.kt:1) | DataStore for phone number, bank preferences, budget prompt dismissal, month start day (1–28) |
| **Repositories** | | |
| Expense Repository | [`ExpenseRepository.kt`](../android/app/src/main/java/com/pesatrack/data/repository/ExpenseRepository.kt:17) | CRUD, month range, domain mapping |
| Category Repository | [`CategoryRepository.kt`](../android/app/src/main/java/com/pesatrack/data/repository/CategoryRepository.kt:18) | Category CRUD (add/edit/delete groups + sub-categories), default init, expense count checks |
| Category Rule Repository | [`CategoryRuleRepository.kt`](../android/app/src/main/java/com/pesatrack/data/repository/CategoryRuleRepository.kt:1) | Rule CRUD, active rules loading for categorization pipeline |
| Budget Repository | [`BudgetRepository.kt`](../android/app/src/main/java/com/pesatrack/data/repository/BudgetRepository.kt:1) | Budget CRUD, period range computation (with month-start-day offset), spending aggregation (total/group/sub-category), progress/alert calculation, monthly income get/set, total budgeted computation |
| Income Repository | [`IncomeRepository.kt`](../android/app/src/main/java/com/pesatrack/data/repository/IncomeRepository.kt:1) | Income transaction CRUD (insert-if-new with dedupe + auto-apply learned sender rules for incoming UNCATEGORIZED income), per-source breakdown, `effectiveMonthlyIncome(yearMonth)` reconciliation (detected vs manual override per plan §6.4 — `EffectiveIncomeSource` of `NONE` / `MANUAL_OVERRIDE` / `DETECTED` / `DETECTED_BELOW_OVERRIDE`), and `learnSenderSource(sender, source)` used by `CategorizeIncomeScreen` to persist user choices to `income_sender_rules` |
| **Dependency Injection** | | |
| Hilt App Module | [`AppModule.kt`](../android/app/src/main/java/com/pesatrack/di/AppModule.kt:19) | Database (v18 with all migrations), DAOs (including BudgetDao, CategoryRuleDao, MonthlyIncomeBudgetDao, IncomeTransactionDao, IncomeSenderRuleDao, ReportSnapshotDao) |

---

### 3. Android App — Domain Layer

#### ✅ Implemented

| Feature | File | Description |
|---------|------|-------------|
| Expense Model | [`Expense.kt`](../android/app/src/main/java/com/pesatrack/domain/models/Expense.kt:6) | Domain model with `isCategorized` + `isExcluded` flags |
| Category Model | [`Category.kt`](../android/app/src/main/java/com/pesatrack/domain/models/Category.kt:1) | Domain model |
| Budget Model | [`Budget.kt`](../android/app/src/main/java/com/pesatrack/domain/models/Budget.kt:1) | Budget (with isGroupBudget flag), BudgetPeriod (WEEKLY/MONTHLY/YEARLY/CUSTOM — CUSTOM hidden via `uiEntries`), BudgetProgress, BudgetStatus (UNDER/WARNING/EXCEEDED), BudgetAlert |
| Budget Forecast Model | [`BudgetForecast.kt`](../android/app/src/main/java/com/pesatrack/domain/models/BudgetForecast.kt:1) | BudgetForecast (dailyBurnRate, exhaustionDate, projectedTotal, safeDailyBudget), ForecastStatus (ON_TRACK/PROJECTED_OVER/EXHAUSTION_IMMINENT) |
| PaymentType Enum | [`Expense.kt`](../android/app/src/main/java/com/pesatrack/domain/models/Expense.kt:32) | 10 values: SEND_MONEY, BUY_GOODS, PAY_BILL, WITHDRAW, AIRTIME, MPESA_CARD, TRANSACTION_COST, BANK_DEBIT, CARD_PAYMENT, CASH |
| ExpenseSource Enum | [`Expense.kt`](../android/app/src/main/java/com/pesatrack/domain/models/Expense.kt:81) | STK_PUSH (legacy), SMS_PARSED, SMS_BANK, EXCEL_IMPORT, MANUAL |

**PaymentType details:**

| Enum Value | Display Name | SMS Pattern |
|------------|-------------|-------------|
| `SEND_MONEY` | Send Money | `"sent to NAME PHONE on"` |
| `BUY_GOODS` | Buy Goods | `"paid to SHOP. on"` |
| `PAY_BILL` | Pay Bill | `"sent to COMPANY for account"` |
| `WITHDRAW` | Withdraw | `"withdrawn ... from AGENT"` |
| `AIRTIME` | Airtime | `"bought ... of airtime"` |
| `MPESA_CARD` | M-PESA Card | `"sent to M-PESA CARD for account"` |
| `TRANSACTION_COST` | Transaction Cost | `"Transaction cost, KshXX.XX"` (auto-categorized as category 811) |
| `CARD_PAYMENT` | Card Payment | NCBA card debit: `"has been debited with KES...Ref: FT..."` + linked approval: `"approved a transaction of USD...at MERCHANT"` |
| `CASH` | Cash | Manual entry only (no SMS pattern) |

**Legacy backward compatibility:** `fromString()` maps old values `"REVERSAL"`, `"RECEIVE_MONEY"`, `"DEPOSIT"` to `SEND_MONEY` for existing DB records.

---

### 4. Android App — Presentation Layer

#### ✅ Implemented

| Feature | File | Description |
|---------|------|-------------|
| **Navigation** | | |
| Nav Graph | [`NavGraph.kt`](../android/app/src/main/java/com/pesatrack/presentation/navigation/NavGraph.kt:17) | 18 routes: Home, Analytics, Expenses, Categorize, Import, ExcelImport, BatchCategorize, Settings, ManualEntry, Budget, CategoryManagement, About, WeeklyReview, MonthlyReview, QuarterlyReview, YearInReview, CategorizeIncome, Income |
| Screen Routes | [`Screen.kt`](../android/app/src/main/java/com/pesatrack/presentation/navigation/Screen.kt:6) | Sealed class with route definitions (incl. QuarterlyReview, YearInReview, CategorizeIncome, Income) |
| Bottom Nav | [`Screen.kt`](../android/app/src/main/java/com/pesatrack/presentation/navigation/Screen.kt:23) | 3 tabs: Home, Analytics, Expenses |
| **Main Activity** | | |
| MainActivity | [`MainActivity.kt`](../android/app/src/main/java/com/pesatrack/presentation/MainActivity.kt:48) | Onboarding overlay → PIN lock overlay → main app; biometric setup; notification channel |
| MainScreen | [`MainActivity.kt`](../android/app/src/main/java/com/pesatrack/presentation/MainActivity.kt:252) | Scaffold with NavigationBar + NavGraph |
| **Home Screen** | | |
| HomeScreen | [`HomeScreen.kt`](../android/app/src/main/java/com/pesatrack/presentation/screens/home/HomeScreen.kt:24) | Monthly summary with investment % breakdown, By Category top-5 (recent activity), recent expenses, uncategorized alert |
| HomeViewModel | [`HomeViewModel.kt`](../android/app/src/main/java/com/pesatrack/presentation/screens/home/HomeViewModel.kt:15) | Category-aware state management, default category init, investment total loading |
| HomeUiState | [`HomeUiState.kt`](../android/app/src/main/java/com/pesatrack/presentation/screens/home/HomeUiState.kt:1) | Uses `ExpenseWithCategory` for rich display + `investmentThisMonth` field |
| **Expenses Screen** | | |
| ExpenseListScreen | [`ExpenseListScreen.kt`](../android/app/src/main/java/com/pesatrack/presentation/screens/expenses/ExpenseListScreen.kt:1) | Full expense history with category colours |
| ExpensesViewModel | [`ExpensesViewModel.kt`](../android/app/src/main/java/com/pesatrack/presentation/screens/expenses/ExpensesViewModel.kt:1) | Category mapping for display |
| ExpensesUiState | [`ExpensesUiState.kt`](../android/app/src/main/java/com/pesatrack/presentation/screens/expenses/ExpensesUiState.kt:1) | `ExpenseWithCategory` data class |
| **Categorize Screen** | | |
| CategorizeScreen | [`CategorizeScreen.kt`](../android/app/src/main/java/com/pesatrack/presentation/screens/categorize/CategorizeScreen.kt:1) | Category assignment with grouped picker |
| CategorizeViewModel | [`CategorizeViewModel.kt`](../android/app/src/main/java/com/pesatrack/presentation/screens/categorize/CategorizeViewModel.kt:1) | State management |
| CategorizeUiState | [`CategorizeUiState.kt`](../android/app/src/main/java/com/pesatrack/presentation/screens/categorize/CategorizeUiState.kt:1) | UI state model |
| **Batch Categorize Screen** | | |
| BatchCategorizeScreen | [`BatchCategorizeScreen.kt`](../android/app/src/main/java/com/pesatrack/presentation/screens/batch_categorize/BatchCategorizeScreen.kt:44) | Batch categorize by recipient + multi-select mode for cross-recipient bulk categorization |
| BatchCategorizeViewModel | [`BatchCategorizeViewModel.kt`](../android/app/src/main/java/com/pesatrack/presentation/screens/batch_categorize/BatchCategorizeViewModel.kt:32) | Recipient group CRUD, auto-suggest, multi-select bulk apply |
| BatchCategorizeUiState | [`BatchCategorizeUiState.kt`](../android/app/src/main/java/com/pesatrack/presentation/screens/batch_categorize/BatchCategorizeUiState.kt:1) | Recipient groups, selection mode state, category picker state |
| **Components** | | |
| ExpenseCard | [`ExpenseCard.kt`](../android/app/src/main/java/com/pesatrack/presentation/components/ExpenseCard.kt:31) | Category name as title, payment type icons, colour-coded, long-press exclude toggle |
| CategoryChip | [`CategoryChip.kt`](../android/app/src/main/java/com/pesatrack/presentation/components/CategoryChip.kt:1) | Category selection chip |
| GroupedCategoryPicker | [`GroupedCategoryPicker.kt`](../android/app/src/main/java/com/pesatrack/presentation/components/GroupedCategoryPicker.kt:1) | Hierarchical category selector. When passed an `onCreateCategory` callback, renders an inline "Add sub-category" entry at the bottom of every expanded group and an "Add new category group" entry at the end of the list; auto-selects newly created sub-categories. |
| CategoryFormDialog | [`CategoryFormDialog.kt`](../android/app/src/main/java/com/pesatrack/presentation/components/CategoryFormDialog.kt:1) | Shared name/icon/color form used by both Settings → Manage Categories and the inline create flow inside `GroupedCategoryPicker`. |
| **Theme** | | |
| Theme | [`Theme.kt`](../android/app/src/main/java/com/pesatrack/presentation/theme/Theme.kt:1) | Material 3 theming |
| Colors | [`Color.kt`](../android/app/src/main/java/com/pesatrack/presentation/theme/Color.kt:1) | Colour palette with `getCategoryColor()` |
| Typography | [`Type.kt`](../android/app/src/main/java/com/pesatrack/presentation/theme/Type.kt:1) | Typography definitions |

| **Onboarding Screen** | | |
| OnboardingScreen | [`OnboardingScreen.kt`](../android/app/src/main/java/com/pesatrack/presentation/screens/onboarding/OnboardingScreen.kt:37) | 4-page HorizontalPager: Welcome, How It Works, SMS Permission (grant button), Import History (import now/later); dot indicators + Skip/Back/Next/Get Started |

| **PIN Lock Screen** | | |
| PinLockScreen | [`PinLockScreen.kt`](../android/app/src/main/java/com/pesatrack/presentation/screens/pin/PinLockScreen.kt:1) | 4-dot indicator, number pad, biometric button, shake animation, cooldown timer |
| PinSetupScreen | [`PinSetupScreen.kt`](../android/app/src/main/java/com/pesatrack/presentation/screens/pin/PinSetupScreen.kt:1) | PIN setup/change/disable flow with enter → confirm steps |
| PinViewModel | [`PinViewModel.kt`](../android/app/src/main/java/com/pesatrack/presentation/screens/pin/PinViewModel.kt:1) | PIN verification, setup/change/disable flows, brute force protection (5 attempts → 30s cooldown) |
| PinUiState | [`PinUiState.kt`](../android/app/src/main/java/com/pesatrack/presentation/screens/pin/PinUiState.kt:1) | PinMode (7 modes), digit entry, error/cooldown state, biometric flags |
| PinManager | [`PinManager.kt`](../android/app/src/main/java/com/pesatrack/services/PinManager.kt:1) | SHA-256 + salt PIN hashing, verification, timeout logic |
| AppLockLifecycleObserver | [`AppLockLifecycleObserver.kt`](../android/app/src/main/java/com/pesatrack/services/AppLockLifecycleObserver.kt:1) | ProcessLifecycleOwner observer — background timestamp, lock state management |
| **Settings Screen** | | |
| SettingsScreen | [`SettingsScreen.kt`](../android/app/src/main/java/com/pesatrack/presentation/screens/settings/SettingsScreen.kt:1) | Security (PIN toggle, change PIN, biometric, timeout) + Category management + Budget management + **Month start day picker (1–28)** + Bank SMS tracking toggles + **Notifications section (recurring reminders toggle)** |
| SettingsViewModel | [`SettingsViewModel.kt`](../android/app/src/main/java/com/pesatrack/presentation/screens/settings/SettingsViewModel.kt:1) | Bank preferences + PIN/biometric preferences + month start day management + **recurring reminders toggle** |
| SettingsUiState | [`SettingsUiState.kt`](../android/app/src/main/java/com/pesatrack/presentation/screens/settings/SettingsUiState.kt:1) | BankToggle + PIN/biometric/timeout + monthStartDay + **recurringRemindersEnabled** state |
| **Category Management Screen** | | |
| CategoryManagementScreen | [`CategoryManagementScreen.kt`](../android/app/src/main/java/com/pesatrack/presentation/screens/category_management/CategoryManagementScreen.kt:1) | Tab-based CRUD: Categories tab (add/edit/delete groups + sub-categories with icon/color pickers) + Auto-Rules tab (CRUD for user-defined categorization rules) |
| CategoryManagementViewModel | [`CategoryManagementViewModel.kt`](../android/app/src/main/java/com/pesatrack/presentation/screens/category_management/CategoryManagementViewModel.kt:1) | Category + rule CRUD, dialog state management, expense count validation |
| CategoryManagementUiState | [`CategoryManagementUiState.kt`](../android/app/src/main/java/com/pesatrack/presentation/screens/category_management/CategoryManagementUiState.kt:1) | CategoryDialogState (7 variants), form models for categories and rules |
| **Smart Categorization** | | |
| CategorizationService | [`AiCategorizationService.kt`](../android/app/src/main/java/com/pesatrack/services/AiCategorizationService.kt:1) | Two-pass categorization: user rules first (priority 0.99), then built-in KeywordRulesEngine fallback |
| KeywordRulesEngine | [`KeywordRulesEngine.kt`](../android/app/src/main/java/com/pesatrack/services/KeywordRulesEngine.kt:1) | 100+ exact name matches, 100+ keyword rules, PaymentType heuristics |
| **Excel Import** | | |
| ExcelImportScreen | [`ExcelImportScreen.kt`](../android/app/src/main/java/com/pesatrack/presentation/screens/excel_import/ExcelImportScreen.kt:26) | File picker, progress, results summary |
| ExcelImportViewModel | [`ExcelImportViewModel.kt`](../android/app/src/main/java/com/pesatrack/presentation/screens/excel_import/ExcelImportViewModel.kt:29) | Multi-file URI handling, import orchestration |
| ExcelImportUiState | [`ExcelImportUiState.kt`](../android/app/src/main/java/com/pesatrack/presentation/screens/excel_import/ExcelImportUiState.kt:8) | READY, FILES_SELECTED, IMPORTING, COMPLETED, ERROR |
| **M-PESA Statement PDF Import** | | |
| MpesaStatementParser | [`MpesaStatementParser.kt`](../android/app/src/main/java/com/pesatrack/utils/parsers/MpesaStatementParser.kt:37) | PDF text extraction via PDFBox, 13+ transaction type regex patterns, charge linking, password-protected PDF support |
| StatementImportService | [`StatementImportService.kt`](../android/app/src/main/java/com/pesatrack/services/StatementImportService.kt:1) | Orchestrates PDF unlock, parse, deduplicate, auto-categorize, batch save |
| StatementImportScreen | [`StatementImportScreen.kt`](../android/app/src/main/java/com/pesatrack/presentation/screens/statement_import/StatementImportScreen.kt:1) | File picker for PDFs, password dialog, progress indicator, results summary |
| StatementImportViewModel | [`StatementImportViewModel.kt`](../android/app/src/main/java/com/pesatrack/presentation/screens/statement_import/StatementImportViewModel.kt:1) | File selection, password input, import execution on IO dispatcher |
| StatementImportUiState | [`StatementImportUiState.kt`](../android/app/src/main/java/com/pesatrack/presentation/screens/statement_import/StatementImportUiState.kt:1) | READY, PASSWORD_REQUIRED, IMPORTING, COMPLETED, ERROR |

| **Manual Entry Screen** | | |
| ManualEntryScreen | [`ManualEntryScreen.kt`](../android/app/src/main/java/com/pesatrack/presentation/screens/manual_entry/ManualEntryScreen.kt:26) | Form: amount, recipient, name, payment type, date picker, category picker, notes |
| ManualEntryViewModel | [`ManualEntryViewModel.kt`](../android/app/src/main/java/com/pesatrack/presentation/screens/manual_entry/ManualEntryViewModel.kt:20) | Validation, save with recipient mapping |
| ManualEntryUiState | [`ManualEntryUiState.kt`](../android/app/src/main/java/com/pesatrack/presentation/screens/manual_entry/ManualEntryUiState.kt:8) | Form fields, validation errors, save state |

| **Analytics Screen** | | |
| AnalyticsScreen | [`AnalyticsScreen.kt`](../android/app/src/main/java/com/pesatrack/presentation/screens/analytics/AnalyticsScreen.kt:1) | **"Insights" \| "Charts" tab toggle** (Insights default): Insights tab = vertical card feed (Weekly/Monthly Review summaries, Pace Card, Quiet Leak Card, Categorization Nudge, Budget Burn-Down Card); Charts tab = Month selector, MoM comparison, trend line, **variable-spend category trends**, daily columns, category bars, top spenders, payment type breakdown (Vico charts); **Yearly tab**: year selector, YoY card, 12-month overlay chart, yearly breakdowns; **Budget setup banner**; **Forecast projection chart**; **RecurringBreakdownCard** |
| AnalyticsViewModel | [`AnalyticsViewModel.kt`](../android/app/src/main/java/com/pesatrack/presentation/screens/analytics/AnalyticsViewModel.kt:1) | Analytics data loading, month/year navigation, MoM/YoY computation, **CV-based volatile category detection**, yearly data lazy loading, budget status check, **forecast projection data loading**, **recurring breakdown loading**, **pace computation**, **quiet leak detection**, **uncategorized % check**, **budget burn-down detection** |
| AnalyticsUiState | [`AnalyticsUiState.kt`](../android/app/src/main/java/com/pesatrack/presentation/screens/analytics/AnalyticsUiState.kt:1) | Charts data, summary stats, month/year selection, **categoryTrends**, **InsightsTab** enum, **PaceCardData**, **QuietLeakData**, **BudgetBurnDownData**, yearly state fields, hasActiveBudgets, **cumulativeActual/cumulativeProjection/totalBudgetCeiling**, **recurringTotal/oneTimeTotal/topRecurringNames** |
| AnalyticsModels | [`AnalyticsModels.kt`](../android/app/src/main/java/com/pesatrack/domain/models/AnalyticsModels.kt:1) | MonthComparison, **YearComparison**, **CategoryTrend** (CV, mean, σ, spend level), **DEFAULT_VARIABLE_SPEND_CATEGORIES** (12 IDs) |

| **Budget Screen** | | |
| BudgetScreen | [`BudgetScreen.kt`](../android/app/src/main/java/com/pesatrack/presentation/screens/budget/BudgetScreen.kt:1) | **Period-first flow**: PeriodSelector (Weekly/Monthly/Yearly tabs + ◀ ▶ navigation), always-visible IncomeAllocationCard, budget list filtered by period, FAB to add, edit/delete, color-coded progress, **searchable** hierarchical category picker (no "Total Spending") |
| BudgetViewModel | [`BudgetViewModel.kt`](../android/app/src/main/java/com/pesatrack/presentation/screens/budget/BudgetViewModel.kt:1) | Period navigation (`setPeriodType`, `navigatePeriod`), loads budgets filtered by period type, income per period key, add/edit inherits period, hierarchical category loading |
| BudgetUiState | [`BudgetUiState.kt`](../android/app/src/main/java/com/pesatrack/presentation/screens/budget/BudgetUiState.kt:1) | `selectedPeriodType` + `selectedPeriodLabel` + `selectedPeriodKey`, budget progress list, BudgetCategoryOption (no Total Spending), income & allocation state, dialog state (no dialogPeriod) |

---

### 5. Notification System

#### ✅ Implemented

| Feature | File | Description |
|---------|------|-------------|
| Notification Helper | [`NotificationHelper.kt`](../android/app/src/main/java/com/pesatrack/services/NotificationHelper.kt:19) | Channel creation + expense alerts + budget alerts + forecast notifications + recurring reminders |
| Expense Notification Channel | [`NotificationHelper.kt`](../android/app/src/main/java/com/pesatrack/services/NotificationHelper.kt:29) | "Expense Notifications" channel (Android 8+) |
| Budget Alert Channel | [`NotificationHelper.kt`](../android/app/src/main/java/com/pesatrack/services/NotificationHelper.kt:104) | "Budget Alerts" channel — high importance when exceeded |
| Recurring Reminders Channel | [`NotificationHelper.kt`](../android/app/src/main/java/com/pesatrack/services/NotificationHelper.kt:271) | "Recurring Reminders" channel — upcoming/overdue expense alerts |
| Expense Notification | [`NotificationHelper.kt`](../android/app/src/main/java/com/pesatrack/services/NotificationHelper.kt:54) | Shows "New Expense: KES X,XXX.XX" + "To recipient" |
| Budget Alert Notification | [`NotificationHelper.kt`](../android/app/src/main/java/com/pesatrack/services/NotificationHelper.kt:132) | Shows "⚠️ Budget Warning" at 80% / "🚨 Budget Exceeded" at 100% with progress details |
| Recurring Reminder Notification | [`NotificationHelper.kt`](../android/app/src/main/java/com/pesatrack/services/NotificationHelper.kt:297) | Shows "🔔 Rent due tomorrow ~KES 35,000" for upcoming and "⏰ Rent appears overdue" for late |
| Recurring Reminder Worker | [`RecurringReminderWorker.kt`](../android/app/src/main/java/com/pesatrack/services/RecurringReminderWorker.kt:1) | Daily WorkManager worker — detects upcoming/overdue recurring expenses, sends notifications |
| Tap-to-Categorize | [`NotificationHelper.kt`](../android/app/src/main/java/com/pesatrack/services/NotificationHelper.kt:64) | PendingIntent opens categorize screen |
| Ignore from Notification | [`NotificationActionReceiver.kt`](../android/app/src/main/java/com/pesatrack/services/NotificationActionReceiver.kt:1) | "Categorize" + "Ignore" action buttons on expense notification; 5s undo window before persisting exclude |
| Channel Init on Launch | [`MainActivity.kt`](../android/app/src/main/java/com/pesatrack/presentation/MainActivity.kt:51) | Created in `onCreate()` (expense + recurring reminders channels) |

---

### 6. Utilities

#### ✅ Implemented

| Feature | File | Description |
|---------|------|-------------|
| Constants | [`Constants.kt`](../android/app/src/main/java/com/pesatrack/utils/Constants.kt:6) | `formatAsCurrency()` extension |
| **Excel Utilities** | | |
| ExcelParser | [`ExcelParser.kt`](../android/app/src/main/java/com/pesatrack/utils/excel/ExcelParser.kt:20) | Apache POI .xlsx parser with dual date format support |
| ExcelCategoryMapper | [`ExcelCategoryMapper.kt`](../android/app/src/main/java/com/pesatrack/utils/excel/ExcelCategoryMapper.kt:12) | 55+ hardcoded Excel label → PesaTrack category ID mappings |

---

### 7. Default Categories

#### ✅ Implemented

18 category groups with hierarchical sub-categories defined in [`DefaultCategories`](../android/app/src/main/java/com/pesatrack/data/local/database/entities/CategoryEntity.kt:64):

| ID | Group Name | Sub-categories |
|----|-----------|----------------|
| 1 | Beekeeping | Bee Equipment, Bee Feed, Bee Hives, Bees, Honey Harvesting, etc. |
| 2 | Digital & Tech | **Airtime** (202), Data Bundles, AI Subs, Streaming, Domain, Hosting, VPN |
| 3 | Education | Certifications, Conferences, Courses, School Fees, Stationery |
| 4 | Entertainment | Events, Games, Hobbies, Movies |
| 5 | Faith & Giving | Church Program, Community Program, **Family & Friends Support**, Give, Offering, **Seed**, Tithe |
| 6 | Financial | Bank Charges, Loan Interest, Loan Repayment, **Mpesa Transaction Cost** (606), Pesalink/RTGS Charges |
| 7 | Food & Dining | Groceries, Eating Out, Snacks/Drinks, Takeaway, Drinking Water |
| 8 | Government & Legal | KRA, NTSA, SHA, County Rates, Excise Duty, Visa/Passport Fees |
| 9 | Health | Medical Checkup, Pharmacy, Dental, Optical, Gym, Health Insurance |
| 10 | Home & Utilities | Rent, Electricity, Water, Gas, WiFi, Security, Repairs |
| **18** | **Investment & Savings** | **Chama, Crypto, Fixed Deposit, Insurance (Investment), MMF, NSSF, Pension, Real Estate, SACCO, Savings, Stocks/Shares, T-Bill/Bond, Unit Trusts** |
| 11 | Life Events | Birthday, Wedding, Funeral, Baby Shower, Harambee |
| 12 | Miscellaneous | Miscellaneous |
| 13 | Personal Care | Haircut, Salon, Laundry/Dry Cleaning |
| 14 | Pets | Pet Food, Vet, Grooming, Supplies |
| 15 | Shopping | Clothing, Electronics, General Shopping, Books, Art, Shipping |
| 16 | Transport & Travel | Uber/Bolt, Boda Boda, Fare, SGR, Flight, Accommodation |
| 17 | Vehicle | Fuel, Car Service, Car Wash, Parking, Expressway, Insurance, Tyres |

**Special auto-categorized categories:**
- **Category 606** ("Mpesa Transaction Cost") — Transaction costs are auto-saved here with `isCategorized = true`

---

### 8. Android Configuration

#### ✅ Implemented

| Feature | File | Description |
|---------|------|-------------|
| Manifest | [`AndroidManifest.xml`](../android/app/src/main/AndroidManifest.xml:1) | SMS + notification permissions only |
| SMS Permissions | [`AndroidManifest.xml`](../android/app/src/main/AndroidManifest.xml:5) | `READ_SMS`, `RECEIVE_SMS` |
| Notification Permission | [`AndroidManifest.xml`](../android/app/src/main/AndroidManifest.xml:9) | `POST_NOTIFICATIONS` (Android 13+) |
| SMS BroadcastReceiver | [`AndroidManifest.xml`](../android/app/src/main/AndroidManifest.xml:34) | Priority 999, `BROADCAST_SMS` permission |
| Runtime Permissions | [`MainActivity.kt`](../android/app/src/main/java/com/pesatrack/presentation/MainActivity.kt:61) | SMS permissions via onboarding flow; notification permission after onboarding |
| Gradle Build | [`build.gradle.kts`](../android/app/build.gradle.kts:1) | compileSdk 36, minSdk 26, targetSdk 36, Kotlin 17 (AGP 8.9.1, Gradle 8.11.1) |
| Build Config | [`build.gradle.kts`](../android/app/build.gradle.kts:29) | Release signing config + minify + shrink enabled |
| ProGuard Rules | [`proguard-rules.pro`](../android/app/proguard-rules.pro:1) | Room + Apache POI keep rules |

**Dependencies:**
- Jetpack Compose (BOM 2024.10.01) + Material 3
- Navigation Compose 2.8.4
- Hilt 2.53 (DI)
- Room 2.6.1 (database)
- DataStore 1.1.1 (preferences)
- Coroutines 1.9.0
- Apache POI 5.2.5 (Excel .xlsx parsing)
- Vico 2.0.0-beta.3 (Compose charting library — analytics charts)
- Biometric 1.2.0-alpha05 (fingerprint / face unlock for PIN lock)
- Lifecycle Process 2.8.7 (ProcessLifecycleOwner for app background detection)
- WorkManager 2.10.0 + Hilt WorkManager 1.2.0 (daily recurring expense reminder worker)

---

### 9. Play Store Release

#### ✅ Published (v1.1.0)

| Item | Details |
|------|---------|
| **Application ID** | `com.pesatrack` |
| **Version** | 1.1.0 (versionCode 4) |
| **Signed AAB** | `app-release.aab` — 13.9 MB, R8 minified + resource shrunk |
| **Signing Key** | `pesatrack-upload.jks` — CN=Joel Ngei, OU=PesaTrack, O=PesaTrack, L=Nairobi, C=KE (valid until 2053-08-07) |
| **Play App Signing** | Upload key used; Google re-signs for distribution |
| **Production Track** | Published — live on Google Play |
| **Internal Testing** | Live — testers can install via opt-in link |
| **Store Listing** | Short description (76 chars), full description (~1950 chars), 8 screenshots, feature graphic (1024×500), hi-res icon (512×512) |
| **Privacy Policy** | [`docs/privacy-policy.html`](../docs/privacy-policy.html) — hosted via GitHub Pages |
| **Content Rating** | IARC questionnaire completed |
| **Data Safety** | Completed — no data collected/shared, all data stored on-device |
| **SMS Permission Declaration** | ✅ Complete |
| **Listing Plan** | [`plans/play-store-listing-plan.md`](../plans/play-store-listing-plan.md) — store copy, release notes, form answers |
| **Release History** | [`_docs/releases.md`](releases.md) — version changelog and release tracking |

---

## Removed Components

The following were removed when STK Push was dropped in favour of SMS-only tracking:

| Removed | Was In | Reason |
|---------|--------|--------|
| `PaymentScreen.kt` | `presentation/screens/payment/` | No more app-initiated payments |
| `PaymentViewModel.kt` | `presentation/screens/payment/` | No more payment flow |
| `PaymentUiState.kt` | `presentation/screens/payment/` | No more payment flow |
| `PaymentRepository.kt` | `data/repository/` | No more API communication |
| `PesaTrackApi.kt` | `data/remote/api/` | No more Retrofit/backend calls |
| `PaymentRequest.kt` | `data/remote/dto/` | No more API DTOs |
| `PaymentResponse.kt` | `data/remote/dto/` | No more API DTOs |
| `PaymentResult.kt` | `domain/models/` | No more payment result states |
| `data/remote/` directory | `data/` | Entire remote layer removed |
| Retrofit dependency | `build.gradle.kts` | No networking needed |
| OkHttp dependency | `build.gradle.kts` | No networking needed |
| Google Generative AI SDK 0.9.0 | `build.gradle.kts` | Replaced with on-device KeywordRulesEngine (free tier rate limits, ~2MB dependency) |
| `GEMINI_API_KEY` BuildConfig | `build.gradle.kts` | No API key needed for rules engine |
| Gemini API key settings UI | `SettingsScreen.kt` | Removed AiCategorizationSection (API key input, toggle, links) |
| `aiCategorizationEnabled` preference | `AppPreferences.kt` | Smart suggest is always available |
| `geminiApiKey` preference | `AppPreferences.kt` | No API key needed |
| Quick Action buttons | `HomeScreen.kt` | Send Money/Buy Goods/Pay Bill buttons removed |
| `PaymentType.RECEIVE_MONEY` | `Expense.kt` | Not an expense |
| `PaymentType.DEPOSIT` | `Expense.kt` | Not an expense |
| `PaymentType.REVERSAL` | `Expense.kt` | Not an expense |
| `PhoneNumberHelper.kt` | `utils/` | Dead code — never called after STK Push removal |
| `provideTelephonyManager()` | `AppModule.kt` | Only injected into PhoneNumberHelper |
| `READ_PHONE_STATE` permission | `AndroidManifest.xml` | Unused — phone auto-fill feature was never wired up |
| `READ_PHONE_NUMBERS` permission | `AndroidManifest.xml` | Unused — same reason |
| `READ_CONTACTS` permission | `AndroidManifest.xml` | Unused — contact picker removed with STK Push |
| `INTERNET` permission | `AndroidManifest.xml` | App is fully offline — no network calls |
| `ACCESS_NETWORK_STATE` permission | `AndroidManifest.xml` | App is fully offline |
| `usesCleartextTraffic="true"` | `AndroidManifest.xml` | No network traffic at all |
| Retrofit/OkHttp/Gson ProGuard rules | `proguard-rules.pro` | Libraries not in dependencies |
| `HowItWorksCard` composable | `HomeScreen.kt` | Static onboarding content on Home feed — info available in onboarding flow |
| CUSTOM period tab + date pickers | `BudgetScreen.kt` | Replaced by global month-start-day setting in Settings |

---

## Current File Structure

### Android App

```
app/src/main/java/com/pesatrack/
├── PesaTrackApp.kt                          ✅ Hilt Application class + ProcessLifecycleOwner (PIN lock) + WorkManager Configuration.Provider
├── di/
│   └── AppModule.kt                         ✅ Database, DAOs (incl. BudgetDao, CategoryRuleDao, MonthlyIncomeBudgetDao, IncomeTransactionDao, IncomeSenderRuleDao)
├── data/
│   ├── local/
│   │   ├── database/
│   │   │   ├── PesaTrackDatabase.kt         ✅ Room v18 with migrations (v16→v17: `income_transactions` table for income tracking Phase 1; v17→v18: `income_sender_rules` table for learned sender→source mappings — income tracking Phase 2)
│   │   │   ├── dao/
│   │   │   │   ├── ExpenseDao.kt            ✅ CRUD + month queries + duplicate check + budget spending queries
│   │   │   │   ├── CategoryDao.kt           ✅ CRUD + search + default seeding + expense count queries + group mgmt
│   │   │   │   ├── CategoryRuleDao.kt       ✅ Rule CRUD + active rules query for categorization pipeline
│   │   │   │   ├── BudgetDao.kt             ✅ Budget CRUD + active queries + affected budget lookups
│   │   │   │   ├── MonthlyIncomeBudgetDao.kt ✅ Manual income override upsert + getByYearMonth + observe (table `income`)
│   │   │   │   ├── IncomeTransactionDao.kt  ✅ Income txn CRUD with dedupe-by-transactionId, range sums, source filters
│   │   │   │   ├── IncomeSenderRuleDao.kt   ✅ Learned sender→IncomeSource upsert / lookup / delete (income tracking Phase 2)
│   │   │   │   └── RecipientCategoryMappingDao.kt ✅ Recipient→category learned mappings CRUD
│   │   │   └── entities/
│   │   │       ├── ExpenseEntity.kt          ✅ Full schema with FK to categories
│   │   │       ├── CategoryEntity.kt         ✅ 17 groups, 95+ categories
│   │   │       ├── CategoryRuleEntity.kt     ✅ User-defined auto-categorization rules (pattern, matchType, priority)
│   │   │       ├── BudgetEntity.kt           ✅ Budget limits per group/sub-category/total with period + isActive + isGroupBudget
│   │   │       ├── MonthlyIncomeBudgetEntity.kt ✅ Manual monthly income override (amount, yearMonth, note). Table `income`
│   │   │       ├── IncomeTransactionEntity.kt ✅ Transaction-level income row (unique transactionId, source enum, parserSource, isExcluded)
│   │   │       ├── IncomeSenderRuleEntity.kt ✅ Learned sender→source mapping (sender PK, source enum name, learnedAt — income tracking Phase 2)
│   │   │       └── RecipientCategoryMappingEntity.kt ✅ Learned recipient→category associations
│   │   └── preferences/
│   │       └── AppPreferences.kt            ✅ DataStore (phone number, bank prefs, budget prompt, PIN lock settings, month start day, forecast notification throttle, **recurring reminders toggle + throttle**)
│   └── repository/
│       ├── InsightsRepository.kt            ✅ Weekly/Monthly/Quarterly/Yearly review CRUD (generate, store, retrieve snapshots)
│       ├── ExpenseRepository.kt             ✅ Domain mapping, CRUD
│       ├── CategoryRepository.kt            ✅ Category CRUD (add/edit/delete groups + sub-categories), expense count checks
│       ├── CategoryRuleRepository.kt        ✅ Rule CRUD, active rules for categorization pipeline
│       ├── BudgetRepository.kt              ✅ Budget CRUD, period ranges (with month-start-day offset), spending aggregation (total/group/sub-category), progress/alerts, monthly income get/set
│       └── RecipientMappingRepository.kt    ✅ Recipient→category mapping CRUD for learned categorization
├── domain/models/
│   ├── Expense.kt                           ✅ PaymentType (8) + ExpenseSource (5)
│   ├── Category.kt                          ✅ Domain model
│   ├── Budget.kt                            ✅ Budget, BudgetPeriod, BudgetProgress, BudgetStatus, BudgetAlert
│   ├── BudgetForecast.kt                    ✅ BudgetForecast (dailyBurnRate, exhaustionDate, projectedTotal, safeDailyBudget), ForecastStatus enum
│   ├── RecurringExpense.kt                  ✅ RecurringExpense, RecurrenceCycle, AmountPattern, RecurringExpenseSummary, RecurringPeriodInfo
│   └── AnalyticsModels.kt                   ✅ MonthComparison + CategoryTrend + DEFAULT_VARIABLE_SPEND_CATEGORIES
├── domain/insights/
│   ├── WeeklyReviewSnapshot.kt              ✅ Weekly review domain model
│   ├── WeeklyReviewGenerator.kt             ✅ Pure function generator for weekly snapshots
│   ├── MonthlyReviewSnapshot.kt             ✅ Monthly review domain model
│   ├── MonthlyReviewGenerator.kt            ✅ Pure function generator for monthly snapshots
│   ├── QuarterlyReviewSnapshot.kt           ✅ Quarterly review domain model (period total, delta, top 5, biggest mover, fees, savings momentum, investment illustration)
│   ├── QuarterlyReviewGenerator.kt          ✅ Pure function generator for quarterly snapshots
│   ├── YearInReviewSnapshot.kt              ✅ Year-in-review domain model (annual total, delta, top 5, biggest mover, fees, quiet leaks, savings story, investment illustration, goals progress)
│   └── YearInReviewGenerator.kt             ✅ Pure function generator for yearly snapshots
├── presentation/
│   ├── MainActivity.kt                      ✅ Onboarding → PIN lock → main app; BiometricPrompt; 3-tab bottom nav; **WorkManager recurring reminder scheduling**
│   ├── navigation/
│   │   ├── NavGraph.kt                      ✅ 12 routes: Home, Analytics, Expenses, Categorize, Import, ExcelImport, BatchCategorize, Settings, ManualEntry, Budget, CategoryManagement, PinSetup
│   │   └── Screen.kt                        ✅ Sealed class + BottomNavItem enum (3 tabs) + PinSetup route
│   ├── screens/
│   │   ├── home/
│   │   │   ├── HomeScreen.kt                ✅ Monthly summary (with investment % breakdown) + mini trend chart + By Category top-5 (most recent activity) + recent expenses + budget summary/prompt cards (HowItWorksCard & ForecastCard removed)
│   │   │   ├── HomeViewModel.kt             ✅ Category-aware state + trend data + investment total + budget progress + prompt logic + month-start-day refresh + **forecast loading**
│   │   │   └── HomeUiState.kt               ✅ ExpenseWithCategory + MonthlyTrend + MonthComparison + investmentThisMonth + budget fields + **budgetForecasts + showForecastCard**
│   │   ├── expenses/
│   │   │   ├── ExpenseListScreen.kt          ✅ Full expense list
│   │   │   ├── ExpensesViewModel.kt          ✅ Category mapping
│   │   │   └── ExpensesUiState.kt            ✅ ExpenseWithCategory model
│   │   ├── categorize/
│   │   │   ├── CategorizeScreen.kt           ✅ Category assignment
│   │   │   ├── CategorizeViewModel.kt        ✅ State management
│   │   │   └── CategorizeUiState.kt          ✅ UI state
│   │   ├── batch_categorize/
│   │   │   ├── BatchCategorizeScreen.kt    ✅ Batch categorize by recipient + multi-select cross-recipient mode
│   │   │   ├── BatchCategorizeUiState.kt   ✅ Recipient groups, selection mode, suggestions
│   │   │   └── BatchCategorizeViewModel.kt ✅ Group CRUD, auto-suggest, multi-select bulk apply
│   │   ├── excel_import/
│   │   │   ├── ExcelImportScreen.kt          ✅ File picker + progress + results
│   │   │   ├── ExcelImportViewModel.kt       ✅ Multi-file import orchestration
│   │   │   └── ExcelImportUiState.kt         ✅ 5 phases (READY→COMPLETED)
│   │   ├── statement_import/
│   │   │   ├── StatementImportScreen.kt      ✅ PDF file picker, password dialog, progress, results
│   │   │   ├── StatementImportViewModel.kt   ✅ File selection, password input, import execution
│   │   │   └── StatementImportUiState.kt     ✅ READY, PASSWORD_REQUIRED, IMPORTING, COMPLETED, ERROR
│   │   ├── import_history/
│   │   │   ├── ImportScreen.kt               ✅ Historical SMS import screen
│   │   │   ├── ImportViewModel.kt            ✅ Import orchestration
│   │   │   └── ImportUiState.kt              ✅ Import state
│   │   ├── about/
│   │   │   ├── AboutScreen.kt                ✅ App info, version, privacy policy link
│   │   │   └── AboutViewModel.kt             ✅ Version info state
│   │   ├── manual_entry/
│   │   │   ├── ManualEntryScreen.kt       ✅ Manual expense form with validation
│   │   │   ├── ManualEntryViewModel.kt    ✅ Save + recipient mapping
│   │   │   └── ManualEntryUiState.kt      ✅ Form state model
│   │   ├── analytics/
│   │   │   ├── AnalyticsScreen.kt         ✅ Full analytics + budget setup banner + **ForecastProjectionChart** + **RecurringBreakdownCard**
│   │   │   ├── AnalyticsViewModel.kt      ✅ Data loading, month nav, MoM computation, CV-based trends, budget status + forecast projection + **recurring breakdown**
│   │   │   └── AnalyticsUiState.kt        ✅ Charts data + summary stats + categoryTrends + hasActiveBudgets + forecast fields + **recurringTotal/oneTimeTotal/topRecurringNames**
│   │   ├── budget/
│   │   │   ├── BudgetScreen.kt            ✅ Period-first flow: PeriodSelector (tabs + nav), always-visible IncomeCard, filtered budget list, searchable category picker (no Total Spending), **per-card forecast subtitle**
│   │   │   ├── BudgetViewModel.kt         ✅ Period navigation, period-filtered loading, income per period key, CRUD inherits period, **forecast loading per period**
│   │   │   └── BudgetUiState.kt           ✅ Period state (type/label/key), progress list, BudgetCategoryOption, income & allocation, dialog (no dialogPeriod), **forecastMap**
│   │   ├── category_management/
│   │   │   ├── CategoryManagementScreen.kt  ✅ Tab-based CRUD: Categories + Auto-Rules, icon/color pickers, dialogs
│   │   │   ├── CategoryManagementViewModel.kt ✅ Category + rule CRUD, dialog state, expense count validation
│   │   │   └── CategoryManagementUiState.kt ✅ CategoryDialogState (7 variants), form models
│   │   ├── onboarding/
│   │   │   └── OnboardingScreen.kt          ✅ 4-page HorizontalPager: Welcome, How It Works, SMS Permission, Import History
│   │   ├── pin/
│   │   │   ├── PinLockScreen.kt             ✅ 4-dot indicator, number pad, biometric button, shake animation
│   │   │   ├── PinSetupScreen.kt            ✅ PIN setup/change/disable flow
│   │   │   ├── PinViewModel.kt              ✅ PIN verification, brute force protection (5 attempts → 30s cooldown)
│   │   │   └── PinUiState.kt               ✅ PinMode (7 modes), digit entry, error/cooldown state
│   │   ├── quarterly_review/
│   │   │   ├── QuarterlyReviewScreen.kt  ✅ Quarterly spending review with top 5, biggest mover, fees, savings momentum, investment illustration
│   │   │   ├── QuarterlyReviewViewModel.kt ✅ Loads quarterly snapshot from InsightsRepository
│   │   │   └── QuarterlyReviewUiState.kt ✅ Loading/loaded/error states for quarterly snapshot
│   │   ├── year_in_review/
│   │   │   ├── YearInReviewScreen.kt     ✅ Annual review with share-as-image button (ReportRenderer)
│   │   │   ├── YearInReviewViewModel.kt  ✅ Loads yearly snapshot from InsightsRepository
│   │   │   └── YearInReviewUiState.kt    ✅ Loading/loaded/error states for yearly snapshot
│   │   ├── categorize_income/
│   │   │   ├── CategorizeIncomeScreen.kt    ✅ FlowRow of IncomeSource chips + dedicated "Not income" FilterChip (toggles row-level isExcluded) for a single uncategorized income txn; "Remember this sender" persists rule to `income_sender_rules` (income tracking Phase 2)
│   │   │   ├── CategorizeIncomeViewModel.kt ✅ Loads income by id, applies chosen source, optional learn-sender-rule write
│   │   │   └── CategorizeIncomeUiState.kt   ✅ Loading / loaded(income, selectedSource, rememberSender) / saved / error
│   │   ├── income/
│   │   │   ├── IncomeScreen.kt              ✅ Month/Quarter/Year segmented income list with `IncomeHeaderCard` (total + reconciliation chip + weighted `SourceBreakdownBar` + `FlowRow` of `SourceLegendChip`s), `IncomeRow`s (color dot + amount + source + date, tap → CategorizeIncome, long-press → toggle `isExcluded` / restore; excluded rows render dimmed + strike-through), and `ManualIncomeEntryDialog` via Extended FAB (income tracking Phase 3)
│   │   │   ├── IncomeViewModel.kt           ✅ `@HiltViewModel` injecting `IncomeRepository`; loads `getForRange` + `sourceBreakdown` + `effectiveMonthlyIncome` (monthly only); persists manual entries via `IncomeRepository.insertIfNew` with `parserSource = "MANUAL"`
│   │   │   └── IncomeUiState.kt             ✅ `IncomePeriod { MONTH, QUARTER, YEAR }` + period/total/breakdown/effectiveSource/transactions + manual-entry dialog fields
│   │   └── settings/
│   │       ├── SettingsScreen.kt             ✅ Security (PIN, biometric, timeout) + Category mgmt + Budget mgmt + **Month start day picker** + Bank SMS toggles + **Notifications section (recurring reminders)**
│   │       ├── SettingsViewModel.kt          ✅ Bank + PIN/biometric + month start day + **recurring reminders** preferences management
│   │       └── SettingsUiState.kt            ✅ BankToggle + PIN/biometric/timeout + monthStartDay + **recurringRemindersEnabled** state
│   ├── components/
│   │   ├── ExpenseCard.kt                   ✅ Payment type icons, category title
│   │   ├── CategoryChip.kt                  ✅ Selection chip
│   │   ├── GroupedCategoryPicker.kt         ✅ Hierarchical selector
│   │   └── ReportRenderer.kt               ✅ Generic share-as-image utility (captures composable as bitmap, shares via intent)
│   └── theme/
│       ├── Theme.kt                         ✅ Material 3
│       ├── Color.kt                         ✅ getCategoryColor()
│       └── Type.kt                          ✅ Typography
├── services/
│   ├── SmsReceiver.kt                       ✅ Multi-source BroadcastReceiver + budget alert check + **forecast check** after save
│   ├── SmsImportService.kt                  ✅ Multi-source historical import
│   ├── ExcelImportService.kt                ✅ Excel import orchestration (match + standalone)
│   ├── StatementImportService.kt            ✅ PDF unlock, parse, deduplicate, auto-categorize, batch save
│   ├── AiCategorizationService.kt           ✅ CategorizationService — two-pass: user rules first, then built-in engine
│   ├── KeywordRulesEngine.kt                ✅ 100+ business names, keyword rules, PaymentType heuristics
│   ├── BudgetService.kt                     ✅ Budget threshold checking after expense save + **forecast checking** (checkForecastsAfterExpense)
│   ├── ForecastService.kt                   ✅ Linear burn rate forecasting + **recurring-aware projections** (splits recurring vs discretionary spending)
│   ├── RecurringExpenseService.kt           ✅ Recurring expense detection engine (interval analysis, 4 cycle types, 15-min cache, period info for forecasting)
│   ├── RecurringReminderWorker.kt           ✅ Daily WorkManager worker — upcoming/overdue recurring expense notifications (@HiltWorker)
│   ├── QuarterlyReviewWorker.kt             ✅ Fires 1st of Apr/Jul/Oct/Jan at 09:00 — generates quarterly review + notification (@HiltWorker)
│   ├── YearInReviewWorker.kt                ✅ Fires Dec 28 at 18:00 — generates year-in-review + notification (@HiltWorker)
│   ├── NotificationActionReceiver.kt        ✅ BroadcastReceiver for notification "Ignore" action — 5s undo window before persisting exclude
│   ├── NotificationHelper.kt               ✅ Expense channel + Budget Alerts channel + **forecast notifications** + **Recurring Reminders channel** + **quarterly_review channel** + **yearly_review channel** + **budget_burndown channel** + **income_received channel (LOW importance — income tracking Phase 2)** + **Categorize/Ignore action buttons**
│   ├── DataManagementService.kt             ✅ Export, backup/restore, data reset
│   ├── SampleDataService.kt                 ✅ Sample data generation for testing/demo
│   ├── PinManager.kt                        ✅ SHA-256 + salt PIN hashing, verification, timeout logic
│   └── AppLockLifecycleObserver.kt          ✅ ProcessLifecycleOwner observer — background/foreground lock management
└── utils/
    ├── SmsParser.kt                         ✅ Backward-compat facade → SmsParserRegistry
    ├── Constants.kt                         ✅ formatAsCurrency()
    ├── UsageSummaryGenerator.kt             ✅ Usage summary text generation
    ├── excel/
    │   ├── ExcelParser.kt                   ✅ Apache POI .xlsx parser (dual date formats)
    │   └── ExcelCategoryMapper.kt           ✅ 55+ label→category ID mappings
    └── parsers/
        ├── SmsParserStrategy.kt             ✅ Strategy interface for SMS parsers — `parseSms(...)` returns `ParsedSms` (Expense / Income / NotARelevant)
        ├── ParsedSms.kt                     ✅ Sealed result type: `ExpenseResult(expense, transactionCost?, isCardApprovalUpdate)` / `IncomeResult(income)` / `NotARelevantMessage` (income tracking Phase 2)
        ├── SmsParserRegistry.kt             ✅ Central dispatcher (sender → parser); `parseSms(...)` returns `ParsedSms`
        ├── MpesaSmsParser.kt                ✅ M-PESA parser — 8 expense types + income detection (salary, business, M-Shwari/agent transfers, peer receive)
        ├── MpesaStatementParser.kt          ✅ PDF text extraction, 13+ transaction type regex, password-protected PDF support; income rows emitted as `IncomeTransaction` with `parserSource = STATEMENT_IMPORT`
        └── NcbaBankParser.kt                ✅ NCBA bank parser (3 expense types + bank-credit income detection)
```

### Backend (Dormant — not used by app)

```
backend/
├── src/
│   ├── index.js                             Express server setup
│   ├── config/daraja.js                     Daraja API config
│   ├── routes/
│   │   ├── payment.js                       STK Push endpoints
│   │   └── callback.js                      M-PESA callback handler
│   ├── services/
│   │   ├── darajaService.js                 Daraja API integration
│   │   ├── paymentService.js                Transaction management
│   │   └── databaseService.js               Prisma + libSQL
│   ├── generated/prisma/                    Auto-generated Prisma client
│   └── middleware/validation.js             Joi validation
├── prisma/
│   ├── schema.prisma                        Transaction model
│   └── migrations/                          SQLite migrations
├── prisma.config.js                         Prisma 7 config
└── package.json
```

> The backend is deployed on Railway at `pesatrack-production.up.railway.app` but the Android app makes no API calls to it. It remains available if STK Push is re-introduced in the future.

---

## Bug Fixes & Improvements History

### Recent Features

- **Merchants (batch re-categorization by paybill account)** — User request: many M-PESA paybill txns had been auto-categorized under a single label ("Beehive Service") because they share a paybill number, but each account is actually a different merchant. New dedicated `Merchants` screen accessible from the Expenses TopAppBar overflow → "Manage merchants". Lists distinct `(recipientName, account)` combos across all history: paybills are keyed on `<recipientName>::<recipient>` so aggregator paybills (e.g. NCBA Loop 247247, Beehive Service) fan out into one row per account; every other payment type collapses to `COALESCE(recipientName, recipient)`. Each row shows current dominant category (mode across the group's categorized txns), transaction count, total spent, and a "Mixed categories" hint when the group already spans more than one category. Search field on top mirrors the Expenses-search pattern (case-insensitive contains on merchant/account/category name). Tapping a row opens a `ModalBottomSheet` with the txn list preview and a "Reassign all N to another category" primary button → `GroupedCategoryPicker`. Reassign does three things in one coroutine: (1) `UPDATE expenses SET categoryId=?, isCategorized=1` for every row in the group; (2) `deleteMapping(...)` for the previous recipient mapping so the old dominant category can't drown out the new one via usage counts; (3) `savePaybillMapping` / `saveMapping` with the new category so future SMS to the same merchant+account auto-route correctly. Success feedback via snackbar ("Reassigned N transactions to <Category>"). New DAO queries: `getMerchantGroups()`, `getMerchantCategoryCounts()`, `getExpensesForMerchantGroup(groupKey)`, `reassignCategoryForMerchantGroup(groupKey, categoryId)`. New repository types: `MerchantGroupSummary` + `getMerchantGroupsWithDominantCategory()` + `reassignMerchantGroupCategory(...)`. Verified with `./gradlew :app:compileDebugKotlin` (success). Serves **awareness before action** (users see per-account counts and totals before deciding) and **honest numbers** (the "Mixed categories" hint stops the current dominant from being read as the only truth). Files: [Screen.kt](../android/app/src/main/java/com/pesatrack/presentation/navigation/Screen.kt), [NavGraph.kt](../android/app/src/main/java/com/pesatrack/presentation/navigation/NavGraph.kt), [ExpenseListScreen.kt](../android/app/src/main/java/com/pesatrack/presentation/screens/expenses/ExpenseListScreen.kt), [MerchantsUiState.kt](../android/app/src/main/java/com/pesatrack/presentation/screens/merchants/MerchantsUiState.kt), [MerchantsViewModel.kt](../android/app/src/main/java/com/pesatrack/presentation/screens/merchants/MerchantsViewModel.kt), [MerchantsScreen.kt](../android/app/src/main/java/com/pesatrack/presentation/screens/merchants/MerchantsScreen.kt), [ExpenseDao.kt](../android/app/src/main/java/com/pesatrack/data/local/database/dao/ExpenseDao.kt), [ExpenseRepository.kt](../android/app/src/main/java/com/pesatrack/data/repository/ExpenseRepository.kt).

- **Expenses screen — free-text search** — User request: `"in expenses screen, add ability to search for an expense"`. Added a persistent `OutlinedTextField` search bar above the list (leading `Icons.Filled.Search`, trailing clear-`Close` icon that appears only when the query is non-empty, `ImeAction.Search`, placeholder "Search recipient, category, amount…"). Matches are computed client-side against `recipient`, `recipientName`, `categoryName`, `notes`, and the integer amount (with commas/spaces stripped so `"1200"` finds `KES 1,200`), all lowercased for case-insensitive contains. When the query is empty the screen renders exactly as before (month summary card + grouped-by-date list); when it's non-empty the month summary card is hidden (it reflects the whole month, not the filter — showing it would be a lie), `supportingText` on the search field shows the live match count, and an empty result set drops in a "No expenses match "…"" panel with a `Icons.Filled.SearchOff` glyph and a nudge to try a different term. `ExpensesUiState` gains `searchQuery: String` so the query survives configuration changes; `ExpensesViewModel.setSearchQuery(q)` is a plain setter — filtering happens in the screen via `remember(uiState.expenses, query)` so we don't hold a second list in the ViewModel. Verified with `./gradlew :app:compileDebugKotlin` (success). Files: [ExpensesUiState.kt](../android/app/src/main/java/com/pesatrack/presentation/screens/expenses/ExpensesUiState.kt), [ExpensesViewModel.kt](../android/app/src/main/java/com/pesatrack/presentation/screens/expenses/ExpensesViewModel.kt), [ExpenseListScreen.kt](../android/app/src/main/java/com/pesatrack/presentation/screens/expenses/ExpenseListScreen.kt).

- **Category × Month grid — fix empty-on-first-open + widen cells to show full amounts** — Two follow-ups to the grid feature after the user reported: `"when i click view all from the home screen to the landing tab, there are no values. if i move to a previous year and back, the values are populated"` and `"we should be able to view the full value in a cell and not partial value. remove kes and just show the value to maximize space"`. **Bug fix (empty grid on deep-link):** `AnalyticsViewModel.init` sets `selectedYearForYearly = currentYear` inside an async coroutine (it first awaits `incomeRepository.refreshMonthStartDay()` to align periods with the user's budget cycle). Home's "Trend by group → View all" fires `LaunchedEffect(initialSection)` immediately on composition of the Analytics screen, calling `selectTab(YEARLY)` + `selectYearlyView(GRID)` before that init coroutine has landed — so both `loadYearlyData()` and `loadYearlyGrid()` read the default `0` and returned an empty grid. A `prev-year → next-year` round-trip masked it because by then init had populated the year and the ±1 math ran off a real value. Fix: both `loadYearlyData()` and `loadYearlyGrid()` now normalise `year == 0 → Calendar.getInstance().get(Calendar.YEAR)` and write the fallback back into state so subsequent year navigation stays coherent. Comment cites the exact race for the next reader. **Cell readability:** grid cells were fixed at 88.dp with `formatAsCurrency()` (`KES 12,345.67`) + `TextOverflow.Ellipsis`, so large amounts truncated mid-digit and the "KES" prefix chewed into space on every column. Replaced with a compact private `formatGridAmount(value: Double)` (locale-aware thousands separator, no decimals — KES is implicit from the surrounding UI) and made the column width dynamic: `rememberTextMeasurer` measures the widest actual amount in the grid (using an "8"-only string of the widest formatted length) against the cell's Text style and `LocalDensity`, floors at 72.dp, and applies the resulting width uniformly to every column so rows stay aligned. `GridAmountCell` drops `TextOverflow.Ellipsis` for `softWrap = false` — every value now fits without partial truncation. Verified with `./gradlew :app:compileDebugKotlin` (success). Files: [AnalyticsViewModel.kt](../android/app/src/main/java/com/pesatrack/presentation/screens/analytics/AnalyticsViewModel.kt), [AnalyticsScreen.kt](../android/app/src/main/java/com/pesatrack/presentation/screens/analytics/AnalyticsScreen.kt).

- **Category × Month grid — CSV export** — Follow-on to the grid feature: the "Excel replacement" now round-trips into a real spreadsheet. `CategoryMonthGridCsvExporter.buildCsv(grid)` serialises the loaded [CategoryMonthGrid](../android/app/src/main/java/com/pesatrack/domain/models/AnalyticsModels.kt) as RFC-4180 CSV — header row is `Category, {12 period labels}, Total` (partial current period marked with `*`, matching the on-screen glyph), body preserves the group → sub-category order the repository produces (sub-categories indented with two spaces so the hierarchy survives in Excel/Sheets), empty periods render as blank fields (not `0` — same "honest numbers" reasoning as `—` in the UI), whole-KES precision without thousands separators for clean re-import, plus trailing `#` comment lines that record whether fees are included and whether a period is still in-progress. `AnalyticsUiState` gains `pendingGridExportFile: java.io.File?` + `yearlyGridExportError: String?`. `AnalyticsViewModel.exportYearlyGridAsCsv(context)` writes the CSV to `context.cacheDir/exports/pesatrack-category-grid-{year}.csv` via the existing FileProvider (`@xml/file_paths` already exposes `cache-path/exports`) and publishes the File to state; `AnalyticsScreen.ChartsTabContent` observes via `LaunchedEffect(pendingGridExportFile)`, resolves through `FileProvider.getUriForFile("${packageName}.fileprovider", file)`, fires `Intent.ACTION_SEND` with `type = "text/csv"` and a `Intent.createChooser`, then calls `viewModel.consumeGridExport()` to prevent re-firing on recomposition. The grid card exposes the trigger via a compact `IconButton(Icons.Filled.Share)` sitting between the title and the "Hide fees / Show fees" text button. Ties to AGENTS.md "local-first" (no cloud round-trip) and "honest numbers" (blanks vs zeros preserved through the file). Verified with `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest` (success). Files: [CategoryMonthGridCsvExporter.kt](../android/app/src/main/java/com/pesatrack/data/export/CategoryMonthGridCsvExporter.kt), [AnalyticsUiState.kt](../android/app/src/main/java/com/pesatrack/presentation/screens/analytics/AnalyticsUiState.kt), [AnalyticsViewModel.kt](../android/app/src/main/java/com/pesatrack/presentation/screens/analytics/AnalyticsViewModel.kt), [AnalyticsScreen.kt](../android/app/src/main/java/com/pesatrack/presentation/screens/analytics/AnalyticsScreen.kt).

- **Category × Month pivot grid (Excel replacement) + Home "Trend by group" preview** — User request: `"I used to have a month-on-month Excel for a year with columns Expense, Jan, Feb, … and totals. I want a similar feature."` and `"I also want it on the home screen but showing only a few groups, with an option to view all which takes you to the landing tab, almost similar to the 'By Category' table."` Delivered as a new **Yearly → Grid** sub-view in Analytics plus a compact preview on Home. Data layer: `ExpenseRepository.getCategoryMonthGridForYear(year, includeFees)` fires 12 `ExpenseDao.getCategoryTotalsForMonth` calls (one per period via `MonthPeriod.rangeForPeriodStart`, honouring the user's `monthStartDay`), transposes the results into a `CategoryMonthGrid(rows: List<GridRow>, periodTotals, grandTotal, partialPeriodIndexes)`, synthesises group (depth 0) rows by summing children (PesaTrack expenses live on leaf sub-categories; groups rarely have direct spend), backfills name/color from `CategoryDao.getAllCategoriesSync`, and drops transaction fees (category 606) unless the user toggles them on. `getGroupTrendPreview(monthsBack = 3, topN = 5)` returns null when fewer than two periods have any spend; direction thresholds ±5%/±25% → `UP2/UP/FLAT/DOWN/DOWN2/INSUFFICIENT`. Investment & Savings (group 18) is flagged `isInvestment = true` so the UI can invert direction semantics (up = good). UI: `YearlyView { OVERVIEW, GRID }` enum + segmented toggle at the top of the Yearly tab (defaults to OVERVIEW so existing users see no change). `CategoryMonthGridCard` renders with a sticky first column and a horizontally-scrolled data area (two `rememberScrollState`s kept in sync via `LaunchedEffect(bodyScroll.value)`); cells: `null` → `—` (empty vs zero), current period marked with `*`, "Hide fees / Show fees" toggle in the header. Group rows are tappable to expand into their sub-categories (state held in `AnalyticsUiState.yearlyGridExpandedGroups`). Home: new [GroupTrendPreview.kt](../android/app/src/main/java/com/pesatrack/presentation/screens/home/GroupTrendPreview.kt) — a 3-period × top-5 groups table below the existing "By Category" section, header "Trend by group" + "View All" TextButton that deep-links via `Screen.Analytics.SECTION_YEARLY_GRID` straight to Yearly → Grid (handled in `AnalyticsScreen`'s `LaunchedEffect(initialSection)` alongside the existing `SECTION_BY_CATEGORY`). Direction glyphs: `▲▲/▲/•/▼/▼▼/—`, tinted red for over-spend and green for under-spend, semantically inverted for Investment & Savings. Ties to AGENTS.md "awareness before action" (users can now see a year of variation per group at a glance without leaving the app) and "save/invest by default" (the Investment & Savings group is surfaced with inverted semantics so more savings reads as good news). Verified with `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest` (both success). Files: [AnalyticsModels.kt](../android/app/src/main/java/com/pesatrack/domain/models/AnalyticsModels.kt), [ExpenseRepository.kt](../android/app/src/main/java/com/pesatrack/data/repository/ExpenseRepository.kt), [AnalyticsUiState.kt](../android/app/src/main/java/com/pesatrack/presentation/screens/analytics/AnalyticsUiState.kt), [AnalyticsViewModel.kt](../android/app/src/main/java/com/pesatrack/presentation/screens/analytics/AnalyticsViewModel.kt), [AnalyticsScreen.kt](../android/app/src/main/java/com/pesatrack/presentation/screens/analytics/AnalyticsScreen.kt), [Screen.kt](../android/app/src/main/java/com/pesatrack/presentation/navigation/Screen.kt), [NavGraph.kt](../android/app/src/main/java/com/pesatrack/presentation/navigation/NavGraph.kt), [HomeUiState.kt](../android/app/src/main/java/com/pesatrack/presentation/screens/home/HomeUiState.kt), [HomeViewModel.kt](../android/app/src/main/java/com/pesatrack/presentation/screens/home/HomeViewModel.kt), [HomeScreen.kt](../android/app/src/main/java/com/pesatrack/presentation/screens/home/HomeScreen.kt), [GroupTrendPreview.kt](../android/app/src/main/java/com/pesatrack/presentation/screens/home/GroupTrendPreview.kt).

- **"Savings rate" formula corrected — savings = money moved into Investment & Savings, not `income − spend` (honest-numbers fix)** — User reported: `"in a new month income, it shows 456,000 received, 74% saved. I have not saved anything yet, where is this value coming from"`. The label was misleading twice: (a) the arithmetic `(received − spent) / received` inflates to near-100% at the start of any period because `spent` is still near zero, and (b) even at end-of-period that number really represents "unspent", not "deliberately saved" — an unpaid rent bill is not savings. Both the Home `MonthlySummaryCard` "% saved" fragment and the Analytics `SavingsRateInsightCard` (current-month + 3-month rolling) used this same formula. Fixed both surfaces to use the same definition: `savings = sum of expenses categorised under the Investment & Savings group (18)` (the same figure that already drives the "KES X (Y% of income) invested" line on Home), so `savingsRate = investment / income` (0..100). Home: `HomeViewModel.loadIncomeData` now calls new `ExpenseRepository.getInvestmentInRange(start, end)` (wrapping the existing `ExpenseDao.getInvestmentTotalInRange`) instead of `getSpendingInRange`, computes `investment / detected × 100`, and publishes it back as `HomeUiState.savingsRatePct` (field name restored to its previous label but with a KDoc spelling out the new semantics and why the earlier "% spent" workaround was reverted); the received line renders "KES 456,000 received · 5% saved" at start-of-period instead of "· 74% saved". Analytics: `AnalyticsViewModel.computeSavingsRate` uses the same `getInvestmentInRange` and coerces to [0, 100]; `SavingsRateData` renames `currentMonthSpend → currentMonthSavings`; `SavingsRateInsightCard`'s "Tap for assumptions" body now reads "Based on {source} of {income} and {savings} moved into Investment & Savings this month. Savings rate = savings ÷ income." (dropped the "Transaction fees count as spend" note — no longer relevant). Ties to AGENTS.md "honest numbers" and "save/invest by default" principles: the number on the card now matches what a user would tell a spouse or a coach they've actually saved. Verified with `./gradlew :app:compileDebugKotlin :app:lintDebug` (success). Files: [ExpenseRepository.kt](../android/app/src/main/java/com/pesatrack/data/repository/ExpenseRepository.kt), [HomeUiState.kt](../android/app/src/main/java/com/pesatrack/presentation/screens/home/HomeUiState.kt), [HomeViewModel.kt](../android/app/src/main/java/com/pesatrack/presentation/screens/home/HomeViewModel.kt), [HomeScreen.kt](../android/app/src/main/java/com/pesatrack/presentation/screens/home/HomeScreen.kt), [AnalyticsUiState.kt](../android/app/src/main/java/com/pesatrack/presentation/screens/analytics/AnalyticsUiState.kt), [AnalyticsViewModel.kt](../android/app/src/main/java/com/pesatrack/presentation/screens/analytics/AnalyticsViewModel.kt), [AnalyticsScreen.kt](../android/app/src/main/java/com/pesatrack/presentation/screens/analytics/AnalyticsScreen.kt).

- **Delete an income row (fixes "no way to remove a manual entry entered in error")** — When a user enters income manually via the Income screen's "+ Add income" dialog and later realises the amount / sender / date is wrong, there was no destructive escape hatch. Long-press was already occupied by the exclude/restore ("Not income") toggle, and "Not income" only hides the row from totals — the record stays. Added a permanent-delete affordance in [CategorizeIncomeScreen.kt](../android/app/src/main/java/com/pesatrack/presentation/screens/categorize_income/CategorizeIncomeScreen.kt) (the detail screen users already reach by tapping any row): a `Icons.Filled.Delete` `IconButton` (error-tinted) in the `TopAppBar` `actions` slot, gated on `income != null && !isSaving && !isDeleting`. Tap raises a confirmation `AlertDialog` — title "Delete this income?", body explaining that this is permanent and pointing users to "Not income" as the reversible alternative, red "Delete" confirm + "Cancel" dismiss. On confirm, `CategorizeIncomeViewModel.delete()` runs `IncomeRepository.delete(id) → IncomeTransactionDao.deleteById(id)` and re-uses the existing `isSaved = true` completion flag so the screen's existing `LaunchedEffect(uiState.isSaved)` pops back without a second observer. `CategorizeIncomeUiState` gained an `isDeleting: Boolean` flag so the confirm dialog can lock its buttons while the DELETE is in flight, and `IncomeRepository` gained `suspend fun delete(id: Long)` alongside the existing `setExcluded`. Verified with `./gradlew :app:compileDebugKotlin :app:lintDebug` (success). Files: [IncomeRepository.kt](../android/app/src/main/java/com/pesatrack/data/repository/IncomeRepository.kt), [CategorizeIncomeUiState.kt](../android/app/src/main/java/com/pesatrack/presentation/screens/categorize_income/CategorizeIncomeUiState.kt), [CategorizeIncomeViewModel.kt](../android/app/src/main/java/com/pesatrack/presentation/screens/categorize_income/CategorizeIncomeViewModel.kt), [CategorizeIncomeScreen.kt](../android/app/src/main/java/com/pesatrack/presentation/screens/categorize_income/CategorizeIncomeScreen.kt).

- **Home "received this month" was showing the calendar-month figure regardless of `monthStartDay` (bug fix)** — User with `monthStartDay = 23` reported Home showed `KES 622,280 received · 10% saved` while the Income screen for the same period showed `KES 531,009 received this month`. Root cause: `HomeViewModel.init` fired `initializeData()` and `loadIncomeData()` as two separate `viewModelScope.launch` blocks with no ordering. `initializeData` was responsible for calling `expenseRepository.refreshMonthStartDay()` / `budgetRepository.refreshMonthStartDay()` / `incomeRepository.refreshMonthStartDay()` (they all cache `_monthStartDay` for synchronous access), while `loadIncomeData` immediately called `expenseRepository.getExpensesForCurrentMonth()` — which binds its date range from `_monthStartDay` at call-time — and then `.collect { … incomeRepository.currentMonthBounds() … }` inside the collect body. When the collect fired before the refresh coroutine landed, `_monthStartDay` was still the default `1` (calendar month), so the first (and often only, if no new expenses arrived) emission computed `receivedThisMonth` against `Jul 1 – Jul 31` instead of the user's `Jul 23 – Aug 23` cycle — pulling in ~91K of extra income that had already been counted in the previous cycle by every other screen. Fix: at the top of `loadIncomeData`'s coroutine (before the `getExpensesForCurrentMonth()` call), explicitly `await` `expenseRepository.refreshMonthStartDay()` and `incomeRepository.refreshMonthStartDay()`. Cheap — it's a `.first()` on a DataStore Flow, and a no-op after the first call. Also updated the fn KDoc to spell out the race so this doesn't get regressed. IncomeScreen was already correct (its `init` explicitly `await`s `refreshMonthStartDay()` before `seedAnchorToNow` + `reload`), so no changes needed there. Files: [HomeViewModel.kt](../android/app/src/main/java/com/pesatrack/presentation/screens/home/HomeViewModel.kt).

- **Home "(41%) invested" percentage was computed against expenses, not income (honest-numbers fix)** — Follow-on to the receivedThisMonth fix. `MonthlySummaryCard` rendered e.g. `KES 230,000 (41%) invested` right next to `KES 622,280 received · 10% saved`. Users reasonably read the 41% as a share of income; in fact it was `investmentTotal / totalExpenses * 100` (denominator was `uiState.totalThisMonth`, i.e. the "Total expenses this month" figure printed one line above). At `monthStartDay = 23` with `total = 561K` and `investmentTotal = 230K` that's ~41%; with income at 531K the honest number is ~43% (of income). Fixed to honour the AGENTS.md "honest numbers" principle: new local `InvestmentShare(pct, basisLabel)` picks the denominator by priority — income when `received > 0` (labelled "of income"), else expenses when `total > 0` (labelled "of spending"), else neither (renders `KES X invested` with no percentage rather than dividing by zero). Existing "No investments this month — even a small amount counts" nudge is untouched when investment is zero. Line now reads e.g. `KES 230,000 (43% of income) invested` for the common case, and `(41% of spending)` for users on brand-new installs / with SMS permission still denied. Files: [HomeScreen.kt](../android/app/src/main/java/com/pesatrack/presentation/screens/home/HomeScreen.kt).

- **Income screen — step through past months / quarters / years** — The Income screen previously locked users into the current period; the only way to review past income was to switch between MONTH / QUARTER / YEAR tabs, each of which showed only "now". Added a `< label >` navigator row inside `IncomeHeaderCard` — `Icons.Filled.ChevronLeft` / `ChevronRight` flanking the (now larger, `titleMedium` / `SemiBold`) period label, so the user can walk backwards through any number of periods and forward again. `IncomeUiState` gained anchor fields (`anchorYear`, `anchorMonth1Based`, `anchorQuarter1Based`) plus a `canGoNext` flag that disables the forward arrow at the current period. `IncomeViewModel` gained `previousPeriod()` / `nextPeriod()` step functions (per-period `Calendar` arithmetic — month rolls year boundaries; quarter uses 1–4 with year wrap; year is a simple `± 1`); a private `seedAnchorToNow(period)` seeder called from `init` and `setPeriod` so switching MONTH → QUARTER → YEAR always starts from the current view instead of a stale anchor; and a private `isCurrentAnchor(state)` predicate reused to compute `canGoNext` after every reload. `boundsForPeriod` was refactored from `(period)` to `(state)`, deriving bounds from the anchor: MONTH via `MonthPeriod.rangeForPeriodStart(anchorYear, anchorMonth1Based, monthStartDay)` (still honors the user's budget-cycle start day), QUARTER as a `Calendar`-computed 3-month span from `(anchorQuarter - 1) * 3`, YEAR as a full calendar year from `anchorYear`. Effective-income Detected/Manual reconciliation is intentionally suppressed when the anchor isn't the current period — the "using set income vs detected" tension only makes sense for the live month; past months just show the historical detected total. All existing surfaces (source breakdown, uncategorized list, per-transaction rows, income icon set) render unchanged, they just source from the new period bounds. Files: [IncomeUiState.kt](../android/app/src/main/java/com/pesatrack/presentation/screens/income/IncomeUiState.kt), [IncomeViewModel.kt](../android/app/src/main/java/com/pesatrack/presentation/screens/income/IncomeViewModel.kt), [IncomeScreen.kt](../android/app/src/main/java/com/pesatrack/presentation/screens/income/IncomeScreen.kt).

- **Paybill expense rows show "PaybillName · Account"** — Completion of the aggregator-paybill fix already shipped for auto-categorization / mapping. `ExpenseCard`'s title / recipient-info line was reading `expense.recipientName ?: expense.recipient`, so a row categorised via the new `PAYBILL::<paybill>::<account>` composite key still surfaced as just the aggregator name (e.g. `NCBA LOOP`) — the account, which is what actually distinguishes one merchant from another under the same paybill, was hidden. New public helper `expenseRecipientDisplay(expense: Expense): String?` in [ExpenseCard.kt](../android/app/src/main/java/com/pesatrack/presentation/components/ExpenseCard.kt) returns `"$name · $account"` when the expense is `PaymentType.PAY_BILL` and both `recipientName` and `recipient` are present, non-blank, and not equal case-insensitively; every other payment type keeps the existing `recipientName ?: recipient` fallback. Wired into both the title fallback (`categoryName ?: recipientDisplay ?: expense.recipient`) and the secondary recipient-info line beneath the category. Result: NCBA Loop paybill 247247 shows as `NCBA LOOP · BEES` (or whichever account was used), single-merchant paybills like KPLC/Water/WiFi are unaffected because their account is stable and often already matches the paybill name. Helper is `null`-returning by design so callers can chain their own final fallback (the raw `recipient`). File: [ExpenseCard.kt](../android/app/src/main/java/com/pesatrack/presentation/components/ExpenseCard.kt).

- **NCBA parser regression tests for real production credit SMS** — Two production-shaped credit SMS variants were failing / silently mishandled in earlier runs and had no test coverage. Added `NcbaBankParserIncomeTest."real NCBA credit SMS with sender and date-before-from is detected as income"` (`KES 524,498.10 on 23/07/2026 at 15:25 from MICROSOFT RESEARCH & DEVELOPMENT KE 16000. Ref: FT26204ZG5R2`, asserts amount + ref + sender contains `MICROSOFT`) — covers the date-before-`from` ordering and multi-word sender names, which the previous fixtures didn't exercise. Added `NcbaBankParserIncomeTest."real NCBA credit SMS without from clause is detected as income"` (`KES 3,125.00 on 20/07/2026 at 11:11 for . Ref: FT26201MC1KB`, asserts amount + ref) — covers the empty `for` clause with no `from`, which was silently returning `NotARelevantMessage` in production. Verified with `./gradlew :app:testDebugUnitTest` (success). File: [NcbaBankParserIncomeTest.kt](../android/app/src/test/java/com/pesatrack/utils/parsers/NcbaBankParserIncomeTest.kt).

- **Usage-context snapshot expanded for feedback triage** — The PII-free "Usage Context" block appended to feedback / About shares (`UsageSummaryGenerator.generate` + `.asJson`) previously read `D1=no D7=no D30=no` on brand-new installs, showed `0 SMS parsed` even after a large historical inbox import (bulk-import counter was never wired), didn't reflect the user changing their mind on the SMS permission after onboarding, showed `0 budgets` for users who restored a backup (create-action counter was blind to restored state), and offered no direct signal on whether the user had actually reached anything usefully categorized. Six improvements shipped together: **(1) Retention states are three-valued** — new `retentionLabel(flagFired, elapsedMs, windowMs)` reports `yes` / `pending` / `no` (windows `24h` / `7d` / `30d` from `installTimestamp`), so a Day-0 install no longer looks like a churner. **(2) Live vs bulk-import SMS split** — new DataStore counter `KEY_COUNT_SMS_IMPORTED` (+ `AppPreferences.incrementSmsImportedCount(byN)` batching helper + new generic `incrementCounterBy` primitive) is incremented once per successful `SmsImportService` run by `newExpensesImported + newIncomesImported`; the existing `KEY_COUNT_SMS_PARSED` remains live-only (only `SmsReceiver` writes it), and both are surfaced on the snapshot as `Activity: N SMS (X live, Y import)` — plus `countSmsImported` in the JSON. **(3) Current SMS permission state** — `UsageSummaryGenerator` now injects `@ApplicationContext Context` and reads `READ_SMS` + `RECEIVE_SMS` via `ContextCompat.checkSelfPermission` at snapshot time; when the current runtime state differs from what onboarding recorded, the line reads `SMS=skipped (now: granted)` / `SMS=granted (now: denied)` etc.; JSON gets `smsPermissionCurrent = granted / partial / denied`. **(4) First-value signal + failure-mode counters** — three new `ExpenseDao` queries: `getFirstValueExpenseCount()` (categorized, non-excluded, non-Miscellaneous), `getUncategorizedBacklogCount()` (`isCategorized = 0 AND isExcluded = 0`), and `getMiscellaneousAutoCatCount()` (auto-cat fell back to `categoryId = 1201`). `UsageSummaryGenerator` now injects `ExpenseDao` and emits `First value: yes/no (backlog=N, misc=M)` on the text snapshot plus `firstValue`, `firstValueCount`, `uncategorizedBacklog`, `miscellaneousAutoCat` in JSON. **(5) DB-derived budget count** — the "budgets" figure on the Activity line and the `Budgets` entry in the `Features:` set now come from `BudgetDao.getActiveBudgetCount()` (injected into `UsageSummaryGenerator`) instead of `KEY_COUNT_BUDGETS_CREATED`, so users who restored an existing budget from a backup no longer look like they never used budgeting. The create-action counter is preserved in JSON as `countBudgetsCreated` for cases where the distinction matters. **(6) Restore timestamp surfaced** — new DataStore key `KEY_LAST_RESTORE_TIMESTAMP` (+ `AppPreferences.recordRestoreCompleted()` overwrite-on-every-call helper, unlike `recordMilestone`) is stamped by `DataManagementService.restoreDatabase` on success. When non-zero, the snapshot inserts a `Restored: Xd ago (create-action counters may under-report)` line right below the `Onboarding:` line, so triage immediately knows why `countManualEntries` / `countCategorizations` / `countBudgetsCreated` may be zero despite a fully-populated DB. `lastRestoreTimestamp` is also emitted in JSON. Consumers (`DataManagementService.asJson`, `HomeViewModel` feedback prompt, `AboutViewModel` / `AboutScreen` share) benefit transparently — no call-site changes needed. Verified with `./gradlew :app:compileDebugKotlin` (success). Files: [AppPreferences.kt](../android/app/src/main/java/com/pesatrack/data/local/preferences/AppPreferences.kt), [SmsImportService.kt](../android/app/src/main/java/com/pesatrack/services/SmsImportService.kt), [DataManagementService.kt](../android/app/src/main/java/com/pesatrack/services/DataManagementService.kt), [ExpenseDao.kt](../android/app/src/main/java/com/pesatrack/data/local/database/dao/ExpenseDao.kt), [UsageSummaryGenerator.kt](../android/app/src/main/java/com/pesatrack/utils/UsageSummaryGenerator.kt).

- **Target Android 16 (API 36) for Google Play compliance** — Google Play's 31 Aug 2026 target API deadline requires `targetSdk = 36`. Bumped `compileSdk` and `targetSdk` from 35 to 36 in [app/build.gradle.kts](../android/app/build.gradle.kts), AGP from 8.7.3 to 8.9.1 in [build.gradle.kts](../android/build.gradle.kts) (first release with stable API 36 support), and the Gradle wrapper from 8.9 to 8.11.1 in [gradle-wrapper.properties](../android/gradle/wrapper/gradle-wrapper.properties) (minimum required by AGP 8.9). No source-code changes were needed for behavior changes — edge-to-edge is already enforced from the API 35 bump, the app has no foreground services, and Compose `BackHandler` already handles predictive-back opt-in. Fixed a new lint error surfaced by AGP 8.9's stricter checks (`PermissionImpliesUnsupportedChromeOsHardware`): added `<uses-feature android:name="android.hardware.telephony" android:required="false" />` to the manifest so the app still installs on tablets/Chromebooks (SMS features simply won't function there). Verified with `./gradlew :app:compileDebugKotlin`, `:app:lint`, and `:app:assembleDebug` — all succeed with only pre-existing warnings. Files: [android/app/build.gradle.kts](../android/app/build.gradle.kts), [android/build.gradle.kts](../android/build.gradle.kts), [android/gradle/wrapper/gradle-wrapper.properties](../android/gradle/wrapper/gradle-wrapper.properties), [AndroidManifest.xml](../android/app/src/main/AndroidManifest.xml).

- **Notification "Categorize" action button now dismisses the notification** — Tapping the "Categorize" action button on the "New Expense" notification opened the categorize screen but left the notification in the tray, because `NotificationCompat.setAutoCancel(true)` only fires on the main content tap, not on action buttons. Fixed by routing the Categorize action through `NotificationActionReceiver` (`ACTION_CATEGORIZE_EXPENSE`) instead of a direct `PendingIntent.getActivity`: the receiver now cancels the notification via `NotificationManager.cancel(expenseId.toInt())` and then starts `MainActivity` with the same `navigate_to=categorize` + `expense_id` extras the content intent uses, so the deep-link behavior is unchanged. The tap-to-open content path already dismissed correctly via `setAutoCancel`; the Ignore action already handled dismissal via its own broadcast path. The income and misc-auto-categorized notifications only have tap-to-open (no action button), so they were already fine. Files: [NotificationActionReceiver.kt](../android/app/src/main/java/com/pesatrack/services/NotificationActionReceiver.kt), [NotificationHelper.kt](../android/app/src/main/java/com/pesatrack/services/NotificationHelper.kt).

- **Home Monthly Summary card now honors `monthStartDay`** — The card's "Total expenses this month" (and the accompanying investment / recent-expenses / By-Category numbers) was still slicing by calendar month even after Budgets, Income, Analytics and the savings-rate card had all moved to the user's configured budget cycle. A salary-on-the-25th user saw their Budget period as "Jun 25 – Jul 24" but the Home card said "July 2026" with only the calendar-July subset of spend, so the received-vs-spent line implicitly compared two different periods. `ExpenseRepository` now injects `AppPreferences` and mirrors the `BudgetRepository` / `IncomeRepository` pattern — cached `_monthStartDay` field, `monthStartDay` getter, `refreshMonthStartDay()` suspend hook — and `getCurrentMonthRange()` delegates to `MonthPeriod.currentRange(_monthStartDay)`. Every already-existing "current month" query (`getExpensesForCurrentMonth`, `getTotalForCurrentMonth`, `getInvestmentTotalForCurrentMonth`) becomes offset-aware without touching call sites. New offset-aware wrapper `getRecentlyActiveCategoryTotalsForCurrentMonth(limit)` replaces the calendar-month year/month lookup the Home "By Category" section was doing. `HomeViewModel.initializeData` (and `refresh()`) now call `expenseRepository.refreshMonthStartDay()` alongside the existing budget/income refreshes and update `HomeUiState.currentMonthLabel` via `MonthPeriod.labelForRange` so the header reads "July 2026" (default) or "Jun 25 – Jul 24, 2026" (offset). `MonthlySummaryCard` takes the label as a parameter (falls back to calendar-month text if blank). Files: [ExpenseRepository.kt](../android/app/src/main/java/com/pesatrack/data/repository/ExpenseRepository.kt), [HomeUiState.kt](../android/app/src/main/java/com/pesatrack/presentation/screens/home/HomeUiState.kt), [HomeViewModel.kt](../android/app/src/main/java/com/pesatrack/presentation/screens/home/HomeViewModel.kt), [HomeScreen.kt](../android/app/src/main/java/com/pesatrack/presentation/screens/home/HomeScreen.kt).

- **Paybill auto-categorization fixed for aggregator paybills** — Previously, once any Paybill payment (e.g. paybill 247247 / account BEES) was categorized, `RecipientMappingRepository.getCategoryForRecipientOrName` would auto-apply that same category to every future Paybill payment sharing the same paybill business name, because the learned mapping was keyed on `recipientName` (the paybill business, e.g. "NCBA LOOP") which for aggregator paybills is shared across unrelated merchants — only the account distinguishes them. New Paybill payments to the same paybill under different account names were being silently mis-categorized. Fix introduces a composite `PAYBILL::<paybill>::<account>` key used exclusively for `PAY_BILL` payments: new helpers `RecipientMappingRepository.composePaybillKey(paybillName, account)`, `savePaybillMapping(paybillName, account, categoryId, displayName)`, and `getCategoryForPaybill(paybillName, account)` (silent no-op / null when either half is blank, so a broken parse never poisons the mapping table). All live-SMS and bulk-import auto-cat lookup sites now branch on `PaymentType.PAY_BILL` and use the composite lookup, skipping the recipient-name-only fallback that caused the misfires: [SmsReceiver.applyAutoCategorization](../android/app/src/main/java/com/pesatrack/services/SmsReceiver.kt), [SmsImportService.applyCategorization](../android/app/src/main/java/com/pesatrack/services/SmsImportService.kt), [StatementImportService.autoCategorize](../android/app/src/main/java/com/pesatrack/services/StatementImportService.kt). All mapping-save sites now branch the same way and persist the composite key for Paybill expenses instead of the two generic recipientName/recipient saves: [BatchCategorizeViewModel](../android/app/src/main/java/com/pesatrack/presentation/screens/batch_categorize/BatchCategorizeViewModel.kt) (single-expense `applyCategoryToExpense`; group-level `applyCategory`, `applyAutoSuggestion`, `applyAllAutoSuggestions`, `applyBulkCategory` — each Paybill group calls new `savePaybillMappingsForGroup(group, categoryId)` which reads the group's uncategorized expenses and saves one composite per distinct account so a group with multiple accounts under the same paybill still learns each account correctly), [ManualEntryViewModel.saveExpense](../android/app/src/main/java/com/pesatrack/presentation/screens/manual_entry/ManualEntryViewModel.kt), [ExcelImportService.importExcel](../android/app/src/main/java/com/pesatrack/services/ExcelImportService.kt). Single-merchant paybills (KPLC, Nairobi Water, WiFi providers, etc.) still work: the user's own meter / account number stays constant, so the composite key stays stable. Existing pre-fix paybill-name-only mappings remain in the DB but become dormant for Paybill lookups (no destructive migration); the next categorization of any paybill account rebuilds the mapping under the correct composite key. Files: [RecipientMappingRepository.kt](../android/app/src/main/java/com/pesatrack/data/repository/RecipientMappingRepository.kt), [SmsReceiver.kt](../android/app/src/main/java/com/pesatrack/services/SmsReceiver.kt), [SmsImportService.kt](../android/app/src/main/java/com/pesatrack/services/SmsImportService.kt), [StatementImportService.kt](../android/app/src/main/java/com/pesatrack/services/StatementImportService.kt), [BatchCategorizeViewModel.kt](../android/app/src/main/java/com/pesatrack/presentation/screens/batch_categorize/BatchCategorizeViewModel.kt), [ManualEntryViewModel.kt](../android/app/src/main/java/com/pesatrack/presentation/screens/manual_entry/ManualEntryViewModel.kt), [ExcelImportService.kt](../android/app/src/main/java/com/pesatrack/services/ExcelImportService.kt).

- **Notification when a new expense is auto-categorized as Miscellaneous** — When live-SMS auto-categorization falls back to Miscellaneous (`KeywordRulesEngine.MISCELLANEOUS_CATEGORY_ID = 1201L`, primarily the `SEND_MONEY` fallback for unknown recipients, but also any learned recipient mapping that resolves to Misc), `SmsReceiver.handleExpenseResult` and `handleCardApprovalUpdate` now post a low-priority notification via new `NotificationHelper.showMiscAutoCategorizedNotification(...)` after the expense saves. Title "Categorized as Miscellaneous: KES X"; body "To {recipient} — Tap to reclassify"; tap deep-links to the same `Categorize` screen the "New Expense" prompt uses (`navigate_to=categorize` + `expense_id`), so the user can pick a better category with one tap. New notification channel `pesatrack_misc_autocategorized` (`IMPORTANCE_LOW`, "Auto-categorized as Miscellaneous") — separate channel so users can silence just this nudge in system settings without touching the categorize / income / budget / weekly-review channels. Extracted the previous magic-number `1201L` in `KeywordRulesEngine` into a public `MISCELLANEOUS_CATEGORY_ID` companion constant so callers reference a single source of truth. Files: [KeywordRulesEngine.kt](../android/app/src/main/java/com/pesatrack/services/KeywordRulesEngine.kt), [NotificationHelper.kt](../android/app/src/main/java/com/pesatrack/services/NotificationHelper.kt), [SmsReceiver.kt](../android/app/src/main/java/com/pesatrack/services/SmsReceiver.kt).

- **Home notification-permission banner** — Mirrors the existing SMS permission banner: shown on the Home screen when the app can't post notifications, so users understand why they aren't getting categorization prompts, weekly reviews, recurring-expense reminders, or income-received alerts. Rendered as a `secondaryContainer` Card with `Icons.Filled.Notifications`, heading "Turn on notifications?", body "Get gentle reminders to categorize new transactions and weekly review alerts. You can change this any time.", primary "Enable" button + secondary "Not now", and a Close icon that opens a dropdown with "Not now" (session) and "Don't ask again" (permanent) matching the SMS banner UX exactly. Permission check uses `NotificationManagerCompat.from(context).areNotificationsEnabled()` so it correctly reflects both the Android 13+ runtime `POST_NOTIFICATIONS` grant and the pre-13 system notification toggle, re-evaluated on `Lifecycle.State.RESUMED` (so returning from App Settings updates the banner). Enable action requests `POST_NOTIFICATIONS` via `ActivityResultContracts.RequestPermission` on API 33+, and opens `Settings.ACTION_APP_NOTIFICATION_SETTINGS` (fallback: `ACTION_APPLICATION_DETAILS_SETTINGS`) on older versions where there's no runtime prompt. New DataStore key `notification_banner_dismissed` + `AppPreferences.notificationBannerDismissed` / `isNotificationBannerDismissed()` / `dismissNotificationBanner()` (parallel to the SMS trio). New `HomeUiState.showNotificationPermissionBanner`; `HomeViewModel` gained `loadNotificationBannerState()`, `updateNotificationPermissionStatus(hasPermission)`, `dismissNotificationBannerSession()`, `dismissNotificationBannerPermanently()`. Files: [AppPreferences.kt](../android/app/src/main/java/com/pesatrack/data/local/preferences/AppPreferences.kt), [HomeUiState.kt](../android/app/src/main/java/com/pesatrack/presentation/screens/home/HomeUiState.kt), [HomeViewModel.kt](../android/app/src/main/java/com/pesatrack/presentation/screens/home/HomeViewModel.kt), [HomeScreen.kt](../android/app/src/main/java/com/pesatrack/presentation/screens/home/HomeScreen.kt).

- **Budget → "Use detected" income shortcut is now visibly a button (UX fix)** — On `IncomeAllocationCard`, the shortcut that pre-fills the manual income override with the detected SMS income was rendered as a plain `TextButton` in `labelSmall` next to the "Detected this month: KES X" sub-line. It blended into the surrounding secondary text (the parent Card itself is `.clickable { onSetIncome() }`), and multiple users didn't realise it was tappable. Replaced with a `FilledTonalButton` at `min-height 32.dp`, `contentPadding = (12.dp, 6.dp)`, leading `Icons.Filled.Download` icon (16.dp), label "Use this" in `labelMedium` / `SemiBold`. The tonal container color gives it a clearly interactive affordance against the card surface without competing with the primary income figure below. Detection trigger unchanged (monthly period + detected > 0 + (no override or `|override − detected| > 1`)); handler still calls `BudgetViewModel.useDetectedIncome()`. File: [BudgetScreen.kt](../android/app/src/main/java/com/pesatrack/presentation/screens/budget/BudgetScreen.kt).

- **Sample data regenerator rewritten to cover every current surface** — The dev-only "Populate Sample Data" flow in Settings had drifted badly behind feature growth. Previous version inserted ~14 expenses across 2 months, all `source = "MANUAL"`, all pre-categorized, with only 4 group-level monthly budgets and a `MonthlyIncomeBudgetEntity` for 2 months — no `IncomeTransactionEntity` rows at all. As a result Weekly / Monthly / Quarterly / Year-in-Review reports rendered nearly empty, YoY analytics had no prior-year data, `RecurringExpenseService` never detected a pattern (no 3+ occurrences at regular cadence), `CategorizeIncomeScreen` and savings-rate / per-source breakdowns had nothing to show, the Home uncategorized alert + `BatchCategorizeScreen` had no queue, transaction-cost surfacing (cat 606) was invisible, source filters looked broken, and `isExcluded` pass-through handling wasn't exercised. `clearAllData()` also silently leaked `income_transactions` between demo runs. Rewrote [SampleDataService.kt](../android/app/src/main/java/com/pesatrack/services/SampleDataService.kt) to generate 14 months of deterministic (seeded `Random`) data: monthly recurring rent / KPLC / water / WiFi / Netflix / SACCO / MMF / tithe + weekly-ish groceries / eating out / delivery / Uber / Bolt / fuel (so `RecurringExpenseService` reliably detects patterns); ~70% of SMS-style rows get a paired category-606 transaction-cost row via `withTxnCost(...)`; expense `source` is randomly picked across `SMS_PARSED` / `SMS_BANK` / `EXCEL_IMPORT` / `STATEMENT_IMPORT` / `MANUAL`; 4 recent expenses left `isCategorized = false` and one row marked `isExcluded = true` to demo the Home alert + BatchCategorize + pass-through flow; withdrawals intentionally left uncategorized to match real-world SMS. `IncomeTransactionDao` is now injected and populated with 14 salary rows (`IncomeSource.SALARY`), quarterly bonuses, occasional business/freelance credits (`BUSINESS`), M-Shwari self-transfers (`TRANSFER_IN`, `isInflow = false`), and 3 recent `UNCATEGORIZED` inflows so `CategorizeIncomeScreen` has content. `MonthlyIncomeBudgetEntity` overrides written for the last 3 months to drive the effective-income reconciliation UI. Budgets extended from 4 to 8 covering group + sub-category + `WEEKLY` + `YEARLY` periods (Food, Transport, Home, Entertainment, Investment groups; Eating-Out sub; Uber weekly; Faith yearly). `clearAllData()` now also clears `income_transactions`. `AppModule.provideSampleDataService` updated to inject `IncomeTransactionDao`. Files: [SampleDataService.kt](../android/app/src/main/java/com/pesatrack/services/SampleDataService.kt), [AppModule.kt](../android/app/src/main/java/com/pesatrack/di/AppModule.kt).

- **Analytics → Charts → Monthly now honors `monthStartDay`** — The Monthly tab was still slicing by calendar month while every other "month" surface (Budgets, Income, Home snapshots) had already moved to the user's configured period start. With `monthStartDay = 25`, opening Analytics on May 1 still showed "May 2026" calendar data even though the active budget period was "Apr 25 – May 24". Migrated every load path on the tab: `init` anchors `selectedYear/selectedMonth` on `MonthPeriod.currentRange(monthStartDay)` (period named by its start year/month); `previousMonth` / `nextMonth` / `canGoNext` use the same anchor so the picker steps period-by-period; `loadMonthData` computes the active range via `MonthPeriod.rangeForPeriodStart` + `labelForRange` (header label switches to "Mar 25 – Apr 24, 2026" when offset), pulls the previous-period comparison the same way, computes `daysForAvg` from `(now − start) / 1 day` clamped to the period length (so the per-day average no longer divides by calendar `daysInMonth`), and zero-fills daily-spending bars across the full period not the calendar month; `loadMonthlyTrend` and `loadCategoryTrends` iterate **6 periods backwards** instead of 6 calendar months (keys remain `"yyyy-MM"` formatted from each period's start so the `MonthlyTrendChart` axis-label code is unchanged); recipient-search MONTHLY branch uses period bounds. New range-based wrappers on `ExpenseRepository` (`getCategoryTotalsInRange`, `getTopSpendersInRange`, `getPaymentTypeBreakdownInRange`, `searchRecipientSpendingInRange`) delegate to the existing DAO `*ForMonth` queries (which already accept `(startMs, endMs)`) so no new DAO work was needed. Removed now-unused helpers `buildMonthKeys` / `fillMissingMonths` / `formatMonthLabel` and the stale `private val calendar` field. Intentionally untouched (out of scope, calendar-month semantics are correct for those): Insights tab cards (pace / quiet leaks / uncategorized %), Yearly tab, Weekly snapshot, Home trend chart, BudgetBurnDown (already period-aware). Files: [AnalyticsViewModel.kt](../android/app/src/main/java/com/pesatrack/presentation/screens/analytics/AnalyticsViewModel.kt), [ExpenseRepository.kt](../android/app/src/main/java/com/pesatrack/data/repository/ExpenseRepository.kt), [MonthPeriod.kt](../android/app/src/main/java/com/pesatrack/utils/MonthPeriod.kt).

- **CSV export now includes income transactions** — Settings → Export Data previously emitted only expenses, so users moving to another tool or running their own spreadsheet had to remember to export income separately (and there was no UI for that). The exporter now merges expenses + income into a single CSV ordered by `timestamp DESC`, with a new leading `Type` column (`Expense` / `Income`); the rest of the header (`Date,Amount (KES),Recipient,Category,Group,Payment Type,Transaction ID,Source,Notes,Excluded`) is unchanged so existing pivots/imports keep working. Income rows are mapped as: `Recipient = sender`, `Category = IncomeSource.fromName(source).displayName`, `Group = "Income"`, `Payment Type` empty, `Source = parserSource`, `Notes = note`. The exporter returns `null` only when **both** lists are empty (was: empty expense list); empty-state toast updated to "No transactions to export"; Settings row subtitle updated to "Export all expenses and income as CSV"; share-intent subject is now "PesaTrack Transaction Export". New `IncomeTransactionDao.getAllIncomeForExport()` query (parallel to the existing `getAllExpensesForExport()`); `DataManagementService` constructor takes an `IncomeTransactionDao` (4th param). Files: [DataManagementService.kt](../android/app/src/main/java/com/pesatrack/services/DataManagementService.kt), [IncomeTransactionDao.kt](../android/app/src/main/java/com/pesatrack/data/local/database/dao/IncomeTransactionDao.kt), [SettingsViewModel.kt](../android/app/src/main/java/com/pesatrack/presentation/screens/settings/SettingsViewModel.kt), [SettingsScreen.kt](../android/app/src/main/java/com/pesatrack/presentation/screens/settings/SettingsScreen.kt).

- **Income + savings rate honor monthStartDay** — Aligned the "this month" period for income/spend snapshots with the user's existing `monthStartDay` budget preference, so a salary-cycle user (e.g. paid on the 25th) gets a coherent picture instead of mixing offset budgets with calendar-month income. New shared helper [`MonthPeriod.kt`](../android/app/src/main/java/com/pesatrack/utils/MonthPeriod.kt) centralises offset-aware bounds (`currentRange`, `rangeForPeriodStart`), override-key derivation (`currentKey`, `keyForPeriodStart` — `"yyyy-MM"` when `startDay = 1`, `"yyyy-MM-dd"` when offset, matching the convention `BudgetRepository.getPeriodKey` already writes), and labelling (`labelForRange` — "March 2026" vs "Mar 25 – Apr 24, 2026"). `IncomeRepository` now injects `AppPreferences`, exposes a cached `monthStartDay` mirror of the BudgetRepository pattern (`refreshMonthStartDay()` + `monthStartDay` getter), plus three new offset-aware entry points — `currentMonthBounds()`, `currentMonthKey()`, `effectiveIncomeForCurrentMonth()`, and `effectiveIncomeForMonth(year, month1Based)`. Migrated surfaces: `HomeViewModel.loadIncomeData` (received-this-month + savings rate), `AnalyticsViewModel.loadSavingsRateCard` (3-month rolling iterates by budget period, not calendar month) and `loadIncomeVsSpendChart` (12-month overlay buckets match Budget/Income periods), and `IncomeViewModel` MONTH tab (bounds + label). Also fixed a latent pre-existing bug: `IncomeRepository.monthBoundsFor` only parsed `"yyyy-MM"` and threw `IllegalArgumentException` whenever `BudgetViewModel.loadIncome` passed an offset key like `"2026-03-25"` (silently swallowed by the surrounding `try/catch`, hiding the manual override from the budget income card); it now accepts both formats. Trend charts on Home/Analytics still use calendar months — those are time-series aggregates whose labels (`MMMM yyyy`) track the calendar; only the per-period income/spend snapshots and Income screen MONTH tab were changed. New `ExpenseRepository.getSpendingInRange(startMs, endMs)` thin wrapper over the existing DAO query. Files: [MonthPeriod.kt](../android/app/src/main/java/com/pesatrack/utils/MonthPeriod.kt), [IncomeRepository.kt](../android/app/src/main/java/com/pesatrack/data/repository/IncomeRepository.kt), [ExpenseRepository.kt](../android/app/src/main/java/com/pesatrack/data/repository/ExpenseRepository.kt), [HomeViewModel.kt](../android/app/src/main/java/com/pesatrack/presentation/screens/home/HomeViewModel.kt), [AnalyticsViewModel.kt](../android/app/src/main/java/com/pesatrack/presentation/screens/analytics/AnalyticsViewModel.kt), [IncomeViewModel.kt](../android/app/src/main/java/com/pesatrack/presentation/screens/income/IncomeViewModel.kt).

- **Bank → M-PESA self-transfer auto-exclusion + Income screen scroll preservation (bug fix)** — Two related polish fixes shipped together. (1) When the user moves money from their NCBA bank account to M-PESA via the bank app, the SMS pair is two halves of the same self-transfer (`MPESA transfer of KES … has been processed` on the NCBA side, already skipped by `NcbaBankParser`; `You have received Ksh… from NCBA BANK on …` on the M-PESA side) — and the M-PESA half was being saved as `UNCATEGORIZED` income, inflating monthly income totals and triggering a stray "Income received" notification. Fix: `MpesaSmsParser.tryParseIncome` now keeps a `bankSelfTransferSenders` list (`"NCBA BANK"`, `"NCBA"`) and, when the parsed `sender` matches any entry (`equals` / `contains`, case-insensitive), rewrites the row to `source = TRANSFER_IN` and `isExcluded = true` before persisting. Side-effects: (a) the notification stays silent because `SmsReceiver.handleIncomeResult` only fires the `pesatrack_income_received` channel for `UNCATEGORIZED` sources; (b) the row is filtered out of every total by existing `IncomeRepository.sumForRange` / `sourceBreakdown` predicates; (c) the row still appears on the Income screen (dimmed, struck-through) so the user can audit / restore it via long-press. New unit test `MpesaSmsParserIncomeTest."NCBA bank to MPESA self-transfer is excluded TRANSFER_IN"`. Extend the list as new banks appear in the wild. (2) `IncomeScreen` previously unmounted the entire `LazyColumn` whenever `uiState.isLoading == true` (replacing the list with a centered `CircularProgressIndicator`), so the ON_RESUME `viewModel.refresh()` call kicked the user back to the top of the list every time they returned from `CategorizeIncomeScreen`. Fix: gate the spinner branch on `isInitialLoad = uiState.isLoading && uiState.transactions.isEmpty() && uiState.totalInflow == 0.0` — refreshes with existing data no longer rebuild the list, preserving scroll position. Files: [MpesaSmsParser.kt](../android/app/src/main/java/com/pesatrack/utils/parsers/MpesaSmsParser.kt), [IncomeScreen.kt](../android/app/src/main/java/com/pesatrack/presentation/screens/income/IncomeScreen.kt), [MpesaSmsParserIncomeTest.kt](../android/app/src/test/java/com/pesatrack/utils/parsers/MpesaSmsParserIncomeTest.kt).

- **Inline category creation from the picker** — Users can now create categories directly while categorizing, without leaving the picker for Settings → Manage Categories. `GroupedCategoryPicker` accepts an optional `onCreateCategory(name, icon, color, parentId, onCreated)` callback; when supplied, every expanded group renders an "Add sub-category to {group}" entry at the end of its children list, and the very bottom of the picker shows an "Add new category group" entry. Tapping either opens the existing `CategoryFormDialog` (name + icon + color), and once the insert completes the picker auto-selects the new sub-category (immediately dismissing) or auto-expands the new group so the user can chain a sub-category create. The form dialog and its preset icon/color grids were extracted from `CategoryManagementScreen.kt` into a shared `presentation/components/CategoryFormDialog.kt` so both surfaces stay consistent. Wired through `CategorizeViewModel` (single-expense picker), `BatchCategorizeViewModel` (quick / individual / bulk pickers — 3 sites), and `ManualEntryViewModel`; each delegates to `CategoryRepository.addCategory(...)` / `addCategoryGroup(...)`. Picker reactivity comes for free because all three ViewModels already collect `categoryRepository.getCategoryGroups()` as a `Flow`. Files: [GroupedCategoryPicker.kt](../android/app/src/main/java/com/pesatrack/presentation/components/GroupedCategoryPicker.kt), [CategoryFormDialog.kt](../android/app/src/main/java/com/pesatrack/presentation/components/CategoryFormDialog.kt), [CategoryManagementScreen.kt](../android/app/src/main/java/com/pesatrack/presentation/screens/category_management/CategoryManagementScreen.kt), [CategorizeViewModel.kt](../android/app/src/main/java/com/pesatrack/presentation/screens/categorize/CategorizeViewModel.kt), [CategorizeScreen.kt](../android/app/src/main/java/com/pesatrack/presentation/screens/categorize/CategorizeScreen.kt), [BatchCategorizeViewModel.kt](../android/app/src/main/java/com/pesatrack/presentation/screens/batch_categorize/BatchCategorizeViewModel.kt), [BatchCategorizeScreen.kt](../android/app/src/main/java/com/pesatrack/presentation/screens/batch_categorize/BatchCategorizeScreen.kt), [ManualEntryViewModel.kt](../android/app/src/main/java/com/pesatrack/presentation/screens/manual_entry/ManualEntryViewModel.kt), [ManualEntryScreen.kt](../android/app/src/main/java/com/pesatrack/presentation/screens/manual_entry/ManualEntryScreen.kt).

- **Live income SMS now triggers a "Income received" notification (bug fix)** — Root cause: `SmsReceiver.onReceive` gated the M-PESA branch on the legacy `SmsParser.isTransactionSms(body)` helper, whose keyword list (`sent to` / `paid to` / `withdrawn` / `of airtime` / `Fuliza` / `bought`) only matched expense SMS. Every live income SMS (`You have received…`, `Salary Payment from…`, `Funds received from…`, M-Shwari → M-PESA, Offnet B2C) was silently dropped before the parser ran, so no row was saved and the `pesatrack_income_received` notification never fired. The bulk SMS import path (`SmsImportService`) routed straight through `SmsParserRegistry`, which is why imported income still appeared in the list but live notifications never did. Fix: in `SmsReceiver`, replaced `&& SmsParser.isTransactionSms(body)` with `&& body.contains("Confirmed", ignoreCase = true)` — the universal M-PESA transaction marker `MpesaSmsParser.canHandle` / `parseSms` already use internally. The parser strategy now decides whether each SMS is `ExpenseResult` / `IncomeResult` / `NotARelevantMessage`. Files: [SmsReceiver.kt](../android/app/src/main/java/com/pesatrack/services/SmsReceiver.kt).

- **Income "Not income" affordance — chip + long-press** — Surfaces the existing row-level `IncomeTransactionEntity.isExcluded` flag (already filtered by `IncomeRepository.sumForRange` / `sourceBreakdown`) so users can mark a one-off transfer, refund, or rounding-noise credit as something that should not count toward income totals, savings rate, or analytics. **CategorizeIncomeScreen** — the dedicated "Exclude from analytics" `Switch` row has been replaced by a "Not income" `FilterChip` rendered inline with the `IncomeSource` chips; selecting it disables the source chips (visually + via `enabled = false`) and shows a brief explainer "This won't count toward income totals, savings rate, or analytics. You can undo with a long-press on the row." Removed unused imports (`Switch`, `HorizontalDivider`, `wrapContentWidth`) and the private `Row` wrapper. **IncomeScreen** — `IncomeRow` now uses `combinedClickable(onClick, onLongClick)` (`@OptIn(ExperimentalFoundationApi::class)`); long-press toggles: if the row is already excluded it calls `IncomeViewModel.restoreIncome(id)`, otherwise `markAsNotIncome(id)`. Excluded rows render at `alpha = 0.45`, with the amount struck through via `TextDecoration.LineThrough` (outline-colored dot) and the subtitle reading `"Not income" · sender`. **IncomeViewModel** gained `markAsNotIncome(id)` / `restoreIncome(id)` — both call `IncomeRepository.setExcluded(id, …)` and reload. No DB migration (column already shipped in v17). Files: [CategorizeIncomeScreen.kt](../android/app/src/main/java/com/pesatrack/presentation/screens/categorize_income/CategorizeIncomeScreen.kt), [IncomeScreen.kt](../android/app/src/main/java/com/pesatrack/presentation/screens/income/IncomeScreen.kt), [IncomeViewModel.kt](../android/app/src/main/java/com/pesatrack/presentation/screens/income/IncomeViewModel.kt).

- **Income tracking Phase 4 — Analytics polish** — Per [plans/income-tracking-plan.md](../plans/income-tracking-plan.md) §9. **Savings Rate insight card** on Analytics → Insights tab (`SavingsRateInsightCard`, primary-container surface, shown only when current month has detected/effective income > 0): displays this-month savings rate, 3-month rolling rate, current-month income vs spend, and a tap-to-expand "Assumptions" block citing the income source label (Detected from SMS / Your override / Detected — higher than your override). **Income vs Spend 12-month overlay chart** on Analytics → Monthly Charts (`IncomeVsSpendChart`, two-line Vico chart with green income series + theme-error spend series + tiny legend dots, only published when at least one of the last 12 months has income > 0; income series uses `incomeRepository.sumForRange(start, end, includeTransfers = false)`). **Investment-illustration attribution** in Monthly Review now appends the income source to the disclaimer: "Based on detected income of KES X." (DETECTED) / "Based on the income you set (KES X)." (MANUAL_OVERRIDE or DETECTED_BELOW_OVERRIDE) / no attribution when no income basis. New UiState additions: `AnalyticsUiState.SavingsRateData`, `AnalyticsUiState.IncomeSpendPoint`, `savingsRate`, `showSavingsRateCard`, `incomeVsSpend`. `AnalyticsViewModel` injects `IncomeRepository` and loads both surfaces from existing `effectiveMonthlyIncome` / `sumForRange` / `getTotalForMonth` queries (no new DAO work). Files: [AnalyticsUiState.kt](../android/app/src/main/java/com/pesatrack/presentation/screens/analytics/AnalyticsUiState.kt), [AnalyticsViewModel.kt](../android/app/src/main/java/com/pesatrack/presentation/screens/analytics/AnalyticsViewModel.kt), [AnalyticsScreen.kt](../android/app/src/main/java/com/pesatrack/presentation/screens/analytics/AnalyticsScreen.kt), [MonthlyReviewGenerator.kt](../android/app/src/main/java/com/pesatrack/domain/insights/MonthlyReviewGenerator.kt).

- **Income tracking Phase 3 — Surfaces** — Per [plans/income-tracking-plan.md](../plans/income-tracking-plan.md) §6. **New Income screen** at `Screen.Income` route ("income"): Scaffold + segmented Month / Quarter / Year period picker, `IncomeHeaderCard` (period label + total + reconciliation chip + weighted `SourceBreakdownBar` + `FlowRow` of `SourceLegendChip`s with a per-`IncomeSource` color palette), `IncomeRow`s (color dot + amount + source/sender + date — tap → `Screen.CategorizeIncome`), `IncomeEmptyState`, `ManualIncomeEntryDialog` (amount / sender / source `FilterChips` filtered to non-`UNCATEGORIZED` / optional note) wired via an `ExtendedFloatingActionButton`; manual entries are persisted with `transactionId = "MANUAL-${ts}-${abs(hash)}"` and `parserSource = "MANUAL"`. **Home secondary income line** on `MonthlySummaryCard`: "KES X received · Y% saved" rendered with `alpha = 0.65` and `Modifier.clickable { onTapIncome() }` → navigates to the new Income screen; hidden when source is `NONE` or `MANUAL_OVERRIDE`. `HomeViewModel` now injects `IncomeRepository` and recomputes detected income + savings rate per-month emission. **Budget screen** `IncomeAllocationCard` extended: sub-line "Detected this month: KES X" + "Use detected" `TextButton` when monthly and (no override or |override − detected| > 1) → calls new `BudgetViewModel.useDetectedIncome()` which writes through to `BudgetRepository.setMonthlyIncome`; reconciliation chip footer below allocation ("Using detected income" / "Using your override" / "Using override — KES X higher than detected"); `SetIncomeDialog` clarifies "This is what the app should treat as your income for budgeting. Detected SMS income is shown for reference and is not changed by edits here." **Monthly Review** new `IncomeSourcesCard` between Headroom and Pace cards: title "Where your income came from", per-source rows sorted desc (name + KES + %), plus reconciliation chip footer; `MonthlyReviewSnapshot` gained `incomeBreakdown: List<IncomeSourceTotal>` and `effectiveIncomeSource: EffectiveIncomeSource?` (both default-valued for back-compat). `InsightsRepository.generateAndStoreMonthlyReview()` now sources `monthlyIncome` from `IncomeRepository.effectiveMonthlyIncome(monthIncomeKey).value` and breakdown from `IncomeRepository.sourceBreakdown(start, endExclusive)` instead of reading only the manual override. **About + privacy policy copy** updated to reflect that both incoming and outgoing SMS are read: AboutScreen trust bullet → "Reads both incoming and outgoing M-PESA and bank SMS — nothing leaves your phone"; `docs/privacy-policy.html` §1 + RECEIVE_SMS bullet aligned. Files: [IncomeScreen.kt](../android/app/src/main/java/com/pesatrack/presentation/screens/income/IncomeScreen.kt), [IncomeViewModel.kt](../android/app/src/main/java/com/pesatrack/presentation/screens/income/IncomeViewModel.kt), [IncomeUiState.kt](../android/app/src/main/java/com/pesatrack/presentation/screens/income/IncomeUiState.kt), [Screen.kt](../android/app/src/main/java/com/pesatrack/presentation/navigation/Screen.kt), [NavGraph.kt](../android/app/src/main/java/com/pesatrack/presentation/navigation/NavGraph.kt), [HomeUiState.kt](../android/app/src/main/java/com/pesatrack/presentation/screens/home/HomeUiState.kt), [HomeViewModel.kt](../android/app/src/main/java/com/pesatrack/presentation/screens/home/HomeViewModel.kt), [HomeScreen.kt](../android/app/src/main/java/com/pesatrack/presentation/screens/home/HomeScreen.kt), [BudgetUiState.kt](../android/app/src/main/java/com/pesatrack/presentation/screens/budget/BudgetUiState.kt), [BudgetViewModel.kt](../android/app/src/main/java/com/pesatrack/presentation/screens/budget/BudgetViewModel.kt), [BudgetScreen.kt](../android/app/src/main/java/com/pesatrack/presentation/screens/budget/BudgetScreen.kt), [MonthlyReviewSnapshot.kt](../android/app/src/main/java/com/pesatrack/domain/insights/MonthlyReviewSnapshot.kt), [MonthlyReviewGenerator.kt](../android/app/src/main/java/com/pesatrack/domain/insights/MonthlyReviewGenerator.kt), [MonthlyReviewScreen.kt](../android/app/src/main/java/com/pesatrack/presentation/screens/monthly_review/MonthlyReviewScreen.kt), [InsightsRepository.kt](../android/app/src/main/java/com/pesatrack/data/repository/InsightsRepository.kt), [AboutScreen.kt](../android/app/src/main/java/com/pesatrack/presentation/screens/about/AboutScreen.kt), [privacy-policy.html](../docs/privacy-policy.html).

- **Income tracking Phase 2 — SMS / statement income detection** (DB v17 → v18) — Per [plans/income-tracking-plan.md](../plans/income-tracking-plan.md) §5, the parser layer now emits income transactions in addition to expenses, end-to-end. New sealed `ParsedSms` result type (`ExpenseResult` / `IncomeResult` / `NotARelevantMessage`) replaces the old nullable `ParsedTransaction?` API; `SmsParserStrategy.parseSms(body, smsDate): ParsedSms` is the new entry point (legacy `SmsParser.parseTransaction(...)` kept as `@Deprecated` facade so callers can migrate). **MpesaSmsParser** added income detection (ordered most specific first to win over the generic "You have received"): salary (`SALARY`), business (`BUSINESS`), funds-received (`UNCATEGORIZED`), peer receive with name+phone capture (`UNCATEGORIZED`), M-Shwari → M-PESA transfers and agent deposits (`TRANSFER_IN`), and Offnet B2C (`UNCATEGORIZED`); reversal SMS still return `NotARelevantMessage`. **NcbaBankParser** added bank-credit income parsing (`creditPattern` + `creditAmountPattern` + best-effort `creditFromPattern` + `creditRefPattern` fallback to deterministic `NCBA-<hash>` id). **MpesaStatementParser** now also returns `incomeTransactions: List<IncomeTransaction>` in `StatementParseResult` (parserSource `STATEMENT_IMPORT`). **Receiver / import services** dispatch on the sealed type: `SmsReceiver` calls `IncomeRepository.insertIfNew(...)` and posts a low-priority "Income received" notification when the source is `UNCATEGORIZED`; `SmsImportService` and `StatementImportService` track `newIncomesImported` / `incomeDuplicatesSkipped` alongside expense counters and surface the totals on the import-result screen. **CategorizeIncomeScreen** (`Screen.CategorizeIncome` route `categorize_income/{incomeId}`, deep-linked from the income notification) shows the amount + sender + a `FlowRow` of `IncomeSource` chips; a "Remember this sender" checkbox writes a row to the new `income_sender_rules` table (sender PK, source, learnedAt). **`IncomeRepository.insertIfNew`** now applies any matching learned sender rule before persisting incoming `UNCATEGORIZED` income (auto-categorization without the user having to tap again). **Migration v17 → v18** adds `income_sender_rules` (non-destructive). New notification channel `pesatrack_income_received` (IMPORTANCE_LOW per AGENTS "nudge, don't nag" principle). New JVM tests `MpesaSmsParserIncomeTest` (8 cases — salary / business / peer / M-Shwari / agent deposit / reversal / random / expense-still-works) and `NcbaBankParserIncomeTest` (4 cases — credit with ref / credit without ref fallback id / credit without amount / detailed Till transfer still parses as `ExpenseResult`). Files: [ParsedSms.kt](../android/app/src/main/java/com/pesatrack/utils/parsers/ParsedSms.kt), [SmsParserStrategy.kt](../android/app/src/main/java/com/pesatrack/utils/parsers/SmsParserStrategy.kt), [SmsParserRegistry.kt](../android/app/src/main/java/com/pesatrack/utils/parsers/SmsParserRegistry.kt), [MpesaSmsParser.kt](../android/app/src/main/java/com/pesatrack/utils/parsers/MpesaSmsParser.kt), [MpesaStatementParser.kt](../android/app/src/main/java/com/pesatrack/utils/parsers/MpesaStatementParser.kt), [NcbaBankParser.kt](../android/app/src/main/java/com/pesatrack/utils/parsers/NcbaBankParser.kt), [SmsParser.kt](../android/app/src/main/java/com/pesatrack/utils/SmsParser.kt), [SmsReceiver.kt](../android/app/src/main/java/com/pesatrack/services/SmsReceiver.kt), [SmsImportService.kt](../android/app/src/main/java/com/pesatrack/services/SmsImportService.kt), [StatementImportService.kt](../android/app/src/main/java/com/pesatrack/services/StatementImportService.kt), [StatementImportScreen.kt](../android/app/src/main/java/com/pesatrack/presentation/screens/statement_import/StatementImportScreen.kt), [NotificationHelper.kt](../android/app/src/main/java/com/pesatrack/services/NotificationHelper.kt), [Screen.kt](../android/app/src/main/java/com/pesatrack/presentation/navigation/Screen.kt), [NavGraph.kt](../android/app/src/main/java/com/pesatrack/presentation/navigation/NavGraph.kt), [MainActivity.kt](../android/app/src/main/java/com/pesatrack/presentation/MainActivity.kt), [CategorizeIncomeScreen.kt](../android/app/src/main/java/com/pesatrack/presentation/screens/categorize_income/CategorizeIncomeScreen.kt), [CategorizeIncomeViewModel.kt](../android/app/src/main/java/com/pesatrack/presentation/screens/categorize_income/CategorizeIncomeViewModel.kt), [CategorizeIncomeUiState.kt](../android/app/src/main/java/com/pesatrack/presentation/screens/categorize_income/CategorizeIncomeUiState.kt), [IncomeSenderRuleEntity.kt](../android/app/src/main/java/com/pesatrack/data/local/database/entities/IncomeSenderRuleEntity.kt), [IncomeSenderRuleDao.kt](../android/app/src/main/java/com/pesatrack/data/local/database/dao/IncomeSenderRuleDao.kt), [PesaTrackDatabase.kt](../android/app/src/main/java/com/pesatrack/data/local/database/PesaTrackDatabase.kt), [AppModule.kt](../android/app/src/main/java/com/pesatrack/di/AppModule.kt), [IncomeRepository.kt](../android/app/src/main/java/com/pesatrack/data/repository/IncomeRepository.kt), [build.gradle.kts](../android/app/build.gradle.kts).

- **Feedback email body now actually pre-populates in Gmail** — Both the Stage 1D structured feedback prompt and the Stage 1E low-engagement dialog (and the AboutScreen "Contact & Feedback" link) build a rich email body via `UsageSummaryGenerator` (structured response or churn reason + on-device usage instrumentation), but the email drafts were opening with an empty body and missing subject. Root cause: `createFeedbackEmailIntent` in [HomeScreen.kt](../android/app/src/main/java/com/pesatrack/presentation/screens/home/HomeScreen.kt) (and the equivalent intent in [AboutScreen.kt](../android/app/src/main/java/com/pesatrack/presentation/screens/about/AboutScreen.kt)) used `Intent.ACTION_SENDTO` with a bare `mailto:address` URI and supplied subject/body via `EXTRA_SUBJECT`/`EXTRA_TEXT` — modern Gmail (and several other Android mail clients) ignore those extras when the mailto URI has no query string and only honor `subject=` / `body=` query parameters in the URI itself. Fix: build the URI as `mailto:joelmumo.jm@gmail.com?subject={Uri.encode(subject)}&body={Uri.encode(body)}` and keep the extras as a fallback for clients that still read them. Files: [HomeScreen.kt](../android/app/src/main/java/com/pesatrack/presentation/screens/home/HomeScreen.kt), [AboutScreen.kt](../android/app/src/main/java/com/pesatrack/presentation/screens/about/AboutScreen.kt).

- **Notification deep-links wired for Monthly / Quarterly / Year-in-Review** — `NotificationHelper` was already attaching `navigate_to` extras (`monthly_review`, `quarterly_review`, `year_in_review`) plus `report_snapshot_id` / `year` to those notifications, but `MainActivity.MainScreen`'s `when (deepLinkTarget)` only handled `categorize`, `budget`, and `weekly_review` — so tapping a Monthly/Quarterly/Yearly review notification just opened the app at the start destination. Added a `pendingYear` deep-link state, extracted the `year` extra in `handleDeepLinkIntent`, plumbed `deepLinkYear` into `MainScreen`, and added `monthly_review` / `quarterly_review` / `year_in_review` branches that navigate to `Screen.MonthlyReview.createRoute(snapshotId)`, `Screen.QuarterlyReview.createRoute(snapshotId)`, and `Screen.YearInReview.createRoute(year)` respectively. File: [`MainActivity.kt`](../android/app/src/main/java/com/pesatrack/presentation/MainActivity.kt).

- **Income tracking Phase 1 — data foundation** (DB v16 → v17) — Per [plans/income-tracking-plan.md](../plans/income-tracking-plan.md), laid the data layer for transaction-level income before any UI surfaces. Renamed `IncomeEntity`/`IncomeDao` → `MonthlyIncomeBudgetEntity`/`MonthlyIncomeBudgetDao` to clarify that the existing one-row-per-month table is the manual **override / expected income**, not a transaction log (table name `income` retained on disk). Added new `IncomeTransactionEntity` + `IncomeTransactionDao` (`income_transactions` table) with unique `transactionId` for free SMS-replay dedupe, indexes on `timestamp` + `source`, and `sumForRange` / `sumForRangeBySources` queries usable as savings-rate denominators. New domain types: `IncomeSource` enum (SALARY, BUSINESS, REFUND, INTEREST, FAMILY, TRANSFER_IN, OTHER, UNCATEGORIZED — each carrying an `isInflow` flag so self-transfers can be excluded from "% of income" math), `IncomeTransaction` model, `EffectiveIncome` + `EffectiveIncomeSource`. New `IncomeRepository` exposes `effectiveMonthlyIncome(yearMonth)` implementing the reconciliation rules from plan §6.4 (`NONE` / `MANUAL_OVERRIDE` / `DETECTED` / `DETECTED_BELOW_OVERRIDE`); reconciliation is exposed as a pure `reconcile()` function for table-driven JVM testing. Fixed a pre-existing bug in quarterly analytics: `InsightsRepository.generateAndStoreQuarterlyReview()` was using only the **first month of the quarter's** manual income as a proxy for the whole quarter (under-reporting whenever income varied or was set on later months) — now averages effective monthly income across the months in the quarter so far and hands the average to `QuarterlyReviewGenerator` (which still multiplies by 3 internally). Added `IncomeRepositoryReconcileTest` (9 JVM cases covering every row of §6.4 plus the ±10% boundary) and `IncomeTransactionDaoTest` (instrumented — dedupe, exclusion, range sums, source filters, toggle helpers). No user-visible UI change in this phase. Files: [PesaTrackDatabase.kt](../android/app/src/main/java/com/pesatrack/data/local/database/PesaTrackDatabase.kt), [AppModule.kt](../android/app/src/main/java/com/pesatrack/di/AppModule.kt), [BudgetRepository.kt](../android/app/src/main/java/com/pesatrack/data/repository/BudgetRepository.kt), [InsightsRepository.kt](../android/app/src/main/java/com/pesatrack/data/repository/InsightsRepository.kt), [SampleDataService.kt](../android/app/src/main/java/com/pesatrack/services/SampleDataService.kt), [IncomeRepository.kt](../android/app/src/main/java/com/pesatrack/data/repository/IncomeRepository.kt), [MonthlyIncomeBudgetEntity.kt](../android/app/src/main/java/com/pesatrack/data/local/database/entities/MonthlyIncomeBudgetEntity.kt), [MonthlyIncomeBudgetDao.kt](../android/app/src/main/java/com/pesatrack/data/local/database/dao/MonthlyIncomeBudgetDao.kt), [IncomeTransactionEntity.kt](../android/app/src/main/java/com/pesatrack/data/local/database/entities/IncomeTransactionEntity.kt), [IncomeTransactionDao.kt](../android/app/src/main/java/com/pesatrack/data/local/database/dao/IncomeTransactionDao.kt), [IncomeSource.kt](../android/app/src/main/java/com/pesatrack/domain/models/IncomeSource.kt), [IncomeTransaction.kt](../android/app/src/main/java/com/pesatrack/domain/models/IncomeTransaction.kt).

- **Home "By Category" card — top 5 most-recently-active categories** — Added a tabular "By Category" section on the Home screen, placed directly above "Recent Expenses". Mirrors the Charts → Monthly → By Category table from Analytics (reuses the same `CategoryBreakdownChart` composable) but limits the list to 5 categories and orders by `MAX(timestamp) DESC` for the current month so users see where money has been moving rather than which categories are largest. New DAO query `ExpenseDao.getRecentlyActiveCategoryTotalsForMonth(start, end, limit)` and repository wrapper `ExpenseRepository.getRecentlyActiveCategoryTotalsForMonth(year, month, limit)`. `HomeUiState.recentCategoryBreakdown` is loaded reactively in `HomeViewModel` whenever the month's expense set changes. "View All" link on the section header deep-links into Analytics with a new `?section={section}` argument on `Screen.Analytics` (`SECTION_BY_CATEGORY`); `AnalyticsScreen` switches to Charts → Monthly and `MonthlyTabContent` uses a hoisted `LazyListState` + `animateScrollToItem` to bring the By Category header into view. `BottomNavItem.ANALYTICS` switched to `Screen.Analytics.BASE_ROUTE` and bottom-nav selection comparison now ignores optional query args. Files: [`ExpenseDao.kt`](../android/app/src/main/java/com/pesatrack/data/local/database/dao/ExpenseDao.kt), [`ExpenseRepository.kt`](../android/app/src/main/java/com/pesatrack/data/repository/ExpenseRepository.kt), [`HomeUiState.kt`](../android/app/src/main/java/com/pesatrack/presentation/screens/home/HomeUiState.kt), [`HomeViewModel.kt`](../android/app/src/main/java/com/pesatrack/presentation/screens/home/HomeViewModel.kt), [`HomeScreen.kt`](../android/app/src/main/java/com/pesatrack/presentation/screens/home/HomeScreen.kt), [`Screen.kt`](../android/app/src/main/java/com/pesatrack/presentation/navigation/Screen.kt), [`NavGraph.kt`](../android/app/src/main/java/com/pesatrack/presentation/navigation/NavGraph.kt), [`MainActivity.kt`](../android/app/src/main/java/com/pesatrack/presentation/MainActivity.kt), [`AnalyticsScreen.kt`](../android/app/src/main/java/com/pesatrack/presentation/screens/analytics/AnalyticsScreen.kt).

- **Investment illustration — principal-source bug + honest lump-sum copy** — In Monthly Review the "What your savings could become" card was showing a principal equal to the month's *total spending minus fees* (e.g. KES ~353k) instead of the actual invested amount (e.g. KES 55k). Root cause: `InsightsRepository.toMonthlyDomain()` re-hydrates persisted `ReportSnapshotEntity` rows but the entity doesn't store the investment illustration, so it was fabricating one with `principalAmount = periodTotal − feesTotal` and `source = HEADROOM`. Fix: made `toMonthlyDomain` `suspend`, re-query the live investment total via `ExpenseDao.getInvestmentTotalInRange(periodStart, periodEnd)` plus stored income (falling back to `headroomAmount + periodTotal`), then call `MonthlyReviewGenerator.buildInvestmentIllustration(...)` — the same path used at generation. Also rewrote the illustration copy across Monthly/Quarterly/Year-in-Review screens to honestly reflect the **lump-sum** future-value math (was implying monthly contribution / "powerful habit / X per month"): HEADROOM heading "What your savings could become" → "Your unspent income this {month/quarter/year}"; bodies reworded to "If invested at 10% p.a. for 5 years it could grow to KES X"; NUDGE_TARGET dropped the misleading "20% of income" claim for users without income set; disclaimer updated to "Assumes a single deposit … left to grow at 10% annual return compounded monthly." Files: [InsightsRepository.kt](android/app/src/main/java/com/pesatrack/data/repository/InsightsRepository.kt), [MonthlyReviewGenerator.kt](android/app/src/main/java/com/pesatrack/domain/insights/MonthlyReviewGenerator.kt), [MonthlyReviewScreen.kt](android/app/src/main/java/com/pesatrack/presentation/screens/monthly_review/MonthlyReviewScreen.kt), [QuarterlyReviewScreen.kt](android/app/src/main/java/com/pesatrack/presentation/screens/quarterly_review/QuarterlyReviewScreen.kt), [YearInReviewScreen.kt](android/app/src/main/java/com/pesatrack/presentation/screens/year_in_review/YearInReviewScreen.kt).

- **Forecasting feature removed; budget remaining + chart polish** — Per product principle ("no projections without showing assumptions") and user feedback that the projections were faulty and that per-day suggestions were unhelpful for non-daily expenses, the entire budget forecasting subsystem was removed: deleted `ForecastService.kt`, `BudgetForecast.kt`, `ForecastProjectionChart` composable, `ForecastCard` on Home, forecast notifications throttle (`canSendForecastNotification` / `FORECAST_NOTIF_PREFIX` / `KEY_COUNT_FORECAST_VIEWS` / `incrementForecastViewsCount`), and all forecast call sites in `HomeViewModel`, `BudgetViewModel`, `AnalyticsViewModel`, `BudgetService`, `SmsReceiver`, `NotificationHelper`. Replaced per-day budget suggestion with a neutral "KES X remaining for N days" line on each `BudgetProgressCard` (new `BudgetRemaining` domain model computed from the active period range). Fixed the daily-spending column chart that previously rendered as a single bar by zero-filling missing days in `AnalyticsViewModel.loadMonthData` so every day in the selected month appears on the X axis. Added visible point markers (Vico `LineCartesianLayer.point` + pill `ShapeComponent`) to the monthly trend line and the 12-month year-over-year overlay so individual data points are readable. Rewrote `CategoryBreakdownChart` in Insights from horizontal bars to a tabular layout (Category / Amount / %) with a colored category dot, divider rows, and right-aligned numeric columns.

### Recent Features

- **Onboarding SMS permission de-ambush** — The auto-prompt added in v1.3.0 (commit `e3ed363`, 13 May 2026) was firing the system SMS dialog the instant the user landed on page 3, before they could read the context — Kenyan users (conditioned by predatory loan apps) reflexively denied it. Cross-referencing Play Console data, uninstall ratio rose from ~35% pre-auto-prompt to ~49% post-auto-prompt. Changes: (1) **removed the auto-launch entirely** — moved to the primer pattern (page 3 shows context + prominent "Grant SMS Permission" button that the user taps to initiate the system dialog); (2) page 3 "Next" button is relabeled "Skip — I'll add manually" (outlined style) when permission is not granted, so the alternative path is explicit rather than feeling like a dead end; (3) page 3 body rewritten to lead with reassurance ("nothing leaves your phone — no internet permission") and explicitly mention the manual-entry fallback; (4) page 4 fallback copy rewritten to be encouraging rather than scolding when SMS was skipped. No version bump yet. File: [`OnboardingScreen.kt`](../android/app/src/main/java/com/pesatrack/presentation/screens/onboarding/OnboardingScreen.kt).

- **Stage 1E tightened + modal — capture churn feedback in-session** — Reduced low-engagement prompt thresholds from `SMS_GRACE=24h` / `FIRST_VALUE_GRACE=72h` to `30min` / `15min` respectively (most uninstalls happen within the first 24h, so the 72h window never reached churners). Condition B no longer requires `hasSmsPermission` — applies whenever onboarding is complete and no first-value event has fired. Converted `LowEngagementFeedbackCard` (inline LazyColumn card) to `LowEngagementFeedbackDialog` (Material 3 `AlertDialog`) so the prompt is visible regardless of scroll position. Title rewritten to neutral framing ("Quick question — what's blocking you?"). All other Stage 1E behavior (reason persisted to `KEY_LOW_ENGAGEMENT_REASON`, email draft via `UsageSummaryGenerator`, `markLowEngagementPromptShown` one-shot guard) unchanged. Files: [`HomeViewModel.kt`](../android/app/src/main/java/com/pesatrack/presentation/screens/home/HomeViewModel.kt), [`HomeScreen.kt`](../android/app/src/main/java/com/pesatrack/presentation/screens/home/HomeScreen.kt).

- **Firebase Analytics reverted (never shipped)** — Firebase BOM, `firebase-analytics`, `google-services` plugin, `AnalyticsService.kt`, and `google-services.json` were added during exploration but reverted before release. Rationale: contradicts the published "no INTERNET permission / no analytics" trust posture committed in privacy policy, About screen, and Play Store Data Safety form. At ~50 installs the cost in differentiation outweighs the directional funnel data Firebase would provide vs. the already-shipped Stage 1 local instrumentation + Play Console retention/uninstall reports + direct user interviews. No INTERNET permission was ever added to `AndroidManifest.xml`; trust language remains accurate.

- **Insights & Reports v1.5 — Tier-Based Investment Illustration** — Replaced naive "what if all expenses invested" with tier-based approach: (C) actual investments from Savings & Investments category group 18, (A) headroom when income set, (B) 20% nudge target fallback. Added `InvestmentSource` enum (`ACTUAL_INVESTMENT`, `HEADROOM`, `NUDGE_TARGET`) and new fields (`source`, `currentPercent`, `nextTargetPercent`, `gapAmount`) to `InvestmentIllustration`. Shared `buildInvestmentIllustration()` helper in `MonthlyReviewGenerator` used by all three review generators. Progressive targets: <20%→20%, 20-29%→30%, 30-49%→50%, ≥50%→celebrate. Horizon changed from 12 to 60 months (5 years). Added `getInvestmentTotalInRange()` DAO query. Updated UI cards in Monthly/Quarterly/Year-in-Review screens with tier-aware copy. All unit tests updated and passing.

- **Insights & Reports v1.4 — Year-in-Review + Share-as-Image** — `YearInReviewSnapshot` domain model (annual total, delta, top 5, biggest mover, fees, quiet leaks, savings story, investment illustration, goals progress). `YearInReviewGenerator` pure function. `InsightsRepository` yearly CRUD. Full screen (YearInReviewScreen + ViewModel + UiState) at `Screen.YearInReview`. `YearInReviewWorker` fires Dec 28 at 18:00, posts notification on `yearly_review` channel. `ReportRenderer.kt` generic share-as-image utility (captures composable as bitmap, shares via intent) — wired into YearInReviewScreen. Unit tested: `YearInReviewGeneratorTest.kt`.

- **Insights & Reports v1.3 — Quarterly Review + Budget Burn-Down** — `QuarterlyReviewSnapshot` domain model (period total, delta, top 5, biggest mover, fees, savings momentum, investment illustration). `QuarterlyReviewGenerator` pure function (8 unit tests). `InsightsRepository` quarterly CRUD. Full screen (QuarterlyReviewScreen + ViewModel + UiState) at `Screen.QuarterlyReview`. `QuarterlyReviewWorker` fires 1st of Apr/Jul/Oct/Jan at 09:00, posts notification on `quarterly_review` channel. Budget Burn-Down Card in Insights tab (categories exhausting ≥3 days early) with `budget_burndown` notification channel.

- **Insights & Reports v1.2 — Insights Section + Insight Cards** — Restructured Analytics screen with "Insights" | "Charts" tab toggle (Insights default). Existing chart content moved under "Charts" tab. Insights tab is a vertical feed of cards: Weekly Review summary (→ WeeklyReview), Monthly Review summary (→ MonthlyReview), Pace Card (shown after 7th of month, projected month-end vs last month), Quiet Leak Card (categories with ≥8 txns and avg ≤ KES 300), Categorization Nudge Card (>15% uncategorized). Added `InsightsTab` enum, `PaceCardData`, `QuietLeakData` to AnalyticsUiState. AnalyticsViewModel computes pace, quiet leaks, uncategorized %.

- **Insights & Reports v1.0 — Weekly Review** (DB v15 → v16) — New `report_snapshots` table persists weekly spending reviews (with previous-period totals, Top 5 categories incl. "others" rollup, biggest-change category, fees-paid surfaced separately, optional headroom against monthly income). Pure-function `WeeklyReviewGenerator` (in `domain/insights/`) shapes a DAO breakdown into a `WeeklyReviewSnapshot` and is fully unit-tested. `WeeklyReviewWorker` (`@HiltWorker`, 7-day periodic, initial delay aligned to next Thursday 18:00 local) runs `InsightsRepository.generateAndStoreWeeklyReview()` and posts a notification on the new `pesatrack_weekly_review` channel (IMPORTANCE_DEFAULT). Notification deep-links to `Screen.WeeklyReview` carrying the snapshot id so the screen shows the exact report it advertised. Settings exposes a "Reports & Insights" section with toggle (`weekly_review_enabled` DataStore key). Honors AGENTS principles: neutral copy, fees not collapsed into discretionary spend, `limitedData=true` suppresses honest-only-when-comparable percentage delta.

### Completed Bug Fixes

1. **`PaymentType.fromString()`** — Was only matching display names ("Send Money") but DB stores enum names ("SEND_MONEY"). Fixed to try `valueOf()` first, then fall back to display name matching.
2. **Seed Category** — Moved from Shopping (id=506) to Faith & Giving (id=905) with DB migration 2→3.
3. **ExpenseCard Title** — Changed from showing phone number to: category name → recipient name → recipient (priority order). Notes deliberately excluded (can be lengthy).
4. **SMS Parser coverage** — Expanded from 3 types (Send Money, Buy Goods, Pay Bill) to 7 types (+ Withdraw, Airtime, M-PESA Card, Fuliza).
5. **Non-expense filtering** — Added skip logic for Receive Money, Deposit, and Reversal SMS (not expenses).
6. **Transaction cost tracking** — Auto-extracted from SMS and saved as separate expense under category 811 with `isCategorized = true`.
7. **Icons.AutoMirrored.Filled.Send** — Not available in compose icons version; fixed with `@Suppress("DEPRECATION") Icons.Filled.Send`.
8. **NCBA Paybill regex (Format B)** — Original regex expected a standalone paybill number (digits) before "account" (e.g. `"to CHURCH 87 account number Offering"`). Failed on NCBA SMS where the business name directly precedes "account number" with no separate paybill number (e.g. `"to Lipa na KCB account number 7575077"`). Fixed by splitting into `paybillPatternA` (with paybill number) and `paybillPatternB` (without), tried in order of specificity.
11. **NCBA new SMS format (Till + Paybill)** — NCBA changed their SMS format: Till messages no longer include the till number (was `"to 8933372 THE FIG AND OLIVE..."`, now `"to JAZA MUTHIGA BANK REF. ..."`), and Paybill messages no longer include the "account" keyword (was `"to NAME account number ACCT..."`, now `"to NAME BANK REF. ..."`). Added `tillPaymentPatternB` (name-only, no till number) and `paybillPatternC` (name-only, no account keyword) in `NcbaBankParser.kt`. Old patterns (A/B) kept as higher-priority fallbacks for backward compatibility. Renamed the original `tillPaymentPattern` to `tillPaymentPatternA` for clarity.
9. **Bottom Nav: Analytics → Home broken** — `restoreState = true` silently fails when the start destination (Home) has no previously-saved state. Navigation appears to do nothing — no exception thrown. Fixed by special-casing the start destination: `inclusive = true` + `saveState = false` + `restoreState = false` for Home tab; other tabs retain save/restore for state preservation.
10. **Onboarding "Import Now" → Home instead of Import screen** — The "Import Now" button on onboarding page 4 called `onImportHistory()` (a no-op) then `onComplete()`, which marked onboarding done and showed the Home screen. Fixed by using a `pendingImportNavigation` state flag: `onImportHistory` sets the flag, and after onboarding completes, `MainScreen` uses a `LaunchedEffect` to navigate to `Screen.ImportHistory`.
12. **NCBA duplicate SMS (card payments + M-PESA transfers)** — Generic debit SMS (`"Your account...has been debited..."`) was being parsed as a `CARD_PAYMENT` when it contained `Ref: FT...`, creating duplicates with the detailed M-PESA confirmation SMS. Fixed by skipping ALL generic debits in `NcbaBankParser`. For card payments, the card approval SMS (`"approved a transaction of USD...at MERCHANT..."`) now triggers a 2-minute SMS inbox lookup in `SmsReceiver` to find the paired debit SMS and extract the KES amount + bank ref. This eliminates duplicates while preserving both merchant name and local currency amount.

### Implemented Features

1. **Edit/Re-categorize expenses** — Removed `isCategorized` guard from ExpenseListScreen and HomeScreen; tapping any expense (categorized or not) now opens the CategorizeScreen. Title shows "Edit Category" for already-categorized expenses. The DAO/Repository/ViewModel already supported re-categorization — only the UI click handlers were blocking it.
2. **Notification System** — SMS-parsed expenses trigger notification with amount + recipient; tap opens categorize screen.
2. **Runtime Permissions** — MainActivity requests SMS and notification permissions on first launch. (Phone state permissions removed — were unused dead code from STK Push era.)
3. **Category-Aware Views** — Home screen and expense list show category name and colour alongside expenses.
4. **Phone Auto-Fill** — SIM number read via TelephonyManager, persisted in DataStore.
5. **Multi-part SMS** — SmsReceiver concatenates multi-part messages before parsing.
6. **Dual expense saving** — Each SMS can produce main expense + transaction cost (both saved).
7. **Duplicate detection** — transactionId checked before saving to avoid double entries.
8. **Excel Import** — Match Excel rows to uncategorized SMS expenses by amount±1 KES / date±1 day; apply categories via 55+ hardcoded mappings; import unmatched rows as standalone expenses; save recipient→category mappings for future auto-categorization; multi-file support via SAF file picker.
9. **M-PESA Statement PDF Import** — Parse password-protected M-PESA statement PDFs via Apache PDFBox; regex-based extraction of 13+ transaction types (Send Money, Pay Bill, Buy Goods, Airtime, Bundles, Withdrawal, M-Shwari, GlobalPay, etc.); transaction charges linked to parent via Receipt No.; deduplication against existing DB; auto-categorization via CategorizationService + recipient mappings; income/reversals skipped; progress callback during import; accessible from Import History screen.
9. **Investment % on Home Screen** — MonthlySummaryCard now shows a muted secondary line with the investment total and percentage (e.g. "📈 KES 50,000 (42%) invested") when any expenses are categorized under Investment & Savings (group 18). Uses a dedicated DAO query joining expenses with categories where `parentId = 18`. Displayed at `alpha = 0.5` to keep focus on the main expense total.
10. **Custom Categories & Auto-Rules (M8)** — Full category management UI: add/edit/delete custom groups and sub-categories with icon/color pickers; user-defined auto-categorization rules (EXACT/CONTAINS/STARTS_WITH match types) checked before built-in KeywordRulesEngine; DB migration v9→v10 adds `category_rules` table; Settings entry point "Manage Categories" with tab-based screen (Categories + Auto-Rules); default categories protected from deletion; categories with expenses cannot be deleted.
11. **Sub-category Budgets (M7 enhancement)** — Extended budgets from group-level to sub-category-level. Three tiers: Total Spending, Group (e.g. "Food & Dining ≤ 15K"), Sub-category (e.g. "Eating Out ≤ 5K"). DB migration v11→v12 renames `categoryGroupId`→`categoryId` and adds `isGroupBudget` column. Both group and sub-category budgets are tracked independently — an eating-out expense counts toward both "Food & Dining" and "Eating Out" budgets. Hierarchical category picker in add/edit dialog shows groups (bold) and indented sub-categories. Budget alerts fire for whichever threshold is reached first.
12. **Multi-Select Batch Categorize** — Long-press any recipient group on BatchCategorizeScreen to enter selection mode. Checkboxes appear on all cards; tap to select multiple groups across different recipients. "Select All" / "Deselect All" toggle in the top bar. Bottom "Categorize Selected (N)" button opens the category picker — applies the chosen category to ALL expenses from ALL selected groups in one action. Saves recipient→category mappings for future auto-categorization. Back press exits selection mode. Coexists with existing single-group, review, and auto-suggest modes.
13. **PIN Lock + Biometric Unlock** — App-level security with 4-digit PIN. PIN stored as SHA-256 + random salt in DataStore (never plaintext). Compose overlay in MainActivity blocks access when locked. Optional biometric (fingerprint/face) via `BiometricPrompt` — auto-launches on unlock screen, falls back to PIN. Lock triggers on cold start and after configurable background timeout (immediate/30s/1min/5min). Brute force protection: 5 wrong attempts → 30-second cooldown. Settings section: PIN enable/disable (verify current PIN first), change PIN (verify → enter → confirm), biometric toggle (only shown when device supports it), timeout picker. `ProcessLifecycleOwner` tracks background/foreground transitions via `AppLockLifecycleObserver`. No PIN recovery by design (clear app data to reset).
14. **First-Launch Onboarding Flow** — 4-page HorizontalPager shown once on first install (tracked via `KEY_ONBOARDING_COMPLETED` in DataStore). Pages: (1) Welcome — what the app does, (2) How It Works — SMS parsing explained, (3) SMS Permission — contextual permission grant with ✅ confirmation, (4) Import History — offer to import past M-PESA SMS. Dot indicators, Back/Next/Skip buttons, "Get Started" on final page. SMS permission requested in-context via `ActivityResultContracts.RequestMultiplePermissions()`. Notification permission requested on completion. Onboarding overlay in `MainActivity.AppEntryPoint()` — same pattern as PIN lock (full-screen, no bypass). Existing users see onboarding once (DataStore default is `false`).
15. **Searchable Budget Category Picker** — Replaced the `ExposedDropdownMenu` in `AddEditBudgetDialog` with a full searchable dialog (`BudgetCategoryPickerDialog`). Features: search bar filters groups and sub-categories by name, expandable/collapsible group headers, auto-expand groups when search matches children, "Total Spending" sentinel at top, "Group" button on each group header for group-level budgets, categories with existing budgets hidden, color-coded dots and checkmarks for selected items. Eliminates excessive scrolling through 100+ categories.
16. **Monthly Income & Budget Allocation** — Manual per-month income entry on the Budget screen. New `IncomeEntity` table (DB migration v12→v13) stores income per `yearMonth`. Budget screen shows an allocation summary card at the top: income vs sum of all category budgets, with progress bar and status (✅ unallocated buffer / ⚠️ over-allocated warning). Tap the card to set or update income. Total budgeted recalculates live when budgets are added/edited/deleted. No SMS parsing changes — income is manual only for now.
17. **Period-First Budget Redesign** — Redesigned Budget screen from a flat list to a period-organized flow. PeriodSelector at the top with Weekly/Monthly/Yearly tabs and ◀ ▶ navigation (e.g. "March 2026"). Income card always visible below the selector (works for any period type, not just monthly). Budgets are filtered by selected period type. "Add Budget" inherits the period from the selector (no period picker in the dialog). "Total Spending" option removed from the category picker. New DAO query `getActiveBudgetsByPeriod`. New repository methods: `getBudgetProgressListForPeriod`, `getTotalBudgetedForPeriod`, `getPeriodKey`, `getPeriodLabel`, `navigateCalendar`. BudgetUiState uses `selectedPeriodType`/`selectedPeriodLabel`/`selectedPeriodKey` instead of `currentYearMonth`. No DB schema changes — fully backward compatible with BudgetService alerts and HomeScreen summary.
18. **Global Month Start Day Setting** — Replaced the CUSTOM period tab with a single global preference: "My month starts on day X" (default 1, range 1–28). All MONTHLY budgets use this offset globally. Covers the "salary on 25th" use case without per-budget custom date pickers. `AppPreferences` stores the setting; `BudgetRepository` caches it and computes offset-aware period ranges, keys ("2026-03-25" format), and labels ("Mar 25 – Apr 24, 2026"). CUSTOM enum kept in `BudgetPeriod` for DB compatibility but hidden from UI via `BudgetPeriod.uiEntries`. Settings screen has a new "Budget Month" section with a dropdown picker showing ordinal suffixes (1st, 2nd, 3rd, etc.).
19. **HowItWorksCard Removal** — Removed the static onboarding "How It Works" card from the Home screen feed. The information is still available in the onboarding flow for new users.
20. **Bottom Navigation Fix** — Fixed Analytics → Home navigation not working. Root cause: `restoreState = true` silently fails when no previously-saved state exists for the start destination. Fix: special-case the start destination (Home) to use `inclusive = true` and skip `saveState`/`restoreState`, eliminating the silent no-op.
21. **Database Backup & Restore** — Full database backup/restore via SAF (Storage Access Framework). Backup creates a .zip archive containing the Room database + a settings.json with month start day and bank tracking preferences. Restore validates the SQLite header, closes the current database, replaces files, restores preferences to DataStore, and restarts the app process to reinitialize Hilt singletons. Users can save backups to Downloads, Google Drive, etc. Solves data loss on uninstall, debug-to-Play Store migration, and device transfers.
22. **Onboarding "Import Now" Navigation Fix** — Fixed the "Import Now" button on onboarding page 4 navigating to the Home screen instead of the Import screen. Root cause: `onImportNow` callback called both `onImportHistory()` (no-op) and `onComplete()` (which finishes onboarding and shows Home). Fix: `onImportHistory` now sets a `pendingImportNavigation` flag in `AppEntryPoint`; after onboarding completes and `MainScreen` loads, a `LaunchedEffect` navigates to `Screen.ImportHistory` and clears the flag.
23. **SMS Permission Recovery (Home Banner + Import Gate)** — Added two surfaces to recover users who skipped onboarding SMS permission or later revoked it. **(a) Home Screen banner**: shown when SMS permission is missing and not permanently dismissed; three actions — "Enable" (launches permission request), "Not now" (session dismiss), "Don't ask again" (permanent dismiss via `KEY_SMS_BANNER_DISMISSED` in DataStore — respects manual-only users). Permission status rechecked on every `Lifecycle.RESUMED` event (catches returns from App Settings). **(b) Import Screen gate**: when `READ_SMS` is not granted, the SMS import UI is replaced with a full-screen explanation card + "Grant SMS Permission" / "Open App Settings" buttons; Excel import remains accessible below the gate. `AppPreferences` gains `smsBannerDismissed` Flow + snapshot + setter.
24. **Budget Forecasting (4 Phases)** — Linear burn rate forecasting across the entire budget system. **Phase A (Home Forecast Card):** New `ForecastService` (`@Singleton`, Hilt-injected, pure Kotlin — no DB tables, no schema migration) computes `BudgetForecast` objects (dailyBurnRate, exhaustionDate, projectedTotal, safeDailyBudget) from existing budget/spending data. `HomeScreen` shows a `ForecastCard` with top 5 at-risk budgets, color-coded status (🔴 exhaustion imminent / 🟡 projected over / 🟢 on track), safe daily spend, and "View →" navigation to budget screen. Minimum 5-day data guard prevents noisy projections. **Phase B (Budget Per-Card Forecast):** Each `BudgetProgressCard` on the budget screen shows a forecast subtitle: projected % by period-end, exhaustion date warning, and safe daily spend — all period-aware (weekly/monthly/yearly). `BudgetUiState.forecastMap` maps budget IDs to forecasts. **Phase C (Forecast Notifications):** `SmsReceiver` triggers `BudgetService.checkForecastsAfterExpense()` after each SMS expense save. Notifications fire when projected ≥110% with ≥7 days remaining OR exhaustion <5 days. 24-hour per-budget throttle via `AppPreferences` DataStore keys prevents notification spam. **Phase D (Analytics Projection Chart):** `ForecastProjectionChart` in analytics shows cumulative actual spending (solid line) + projected extension to month-end (second series) + horizontal budget ceiling reference line. Uses Vico 2-series line chart. Only shown for current month with spending data.
25. **Recurring Expense Detection (4 Phases)** — Pattern-based detection of recurring expenses with no new DB tables or schema changes. **Phase A (Detection Engine):** New `RecurringExpenseService` (`@Singleton`, Hilt-injected, 15-minute in-memory cache) analyses interval patterns between payments to the same recipient. Detects 4 cycle types: WEEKLY (5–9 day gaps), BIWEEKLY (12–16 days), MONTHLY (26–35 days), QUARTERLY (80–100 days). Requires ≥3 occurrences. Amount classified as FIXED (CV ≤25%, ±10% tolerance) or VARIABLE. New DAO query `getExpensesForRecurrenceDetection()` groups candidates with ≥3 payments in the last 12 months. **Phase B (Analytics Split):** `AnalyticsScreen` monthly tab shows a `RecurringBreakdownCard` with two progress bars (recurring vs one-time spending) and top 3 recurring expense names. Injected via `AnalyticsViewModel.loadRecurringBreakdown()`. **Phase C (Recurring-Aware Forecasting):** `ForecastService.computeForecast()` splits spending into recurring (known) + discretionary (extrapolated) components. Only discretionary spending is projected via burn rate; upcoming recurring expenses are added as known amounts. Eliminates absurd projections caused by large one-time recurring payments (e.g. rent on day 1). `ForecastService` fetches recurring info internally — no caller changes needed. **Phase D (Notifications):** Daily `RecurringReminderWorker` (WorkManager, `@HiltWorker`) checks for upcoming (within 2 days) and overdue (3+ days late) recurring expenses. Shows reminder notifications via dedicated "Recurring Reminders" notification channel. Toggleable in Settings via `recurringRemindersEnabled` preference. Per-expense notification throttle prevents spam (cycle-duration-based cooldown).

---

## MVP Feature Checklist

| Feature | Status | Notes |
|---------|--------|-------|
| M-PESA SMS parsing (7 expense types) | ✅ Complete | Send Money, Buy Goods, Pay Bill, Withdraw, Airtime, M-PESA Card, Fuliza |
| Transaction cost auto-tracking | ✅ Complete | Auto-saved under category 811 |
| Non-expense SMS filtering | ✅ Complete | Receive Money, Deposit, Reversal skipped |
| Local expense storage with Room | ✅ Complete | Full CRUD with migrations |
| Expense list with categories | ✅ Complete | Category names and colours |
| Monthly summary on home screen | ✅ Complete | Total + recent 5 expenses |
| Uncategorized expense alert | ✅ Complete | Count shown on home screen |
| Categorize expenses | ✅ Complete | Grouped hierarchical picker |
| Notification for SMS-parsed expenses | ✅ Complete | Tap to categorize |
| Phone number auto-fill | ✅ Complete | SIM reading + DataStore |
| Duplicate detection | ✅ Complete | transactionId uniqueness |
| Correct payment type labels | ✅ Complete | `fromString()` handles both formats |
| Category-aware expense cards | ✅ Complete | Category name as title |
| Edit/re-categorize expenses | ✅ Complete | Tap any expense to change its category |
| Runtime permission flow | ✅ Complete | SMS via onboarding; notifications after onboarding |
| Manual expense entry | ✅ Complete | Form with amount, recipient, payment type, date, category, notes |
| Historical SMS import | ✅ Complete | ContentResolver-based import with date range picker |
| Excel spreadsheet import | ✅ Complete | Match to SMS + import unmatched + multi-file |
| Recipient-based auto-categorization | ✅ Complete | Learned mappings applied to new transactions |
| Batch categorization by recipient | ✅ Complete | Group uncategorized expenses by recipient |
| Bank SMS tracking (NCBA) | ✅ Complete | Strategy pattern with parser registry |
| Settings screen | ✅ Complete | Bank SMS tracking toggles |
| Multi-source SMS import | ✅ Complete | M-PESA + enabled banks |
| Exclude pass-through expenses | ✅ Complete | Long-press to exclude from totals |

---

## Phase 2: Feature Progress

| Milestone | Feature | Status | Notes |
|-----------|---------|--------|-------|
| **M1** | Historical SMS Import | ✅ Complete | ContentResolver import with date range |
| **M1** | Recipient Category Mapping | ✅ Complete | Learned recipient→category auto-apply |
| **M1** | Batch Categorize Screen | ✅ Complete | Group by recipient, apply-to-all |
| **M1** | Import Screen with progress | ✅ Complete | Date picker + import summary |
| **M1** | Deterministic auto-categorization | ✅ Complete | Airtime→1001, Costs→811 |
| **M1** | SmsReceiver auto-categorize | ✅ Complete | Uses recipient mapping for live SMS |
| **M2** | Bank SMS Tracking (NCBA) | ✅ Complete | Strategy pattern + parser registry + Settings UI |
| — | Exclude pass-through expenses | ✅ Complete | `isExcluded` flag, long-press toggle, dimmed + strikethrough UI |
| **M3** | Smart Categorization (Rules Engine) | ✅ Complete | On-device KeywordRulesEngine (replaced Gemini AI) — 100+ business names, keyword rules, PaymentType heuristics; zero cost, offline, always-on |
| — | Excel Import (match + standalone) | ✅ Complete | Apache POI parser, 55+ category mappings, multi-file, SMS matching |
| — | M-PESA Statement PDF Import | ✅ Complete | PDFBox parser, 13+ transaction types, password-protected PDFs, charge linking, dedup, auto-categorize |
| **M4** | Manual expense entry screen | ✅ Complete | Form: amount, recipient, payment type, date, category, notes; saves with recipient mapping |
| **M5** | Settings & Configuration | ✅ Complete | Bank toggles (M2); Category management (M8); About screen; Privacy policy (GitHub Pages); Data management (export CSV + reset categories); First-launch onboarding flow |
| — | Expense charts and analytics | ✅ Complete | Vico charts: monthly trend, **variable-spend category trends (CV detection, ≥3 months, KES 100 min)**, daily spending, category breakdown, top spenders, payment type breakdown, MoM comparison |
| — | Year-over-Year analytics | ✅ Complete | Tab-based Monthly/Yearly view: annual total card, YoY % change, 12-month overlay chart (this year vs last year), yearly category breakdown, top recipients by year, payment type breakdown by year |
| — | Monthly/weekly summaries | ✅ Complete | Month selector + daily/monthly aggregation in analytics |
| **M6** | Investment Category Deep-Dive | ✅ Complete | New group 18 "Investment & Savings" (13 sub-categories); Financial trimmed to expense-only (6 sub-categories); DB migration v7→v8 remaps IDs; KeywordRulesEngine + ExcelCategoryMapper updated with Kenyan investment paybills |
| **M7** | Category & Sub-Category Budgets | ✅ Complete | Group-level + sub-category-level + total budgets (weekly/monthly/yearly); DB v8→v9 (budgets table), v11→v12 (sub-category support: renamed categoryGroupId→categoryId, added isGroupBudget); BudgetScreen CRUD with hierarchical category picker; Budget Alerts at 80%/100% for all levels; Home budget summary + data-driven prompt; Analytics setup banner; Settings entry point |
| **M8** | Custom Categories & Auto-Rules | ✅ Complete | Custom groups + sub-categories CRUD with icon/color pickers; user-defined auto-categorization rules (EXACT/CONTAINS/STARTS_WITH); DB v9→v10 (category_rules table); rules integrated into CategorizationService (checked before built-in engine); Settings entry point |
| — | Forecasting | ✅ Complete | Budget burn rate projections — 4 phases: Home forecast card, Budget screen per-card forecast, Forecast notifications (24h throttle), Analytics projection chart |
| — | Database Backup/Restore | ✅ Complete | .zip backup via SAF (database + settings.json); restore with SQLite validation + app restart |
| — | Cloud sync | ⏳ Pending | Backup/restore across devices |
| — | Recurring expense tracking | ✅ Complete | Detection engine + analytics split + recurring-aware forecasting + daily reminder notifications |
| — | Insights & Reports v1.2 | ✅ Complete | Insights tab on Analytics (card feed: Weekly/Monthly Review, Pace, Quiet Leak, Categorization Nudge) |
| — | Insights & Reports v1.3 | ✅ Complete | Quarterly Review screen + Budget Burn-Down card + QuarterlyReviewWorker + 8 unit tests |
| — | Insights & Reports v1.4 | ✅ Complete | Year-in-Review screen + YearInReviewWorker + ReportRenderer share-as-image + unit tests |

---

## Next Steps (Recommended)

### High Priority
- [x] Test on a real Android device with actual M-PESA + NCBA SMS messages
- [x] Fix any parsing bugs discovered from real-world SMS formats
- [x] Pre-release cleanup (removed unused permissions, dead code, configured signing) — see [`plans/signed-apk-playstore-plan.md`](../plans/signed-apk-playstore-plan.md)
- [x] Generate signed AAB for Play Store distribution — `app-release.aab` (13.9 MB), signed by CN=Joel Ngei, valid until 2053
- [x] Submit to Google Play Store — v1.0.0 published to Production track (under review) + Internal Testing (live for testers)
- [x] Complete SMS Permission Declaration
- [x] Monitor Production review status — app live on Google Play

### Medium Priority — Phase 2 Milestone 5
- [x] About section (app version, privacy policy link, contact, data practices summary)
- [x] Privacy policy page (GitHub Pages: `docs/privacy-policy.html`)
- [x] Data management — Reset categories to defaults (removes custom categories & rules)
- [x] First-launch onboarding flow (4-page HorizontalPager: Welcome, How It Works, SMS Permission, Import History)
- [ ] Notification preferences (deferred — low priority)
- [x] Category management UI (M8 — custom categories + auto-rules)

### ~~Medium Priority — Phase 2 Milestone 6 (Investment Deep-Dive)~~ ✅ Complete
- [x] Audit current Financial sub-categories (602, 605, 610, 611, 612) for investment coverage gaps
- [x] Decide: promote Investment to its own top-level group (Option A — group 18)
- [x] Add missing investment sub-categories (Stocks, Crypto, Real Estate, Chama, Pension, Fixed Deposit, Unit Trusts, Insurance)
- [x] Add KeywordRulesEngine entries for common Kenyan investment paybills (CIC, Sanlam, Cytonn, Britam, Genghis, Old Mutual, Stima SACCO, M-Akiba, Binance, etc.)
- [x] Opted for group-level separation instead of `isInvestment` flag (cleaner UX)
- [x] DB migration v7→v8: remap 602→1811, 605→1805, 607→1806, 610→1809, 611→1810, 612→1812

### ~~Medium Priority — Phase 2 Milestone 7 (Category & Sub-Category Budgets)~~ ✅ Complete
- [x] Budget data model: BudgetEntity + BudgetDao + DB migration v8→v9 (budgets table with unique index)
- [x] Budget domain model: Budget, BudgetPeriod (WEEKLY/MONTHLY/YEARLY), BudgetProgress, BudgetStatus, BudgetAlert
- [x] BudgetRepository: CRUD, period range computation (weekly/monthly/yearly), spending aggregation, progress/alert calculation
- [x] BudgetScreen CRUD UI: list with progress bars, FAB to add, edit/delete dialog, grouped category picker
- [x] Budget progress on Home screen: BudgetSummaryCard (top 3 by %), BudgetPromptCard (data-driven, ≥20 categorized expenses)
- [x] Budget alerts: BudgetService checks thresholds after SMS expense save; NotificationHelper shows 80%/100% alerts
- [x] Analytics integration: budget setup banner when no budgets exist
- [x] Entry points: Settings "Manage Budgets" row, Home cards navigate to Budget screen
- [x] No rollover — fresh budget each period (as per user preference)
- [x] Investments included in budgets (user wants to budget for them)
- [x] **Sub-category budgets**: DB migration v11→v12 (renamed `categoryGroupId`→`categoryId`, added `isGroupBudget` column); three budget levels (Total, Group, Sub-category); hierarchical category picker in add/edit dialog; independent tracking when both group and sub-category budgets exist; alerts fire for all affected budgets

### Lower Priority
- [ ] Add more bank parsers (Equity, KCB, Cooperative, etc.)
- [x] Expense charts/analytics (Vico library — monthly trend, variable-spend category trends with CV detection, daily spending, category breakdown, top spenders, payment types, MoM comparison, **YoY yearly analytics with tab-based view**)
- [ ] Clean up unused backend deployment on Railway

---

## Brainstorm: Investment Category Deep-Dive (M6)

### Current State

The **Financial** group (ID 6) currently contains 12 sub-categories that mix true expenses with wealth-building activity:

| ID | Name | Type |
|----|------|------|
| 601 | Bank Charges | Expense ✅ |
| 602 | Investments | Investment 💰 |
| 603 | Loan Interest | Expense ✅ |
| 604 | Loan Repayment | Debt 💳 |
| 605 | Money Market Fund | Investment 💰 |
| 606 | Mpesa Transaction Cost | Expense ✅ |
| 607 | NSSF | Investment 💰 (mandatory) |
| 608 | Pesalink Charges | Expense ✅ |
| 609 | RTGS Charges | Expense ✅ |
| 610 | SACCO | Investment 💰 |
| 611 | Savings | Investment 💰 |
| 612 | Treasury Bill | Investment 💰 |

**Problem:** Lumping bank charges with investments inflates perceived "spending" and hides actual wealth-building. A user transferring KES 50,000 to a money market fund shouldn't see that as the same kind of outflow as KES 500 in bank charges.

### Option A: Promote Investment to a Top-Level Group

Create group **18: Investment & Savings** and move investment-type sub-categories there:

| New ID | Name | Notes |
|--------|------|-------|
| 1801 | Savings Account | General savings |
| 1802 | Money Market Fund | e.g. CIC MMF, Sanlam, Cytonn |
| 1803 | Treasury Bill/Bond | Government securities |
| 1804 | SACCO Contributions | e.g. Stima, Mwalimu |
| 1805 | NSSF/NHIF | Mandatory deductions |
| 1806 | Stocks/Shares | NSE, global broker |
| 1807 | Unit Trusts/Mutual Funds | Collective investment schemes |
| 1808 | Crypto | Bitcoin, ETH, etc. |
| 1809 | Real Estate Investment | Land, REIT, plots |
| 1810 | Chama Contributions | Investment groups |
| 1811 | Pension/Retirement | Voluntary pension |
| 1812 | Fixed Deposit | Bank FDs |
| 1813 | Insurance (Investment) | Endowment, whole life |

**Pros:** Clean separation; analytics can show "Total Expenses" vs "Total Invested"
**Cons:** DB migration; existing categorized expenses under 602/605/610–612 need remapping

### Option B: Keep Under Financial, Add `isInvestment` Flag

Add a boolean `isInvestment` column to `CategoryEntity`. Mark investment sub-categories. Analytics filters by this flag.

**Pros:** No group restructuring; simpler migration
**Cons:** Less visible separation in UI; still mixed in the category picker

### Recommendation

**Option A** — it's the right long-term move. The migration can remap old IDs, and the UI benefit is immediate. Investment tracking deserves first-class visibility.

### Auto-Categorization Targets

Common Kenyan investment paybills/tills to add to `KeywordRulesEngine`:

| Recipient | Category |
|-----------|----------|
| CIC Asset Management | Money Market Fund |
| Sanlam Investments | Money Market Fund |
| Cytonn Investments | Money Market Fund |
| Britam Asset Managers | Unit Trusts |
| Genghis Capital | Stocks/Shares |
| ICEA Lion | Unit Trusts |
| Old Mutual | Pension/Retirement |
| Stima SACCO | SACCO Contributions |
| Mwalimu National SACCO | SACCO Contributions |
| Kenya Re SACCO | SACCO Contributions |
| Safaricom SACCO | SACCO Contributions |
| M-Akiba | Treasury Bill/Bond |
| Binance | Crypto |
| Lipa Na M-PESA (specific SACCOs) | SACCO Contributions |

---

## Brainstorm: Category-Based Budgets (M7)

### The Core Problem

Users can see *what* they spent and *where*, but they can't set limits or get warned when they're overspending in a category. The analytics show history — budgets add **forward-looking control**.

### How It Should Work

#### 1. Data Model

```
BudgetEntity
├── id: Long (PK)
├── categoryId: Long (FK → categories, nullable for "Total" budget)
├── isGroupBudget: Boolean (true = group level, false = sub-category level)
├── amount: Double (budget limit in KES)
├── period: BudgetPeriod (MONTHLY, WEEKLY, YEARLY)
├── alertAt: List<Int> (e.g. [80, 100] — percentage thresholds)
├── rolloverEnabled: Boolean (unspent amount carries to next period)
├── isActive: Boolean
├── createdAt: Long
├── updatedAt: Long
```

```kotlin
enum class BudgetPeriod { WEEKLY, MONTHLY, YEARLY }
```

#### 2. Budget Levels — Three Tiers

| Level | Example | What it tracks |
|-------|---------|----------------|
| **Total** | "I want to spend ≤ KES 80,000/month" | Sum of ALL non-excluded expenses |
| **Group** | "Food & Dining ≤ KES 15,000/month" | Sum of all sub-categories in group 7 |
| **Sub-category** | "Eating Out ≤ KES 5,000/month" | Only category 702 |

**Conflict resolution:** If both a group budget and sub-category budget exist, both are independently tracked. The sub-category budget is the tighter constraint (alerts fire for whichever is hit first).

#### 3. Budget Setup UI

**Entry point:** Settings → "Budgets" section, or a new "Budget" tab in Analytics.

**Flow:**
1. Tap "Add Budget" → pick category (grouped picker, same as categorize screen — plus a "Total Spending" option at top)
2. Enter amount (KES input with currency formatting)
3. Pick period (Monthly default, Weekly/Yearly available)
4. Toggle rollover on/off (default: off)
5. Set alert thresholds (default: 80% and 100%; customizable)
6. Save

**Edit/Delete:** Long-press or swipe on budget list item.

#### 4. Budget Progress Display

**Home Screen — Budget Summary Card:**
```
┌──────────────────────────────────────────┐
│  📊 March Budget                         │
│                                          │
│  Total     ████████████░░░░  KES 62,400  │
│            ─────────────────  / 80,000    │
│                                   78%    │
│                                          │
│  ⚠️ Food    ████████████████  KES 14,800  │
│            ─────────────────  / 15,000    │
│                                   99%    │
│                                          │
│  Transport ██████░░░░░░░░░░  KES 3,200   │
│            ─────────────────  / 8,000     │
│                                   40%    │
└──────────────────────────────────────────┘
```

- Green bar: < 80%
- Amber bar: 80–99%
- Red bar: ≥ 100%
- Only show budgets that are active; sort by % used descending

**Analytics Screen — Budget vs Actual:**
- Category breakdown chart with budget line overlay
- Monthly trend chart with budget ceiling line
- "Over budget" categories highlighted in red

#### 5. Alert Notifications

When an expense is saved (SMS, manual, or import) and it pushes a budget past a threshold:

| Threshold | Notification |
|-----------|-------------|
| 80% | "⚠️ Food & Dining: 80% of KES 15,000 budget used (KES 12,100 spent)" |
| 100% | "🚨 Food & Dining: Budget exceeded! KES 15,200 / 15,000 (101%)" |

**Implementation:** Check budgets in `ExpenseRepository.insertExpense()` after successful save. Use existing `NotificationHelper` with a new channel "Budget Alerts".

#### 6. Rollover Logic

If `rolloverEnabled = true`:
- End of period: `remainingBudget = budgetAmount - actualSpend`
- Next period budget = `budgetAmount + remainingBudget` (if positive) or `budgetAmount` (if overspent, no penalty carry)
- Store rollover history in a `BudgetRolloverEntity` for audit trail

**Default: Off.** Most users expect a fresh budget each month.

#### 7. Excluded Expenses

Expenses with `isExcluded = true` do **not** count toward any budget. This is consistent with the existing analytics behavior.

#### 8. Investment Interaction (M6 → M7 Dependency)

If M6 is done first and investments are separated:
- Budgets only apply to **expense** categories (not investment)
- "Total" budget = total expenses excluding investment outflows
- This prevents a KES 50,000 MMF transfer from blowing the monthly budget

If M6 is NOT done first:
- User can choose to exclude Financial > Investments from the total budget manually
- Or we add a "Budget applies to expenses only" toggle

#### 9. Suggested Budgets (Nice-to-Have)

Based on the last 3–6 months of data:
- "You typically spend KES 14,200/month on Food & Dining. Set budget at KES 15,000?"
- Uses same CV-based analysis from analytics to identify stable categories worth budgeting

#### 10. Technical Implementation Plan

| Step | Component | Description |
|------|-----------|-------------|
| 1 | `BudgetEntity` | Room entity + DAO + migration |
| 2 | `BudgetRepository` | CRUD + budget checking logic |
| 3 | `BudgetViewModel` | Budget setup/edit UI state |
| 4 | `BudgetScreen` | Compose UI for budget CRUD |
| 5 | Home integration | Budget summary card on HomeScreen |
| 6 | Alert integration | Budget check in ExpenseRepository + NotificationHelper |
| 7 | Analytics integration | Budget overlay on category charts |
| 8 | Navigation | Add Budget route to NavGraph |

### Open Questions

1. **Should budgets be per-month or configurable period?** Monthly covers 90% of use cases. Weekly/yearly adds flexibility but also complexity.
2. **Group vs sub-category budgets?** Supporting both is ideal but the UI needs to be clear about which level you're setting.
3. **What happens when the user recategorizes an expense?** Both the old and new category budgets need recalculation. Use Room Flow/LiveData to make this reactive.
4. **Should there be a "quick budget" from the category breakdown chart?** Tap a category bar → "Set budget for this category" — reduces friction.


---

## Brainstorm: Forecasting (✅ Complete)

> **Status:** ✅ Implemented — all 4 phases complete. See [implementation plan](../plans/forecasting-implementation-plan.md).
> **Original plan:** [`plans/forecasting-plan.md`](../plans/forecasting-plan.md)

### The Idea

Forecasting completes the budget loop. Budgets are reactive (alert at 80%/100% of *actual* spend). Forecasting is proactive ("at your current pace, you'll bust the budget by the 25th").

### Why Defer

1. **Budget adoption is untested** — M7 budgets haven't been tested on real devices yet. If nobody uses budgets, there's nothing to forecast against.
2. **Needs data maturity** — Linear projections need ≥5 days in a period; seasonal models need ≥3 months of history. Brand-new users get noise.
3. **Recurring expense detection (pending)** — A KES 35,000 rent payment on day 1 makes projections absurd for the rest of the month. Needs recurring expense awareness.
4. **MVP is trivially quick (~2–3 hours)** — Easy to slot in later. No rush to build speculatively.

### Key Outputs (When Built)

| Output | Surface | Description |
|--------|---------|-------------|
| **Budget exhaustion date** | Home card | "Food & Dining runs out ~March 25th" |
| **Projected end-of-period spend** | Home card | "Projected: KES 87,200 / 80,000 (109%)" |
| **Safe daily budget** | Home card | "KES 240/day to stay on track" |
| **Projection line** | Analytics chart | Dashed line from today → month-end + budget ceiling |
| **Forecast notifications** | Notification | "⏰ Budget runs out in ~4 days at current pace" |

### Models (Progressive)

1. **Linear burn rate** (MVP) — `spent / daysElapsed × totalDays`. Zero new queries needed.
2. **Weighted recent days** — 60% weight on last 7 days, 40% on last 14. One new DAO query.
3. **Day-of-week seasonal** — Historical weekday profile from 3 months. One new DAO query.

### Architecture

A new `ForecastService` (pure Kotlin, Hilt-injected) takes existing `ExpenseRepository` + `BudgetRepository` as inputs and produces `BudgetForecast` objects. **No new database tables. No schema migration.**

### ~~Trigger to Revisit~~ — Implemented

All 4 phases implemented. Linear burn rate model (MVP) deployed across Home, Budget, Analytics screens, and notifications. No new DB tables — `ForecastService` is pure computation on top of existing `BudgetRepository` data.
