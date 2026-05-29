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
| **Room Database (v16)** | ✅ Complete | 100% |
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
| **Budget Forecasting (4 phases)** | ✅ Complete | 100% |
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
| Database Setup | [`PesaTrackDatabase.kt`](../android/app/src/main/java/com/pesatrack/data/local/database/PesaTrackDatabase.kt:16) | Version 15 with migrations |
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
| Expense Entity | [`ExpenseEntity.kt`](../android/app/src/main/java/com/pesatrack/data/local/database/entities/ExpenseEntity.kt:11) | Full schema with FK to categories + isExcluded flag |
| Category Entity | [`CategoryEntity.kt`](../android/app/src/main/java/com/pesatrack/data/local/database/entities/CategoryEntity.kt:12) | Hierarchical categories with parent-child |
| Category Rule Entity | [`CategoryRuleEntity.kt`](../android/app/src/main/java/com/pesatrack/data/local/database/entities/CategoryRuleEntity.kt:1) | User-defined auto-categorization rules (pattern, matchType, categoryId, priority) |
| Budget Entity | [`BudgetEntity.kt`](../android/app/src/main/java/com/pesatrack/data/local/database/entities/BudgetEntity.kt:1) | Budget limits per category group, sub-category, or total, with period + isActive + isGroupBudget |
| Income Entity | [`IncomeEntity.kt`](../android/app/src/main/java/com/pesatrack/data/local/database/entities/IncomeEntity.kt:1) | Monthly income records (amount, yearMonth unique, note) for budget allocation checking |
| Default Categories | [`CategoryEntity.kt`](../android/app/src/main/java/com/pesatrack/data/local/database/entities/CategoryEntity.kt:57) | 17 groups, 95+ sub-categories |
| Expense DAO | [`ExpenseDao.kt`](../android/app/src/main/java/com/pesatrack/data/local/database/dao/ExpenseDao.kt:10) | CRUD + month queries + duplicate check + budget spending queries (total, group, sub-category) |
| Category DAO | [`CategoryDao.kt`](../android/app/src/main/java/com/pesatrack/data/local/database/dao/CategoryDao.kt:11) | CRUD + search + default seeding + expense count queries + group management |
| Category Rule DAO | [`CategoryRuleDao.kt`](../android/app/src/main/java/com/pesatrack/data/local/database/dao/CategoryRuleDao.kt:1) | Rule CRUD + active rules query |
| Budget DAO | [`BudgetDao.kt`](../android/app/src/main/java/com/pesatrack/data/local/database/dao/BudgetDao.kt:1) | Budget CRUD + active budget queries + affected budget lookups (group + sub-category) |
| Income DAO | [`IncomeDao.kt`](../android/app/src/main/java/com/pesatrack/data/local/database/dao/IncomeDao.kt:1) | Income upsert + getByYearMonth + observe as Flow |
| **Preferences** | | |
| AppPreferences | [`AppPreferences.kt`](../android/app/src/main/java/com/pesatrack/data/local/preferences/AppPreferences.kt:1) | DataStore for phone number, bank preferences, budget prompt dismissal, month start day (1–28) |
| **Repositories** | | |
| Expense Repository | [`ExpenseRepository.kt`](../android/app/src/main/java/com/pesatrack/data/repository/ExpenseRepository.kt:17) | CRUD, month range, domain mapping |
| Category Repository | [`CategoryRepository.kt`](../android/app/src/main/java/com/pesatrack/data/repository/CategoryRepository.kt:18) | Category CRUD (add/edit/delete groups + sub-categories), default init, expense count checks |
| Category Rule Repository | [`CategoryRuleRepository.kt`](../android/app/src/main/java/com/pesatrack/data/repository/CategoryRuleRepository.kt:1) | Rule CRUD, active rules loading for categorization pipeline |
| Budget Repository | [`BudgetRepository.kt`](../android/app/src/main/java/com/pesatrack/data/repository/BudgetRepository.kt:1) | Budget CRUD, period range computation (with month-start-day offset), spending aggregation (total/group/sub-category), progress/alert calculation, monthly income get/set, total budgeted computation |
| **Dependency Injection** | | |
| Hilt App Module | [`AppModule.kt`](../android/app/src/main/java/com/pesatrack/di/AppModule.kt:19) | Database (v15 with all migrations), DAOs (including BudgetDao, CategoryRuleDao, IncomeDao) |

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
| Nav Graph | [`NavGraph.kt`](../android/app/src/main/java/com/pesatrack/presentation/navigation/NavGraph.kt:17) | 16 routes: Home, Analytics, Expenses, Categorize, Import, ExcelImport, BatchCategorize, Settings, ManualEntry, Budget, CategoryManagement, About, WeeklyReview, MonthlyReview, QuarterlyReview, YearInReview |
| Screen Routes | [`Screen.kt`](../android/app/src/main/java/com/pesatrack/presentation/navigation/Screen.kt:6) | Sealed class with route definitions (incl. QuarterlyReview, YearInReview) |
| Bottom Nav | [`Screen.kt`](../android/app/src/main/java/com/pesatrack/presentation/navigation/Screen.kt:23) | 3 tabs: Home, Analytics, Expenses |
| **Main Activity** | | |
| MainActivity | [`MainActivity.kt`](../android/app/src/main/java/com/pesatrack/presentation/MainActivity.kt:48) | Onboarding overlay → PIN lock overlay → main app; biometric setup; notification channel |
| MainScreen | [`MainActivity.kt`](../android/app/src/main/java/com/pesatrack/presentation/MainActivity.kt:252) | Scaffold with NavigationBar + NavGraph |
| **Home Screen** | | |
| HomeScreen | [`HomeScreen.kt`](../android/app/src/main/java/com/pesatrack/presentation/screens/home/HomeScreen.kt:24) | Monthly summary with investment % breakdown, recent expenses, uncategorized alert |
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
| GroupedCategoryPicker | [`GroupedCategoryPicker.kt`](../android/app/src/main/java/com/pesatrack/presentation/components/GroupedCategoryPicker.kt:1) | Hierarchical category selector |
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
| Gradle Build | [`build.gradle.kts`](../android/app/build.gradle.kts:1) | compileSdk 35, minSdk 26, targetSdk 35, Kotlin 17 |
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
│   └── AppModule.kt                         ✅ Database, DAOs (incl. BudgetDao, CategoryRuleDao, IncomeDao)
├── data/
│   ├── local/
│   │   ├── database/
│   │   │   ├── PesaTrackDatabase.kt         ✅ Room v15 with migrations (v14→v15: Family & Friends Support category)
│   │   │   ├── dao/
│   │   │   │   ├── ExpenseDao.kt            ✅ CRUD + month queries + duplicate check + budget spending queries
│   │   │   │   ├── CategoryDao.kt           ✅ CRUD + search + default seeding + expense count queries + group mgmt
│   │   │   │   ├── CategoryRuleDao.kt       ✅ Rule CRUD + active rules query for categorization pipeline
│   │   │   │   ├── BudgetDao.kt             ✅ Budget CRUD + active queries + affected budget lookups
│   │   │   │   ├── IncomeDao.kt             ✅ Income upsert + getByYearMonth + observe as Flow
│   │   │   │   └── RecipientCategoryMappingDao.kt ✅ Recipient→category learned mappings CRUD
│   │   │   └── entities/
│   │   │       ├── ExpenseEntity.kt          ✅ Full schema with FK to categories
│   │   │       ├── CategoryEntity.kt         ✅ 17 groups, 95+ categories
│   │   │       ├── CategoryRuleEntity.kt     ✅ User-defined auto-categorization rules (pattern, matchType, priority)
│   │   │       ├── BudgetEntity.kt           ✅ Budget limits per group/sub-category/total with period + isActive + isGroupBudget
│   │   │       ├── IncomeEntity.kt           ✅ Monthly income records (amount, yearMonth, note)
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
│   │   │   ├── HomeScreen.kt                ✅ Monthly summary (with investment % breakdown) + mini trend chart + recent expenses + budget summary/prompt cards + **ForecastCard** (HowItWorksCard removed)
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
│   ├── NotificationHelper.kt               ✅ Expense channel + Budget Alerts channel + **forecast notifications** + **Recurring Reminders channel** + **quarterly_review channel** + **yearly_review channel** + **budget_burndown channel** + **Categorize/Ignore action buttons**
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
        ├── SmsParserStrategy.kt             ✅ Strategy interface for SMS parsers
        ├── SmsParserRegistry.kt             ✅ Central dispatcher (sender → parser)
        ├── MpesaSmsParser.kt                ✅ M-PESA parser (8 expense types)
        ├── MpesaStatementParser.kt          ✅ PDF text extraction, 13+ transaction type regex, password-protected PDF support
        └── NcbaBankParser.kt                ✅ NCBA bank parser (3 types)
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
