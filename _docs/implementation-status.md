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
| **Room Database (v12)** | ✅ Complete | 100% |
| **Category System (18 groups + custom)** | ✅ Complete | 100% |
| **Expense Management UI** | ✅ Complete | 100% |
| **Notifications** | ✅ Complete | 100% |
| **Runtime Permissions** | ✅ Complete | 100% |
| **Backend Server (unused)** | 🟡 Dormant | N/A |
| **Phase 2 M1: Historical SMS Import + Recipient Learning** | ✅ Complete | 100% |
| **Phase 2 M2: Bank SMS Tracking (NCBA)** | ✅ Complete | 100% |
| **Phase 2 M3: Smart Categorization (Rules Engine)** | ✅ Complete | 100% |
| **Excel Import (match + standalone)** | ✅ Complete | 100% |
| **Phase 2 M4: Manual Expense Entry** | ✅ Complete | 100% |
| **Phase 2 M5: Settings & Configuration** | ✅ Complete | 100% |
| **About Screen + Privacy Policy** | ✅ Complete | 100% |
| **Data Management (Export + Reset)** | ✅ Complete | 100% |
| **Expense Charts & Analytics** | ✅ Complete | 100% |
| **Year-over-Year Analytics** | ✅ Complete | 100% |
| **Phase 2 M6: Investment Category Deep-Dive** | ✅ Complete | 100% |
| **Phase 2 M7: Category & Sub-Category Budgets** | ✅ Complete | 100% |
| **Phase 2 M8: Custom Categories & Auto-Rules** | ✅ Complete | 100% |
| **PIN Lock + Biometric Unlock** | ✅ Complete | 100% |

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
| **NCBA Bank Parser** | [`NcbaBankParser.kt`](../android/app/src/main/java/com/pesatrack/utils/parsers/NcbaBankParser.kt:38) | 3 NCBA transaction types (Send, Till, Paybill) |
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
| NCBA Till Payment | [`NcbaBankParser.kt`](../android/app/src/main/java/com/pesatrack/utils/parsers/NcbaBankParser.kt:76) | Pattern: `"Mpesa Till transfer of KES to TILL"` |
| NCBA Paybill (2 formats) | [`NcbaBankParser.kt`](../android/app/src/main/java/com/pesatrack/utils/parsers/NcbaBankParser.kt:88) | Format A: `"...to NAME PAYBILL account..."`, Format B: `"...to NAME account number ACCT..."` |
| Transaction cost extraction | [`MpesaSmsParser.kt`](../android/app/src/main/java/com/pesatrack/utils/parsers/MpesaSmsParser.kt:48) | Regex: `"Transaction cost,? Ksh..."` |
| Non-expense filtering | [`MpesaSmsParser.kt`](../android/app/src/main/java/com/pesatrack/utils/parsers/MpesaSmsParser.kt:56) | Skips Receive Money, Deposit, Reversal |
| NCBA self-transfer skip | [`NcbaBankParser.kt`](../android/app/src/main/java/com/pesatrack/utils/parsers/NcbaBankParser.kt:70) | Skips bank→own M-PESA transfers |
| NCBA generic debit skip | [`NcbaBankParser.kt`](../android/app/src/main/java/com/pesatrack/utils/parsers/NcbaBankParser.kt:51) | Skips "has been debited" (less info) |
| SMS Receiver | [`SmsReceiver.kt`](../android/app/src/main/java/com/pesatrack/services/SmsReceiver.kt:30) | Multi-source BroadcastReceiver with bank preference check |
| Multi-source Import | [`SmsImportService.kt`](../android/app/src/main/java/com/pesatrack/services/SmsImportService.kt:36) | Imports from M-PESA + enabled banks |
| Duplicate detection | [`SmsReceiver.kt`](../android/app/src/main/java/com/pesatrack/services/SmsReceiver.kt:30) | Checks transactionId before insert |

---

### 2. Android App — Data Layer

#### ✅ Implemented

| Feature | File | Description |
|---------|------|-------------|
| **Room Database** | | |
| Database Setup | [`PesaTrackDatabase.kt`](../android/app/src/main/java/com/pesatrack/data/local/database/PesaTrackDatabase.kt:16) | Version 12 with migrations |
| Migration 2→3 | [`PesaTrackDatabase.kt`](../android/app/src/main/java/com/pesatrack/data/local/database/PesaTrackDatabase.kt:34) | Moved Seed category to Faith & Giving |
| Migration 6→7 | [`PesaTrackDatabase.kt`](../android/app/src/main/java/com/pesatrack/data/local/database/PesaTrackDatabase.kt:469) | Added `isExcluded` column to expenses |
| Migration 7→8 | [`PesaTrackDatabase.kt`](../android/app/src/main/java/com/pesatrack/data/local/database/PesaTrackDatabase.kt:484) | Investment deep-dive: moved 6 sub-categories from Financial to new Investment & Savings group (18) |
| Migration 8→9 | [`PesaTrackDatabase.kt`](../android/app/src/main/java/com/pesatrack/data/local/database/PesaTrackDatabase.kt:776) | Category-based budgets: `budgets` table with unique index on (categoryGroupId, period) |
| Migration 9→10 | [`PesaTrackDatabase.kt`](../android/app/src/main/java/com/pesatrack/data/local/database/PesaTrackDatabase.kt:798) | User-defined auto-categorization rules: `category_rules` table |
| Migration 10→11 | [`PesaTrackDatabase.kt`](../android/app/src/main/java/com/pesatrack/data/local/database/PesaTrackDatabase.kt:834) | Beekeeping group converted from default to custom |
| Migration 11→12 | [`PesaTrackDatabase.kt`](../android/app/src/main/java/com/pesatrack/data/local/database/PesaTrackDatabase.kt:845) | Sub-category budgets: renamed `categoryGroupId` → `categoryId`, added `isGroupBudget` column |
| Expense Entity | [`ExpenseEntity.kt`](../android/app/src/main/java/com/pesatrack/data/local/database/entities/ExpenseEntity.kt:11) | Full schema with FK to categories + isExcluded flag |
| Category Entity | [`CategoryEntity.kt`](../android/app/src/main/java/com/pesatrack/data/local/database/entities/CategoryEntity.kt:12) | Hierarchical categories with parent-child |
| Category Rule Entity | [`CategoryRuleEntity.kt`](../android/app/src/main/java/com/pesatrack/data/local/database/entities/CategoryRuleEntity.kt:1) | User-defined auto-categorization rules (pattern, matchType, categoryId, priority) |
| Budget Entity | [`BudgetEntity.kt`](../android/app/src/main/java/com/pesatrack/data/local/database/entities/BudgetEntity.kt:1) | Budget limits per category group, sub-category, or total, with period + isActive + isGroupBudget |
| Default Categories | [`CategoryEntity.kt`](../android/app/src/main/java/com/pesatrack/data/local/database/entities/CategoryEntity.kt:57) | 18 groups, 90+ sub-categories |
| Expense DAO | [`ExpenseDao.kt`](../android/app/src/main/java/com/pesatrack/data/local/database/dao/ExpenseDao.kt:10) | CRUD + month queries + duplicate check + budget spending queries (total, group, sub-category) |
| Category DAO | [`CategoryDao.kt`](../android/app/src/main/java/com/pesatrack/data/local/database/dao/CategoryDao.kt:11) | CRUD + search + default seeding + expense count queries + group management |
| Category Rule DAO | [`CategoryRuleDao.kt`](../android/app/src/main/java/com/pesatrack/data/local/database/dao/CategoryRuleDao.kt:1) | Rule CRUD + active rules query |
| Budget DAO | [`BudgetDao.kt`](../android/app/src/main/java/com/pesatrack/data/local/database/dao/BudgetDao.kt:1) | Budget CRUD + active budget queries + affected budget lookups (group + sub-category) |
| **Preferences** | | |
| AppPreferences | [`AppPreferences.kt`](../android/app/src/main/java/com/pesatrack/data/local/preferences/AppPreferences.kt:1) | DataStore for phone number, bank preferences, budget prompt dismissal |
| **Repositories** | | |
| Expense Repository | [`ExpenseRepository.kt`](../android/app/src/main/java/com/pesatrack/data/repository/ExpenseRepository.kt:17) | CRUD, month range, domain mapping |
| Category Repository | [`CategoryRepository.kt`](../android/app/src/main/java/com/pesatrack/data/repository/CategoryRepository.kt:18) | Category CRUD (add/edit/delete groups + sub-categories), default init, expense count checks |
| Category Rule Repository | [`CategoryRuleRepository.kt`](../android/app/src/main/java/com/pesatrack/data/repository/CategoryRuleRepository.kt:1) | Rule CRUD, active rules loading for categorization pipeline |
| Budget Repository | [`BudgetRepository.kt`](../android/app/src/main/java/com/pesatrack/data/repository/BudgetRepository.kt:1) | Budget CRUD, period range computation, spending aggregation (total/group/sub-category), progress/alert calculation |
| **Dependency Injection** | | |
| Hilt App Module | [`AppModule.kt`](../android/app/src/main/java/com/pesatrack/di/AppModule.kt:19) | Database (v12 with all migrations), DAOs (including BudgetDao, CategoryRuleDao) |

---

### 3. Android App — Domain Layer

#### ✅ Implemented

| Feature | File | Description |
|---------|------|-------------|
| Expense Model | [`Expense.kt`](../android/app/src/main/java/com/pesatrack/domain/models/Expense.kt:6) | Domain model with `isCategorized` + `isExcluded` flags |
| Category Model | [`Category.kt`](../android/app/src/main/java/com/pesatrack/domain/models/Category.kt:1) | Domain model |
| Budget Model | [`Budget.kt`](../android/app/src/main/java/com/pesatrack/domain/models/Budget.kt:1) | Budget (with isGroupBudget flag), BudgetPeriod (WEEKLY/MONTHLY/YEARLY), BudgetProgress, BudgetStatus (UNDER/WARNING/EXCEEDED), BudgetAlert |
| PaymentType Enum | [`Expense.kt`](../android/app/src/main/java/com/pesatrack/domain/models/Expense.kt:32) | 9 values: SEND_MONEY, BUY_GOODS, PAY_BILL, WITHDRAW, AIRTIME, MPESA_CARD, TRANSACTION_COST, BANK_DEBIT, CASH |
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
| `CASH` | Cash | Manual entry only (no SMS pattern) |

**Legacy backward compatibility:** `fromString()` maps old values `"REVERSAL"`, `"RECEIVE_MONEY"`, `"DEPOSIT"` to `SEND_MONEY` for existing DB records.

---

### 4. Android App — Presentation Layer

#### ✅ Implemented

| Feature | File | Description |
|---------|------|-------------|
| **Navigation** | | |
| Nav Graph | [`NavGraph.kt`](../android/app/src/main/java/com/pesatrack/presentation/navigation/NavGraph.kt:17) | 12 routes: Home, Analytics, Expenses, Categorize, Import, ExcelImport, BatchCategorize, Settings, ManualEntry, Budget, CategoryManagement, About |
| Screen Routes | [`Screen.kt`](../android/app/src/main/java/com/pesatrack/presentation/navigation/Screen.kt:6) | Sealed class with route definitions |
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
| SettingsScreen | [`SettingsScreen.kt`](../android/app/src/main/java/com/pesatrack/presentation/screens/settings/SettingsScreen.kt:1) | Security (PIN toggle, change PIN, biometric, timeout) + Category management + Budget management + Bank SMS tracking toggles |
| SettingsViewModel | [`SettingsViewModel.kt`](../android/app/src/main/java/com/pesatrack/presentation/screens/settings/SettingsViewModel.kt:1) | Bank preferences + PIN/biometric preferences management |
| SettingsUiState | [`SettingsUiState.kt`](../android/app/src/main/java/com/pesatrack/presentation/screens/settings/SettingsUiState.kt:1) | BankToggle + PIN/biometric/timeout state |
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

| **Manual Entry Screen** | | |
| ManualEntryScreen | [`ManualEntryScreen.kt`](../android/app/src/main/java/com/pesatrack/presentation/screens/manual_entry/ManualEntryScreen.kt:26) | Form: amount, recipient, name, payment type, date picker, category picker, notes |
| ManualEntryViewModel | [`ManualEntryViewModel.kt`](../android/app/src/main/java/com/pesatrack/presentation/screens/manual_entry/ManualEntryViewModel.kt:20) | Validation, save with recipient mapping |
| ManualEntryUiState | [`ManualEntryUiState.kt`](../android/app/src/main/java/com/pesatrack/presentation/screens/manual_entry/ManualEntryUiState.kt:8) | Form fields, validation errors, save state |

| **Analytics Screen** | | |
| AnalyticsScreen | [`AnalyticsScreen.kt`](../android/app/src/main/java/com/pesatrack/presentation/screens/analytics/AnalyticsScreen.kt:1) | **Tab-based Monthly/Yearly view**: Month selector, MoM comparison, trend line, **variable-spend category trends**, daily columns, category bars, top spenders, payment type breakdown (Vico charts); **Yearly tab**: year selector, YoY card, 12-month overlay chart, yearly breakdowns; **Budget setup banner** when no budgets exist |
| AnalyticsViewModel | [`AnalyticsViewModel.kt`](../android/app/src/main/java/com/pesatrack/presentation/screens/analytics/AnalyticsViewModel.kt:1) | Analytics data loading, month/year navigation, MoM/YoY computation, **CV-based volatile category detection**, yearly data lazy loading, budget status check |
| AnalyticsUiState | [`AnalyticsUiState.kt`](../android/app/src/main/java/com/pesatrack/presentation/screens/analytics/AnalyticsUiState.kt:1) | Charts data, summary stats, month/year selection, **categoryTrends**, **AnalyticsTab** (MONTHLY/YEARLY), yearly state fields, hasActiveBudgets |
| AnalyticsModels | [`AnalyticsModels.kt`](../android/app/src/main/java/com/pesatrack/domain/models/AnalyticsModels.kt:1) | MonthComparison, **YearComparison**, **CategoryTrend** (CV, mean, σ, spend level), **DEFAULT_VARIABLE_SPEND_CATEGORIES** (12 IDs) |

| **Budget Screen** | | |
| BudgetScreen | [`BudgetScreen.kt`](../android/app/src/main/java/com/pesatrack/presentation/screens/budget/BudgetScreen.kt:1) | Full CRUD: budget list with progress bars, FAB to add, edit/delete, color-coded progress (green/amber/red), hierarchical category picker (group + sub-category) |
| BudgetViewModel | [`BudgetViewModel.kt`](../android/app/src/main/java/com/pesatrack/presentation/screens/budget/BudgetViewModel.kt:1) | Loads budgets with progress, add/edit/delete, hierarchical category loading (groups + sub-categories) |
| BudgetUiState | [`BudgetUiState.kt`](../android/app/src/main/java/com/pesatrack/presentation/screens/budget/BudgetUiState.kt:1) | Budget progress list, hierarchical category options (BudgetCategoryOption), dialog state for add/edit |

---

### 5. Notification System

#### ✅ Implemented

| Feature | File | Description |
|---------|------|-------------|
| Notification Helper | [`NotificationHelper.kt`](../android/app/src/main/java/com/pesatrack/services/NotificationHelper.kt:19) | Channel creation + expense alerts + budget alerts |
| Expense Notification Channel | [`NotificationHelper.kt`](../android/app/src/main/java/com/pesatrack/services/NotificationHelper.kt:29) | "Expense Notifications" channel (Android 8+) |
| Budget Alert Channel | [`NotificationHelper.kt`](../android/app/src/main/java/com/pesatrack/services/NotificationHelper.kt:104) | "Budget Alerts" channel — high importance when exceeded |
| Expense Notification | [`NotificationHelper.kt`](../android/app/src/main/java/com/pesatrack/services/NotificationHelper.kt:54) | Shows "New Expense: KES X,XXX.XX" + "To recipient" |
| Budget Alert Notification | [`NotificationHelper.kt`](../android/app/src/main/java/com/pesatrack/services/NotificationHelper.kt:132) | Shows "⚠️ Budget Warning" at 80% / "🚨 Budget Exceeded" at 100% with progress details |
| Tap-to-Categorize | [`NotificationHelper.kt`](../android/app/src/main/java/com/pesatrack/services/NotificationHelper.kt:64) | PendingIntent opens categorize screen |
| Channel Init on Launch | [`MainActivity.kt`](../android/app/src/main/java/com/pesatrack/presentation/MainActivity.kt:51) | Created in `onCreate()` |

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
| 5 | Faith & Giving | Church Program, Give, Offering, **Seed**, Tithe |
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

---

## Current File Structure

### Android App

```
app/src/main/java/com/pesatrack/
├── PesaTrackApp.kt                          ✅ Hilt Application class + ProcessLifecycleOwner (PIN lock)
├── di/
│   └── AppModule.kt                         ✅ Database, DAOs (incl. BudgetDao, CategoryRuleDao)
├── data/
│   ├── local/
│   │   ├── database/
│   │   │   ├── PesaTrackDatabase.kt         ✅ Room v12 with migrations (v11→v12: sub-category budgets)
│   │   │   ├── dao/
│   │   │   │   ├── ExpenseDao.kt            ✅ CRUD + month queries + duplicate check + budget spending queries
│   │   │   │   ├── CategoryDao.kt           ✅ CRUD + search + default seeding + expense count queries + group mgmt
│   │   │   │   ├── CategoryRuleDao.kt       ✅ Rule CRUD + active rules query for categorization pipeline
│   │   │   │   └── BudgetDao.kt             ✅ Budget CRUD + active queries + affected budget lookups
│   │   │   └── entities/
│   │   │       ├── ExpenseEntity.kt          ✅ Full schema with FK to categories
│   │   │       ├── CategoryEntity.kt         ✅ 18 groups, 90+ categories
│   │   │       ├── CategoryRuleEntity.kt     ✅ User-defined auto-categorization rules (pattern, matchType, priority)
│   │   │       └── BudgetEntity.kt           ✅ Budget limits per group/sub-category/total with period + isActive + isGroupBudget
│   │   └── preferences/
│   │       └── AppPreferences.kt            ✅ DataStore (phone number, bank prefs, budget prompt, PIN lock settings)
│   └── repository/
│       ├── ExpenseRepository.kt             ✅ Domain mapping, CRUD
│       ├── CategoryRepository.kt            ✅ Category CRUD (add/edit/delete groups + sub-categories), expense count checks
│       ├── CategoryRuleRepository.kt        ✅ Rule CRUD, active rules for categorization pipeline
│       └── BudgetRepository.kt              ✅ Budget CRUD, period ranges, spending aggregation (total/group/sub-category), progress/alerts
├── domain/models/
│   ├── Expense.kt                           ✅ PaymentType (8) + ExpenseSource (5)
│   ├── Category.kt                          ✅ Domain model
│   ├── Budget.kt                            ✅ Budget, BudgetPeriod, BudgetProgress, BudgetStatus, BudgetAlert
│   └── AnalyticsModels.kt                   ✅ MonthComparison + CategoryTrend + DEFAULT_VARIABLE_SPEND_CATEGORIES
├── presentation/
│   ├── MainActivity.kt                      ✅ Onboarding → PIN lock → main app; BiometricPrompt; 3-tab bottom nav
│   ├── navigation/
│   │   ├── NavGraph.kt                      ✅ 12 routes: Home, Analytics, Expenses, Categorize, Import, ExcelImport, BatchCategorize, Settings, ManualEntry, Budget, CategoryManagement, PinSetup
│   │   └── Screen.kt                        ✅ Sealed class + BottomNavItem enum (3 tabs) + PinSetup route
│   ├── screens/
│   │   ├── home/
│   │   │   ├── HomeScreen.kt                ✅ Monthly summary (with investment % breakdown) + mini trend chart + recent expenses + budget summary/prompt cards
│   │   │   ├── HomeViewModel.kt             ✅ Category-aware state + trend data + investment total + budget progress + prompt logic
│   │   │   └── HomeUiState.kt               ✅ ExpenseWithCategory + MonthlyTrend + MonthComparison + investmentThisMonth + budget fields
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
│   │   ├── manual_entry/
│   │   │   ├── ManualEntryScreen.kt       ✅ Manual expense form with validation
│   │   │   ├── ManualEntryViewModel.kt    ✅ Save + recipient mapping
│   │   │   └── ManualEntryUiState.kt      ✅ Form state model
│   │   ├── analytics/
│   │   │   ├── AnalyticsScreen.kt         ✅ Full analytics + budget setup banner (when no budgets)
│   │   │   ├── AnalyticsViewModel.kt      ✅ Data loading, month nav, MoM computation, CV-based trends, budget status
│   │   │   └── AnalyticsUiState.kt        ✅ Charts data + summary stats + categoryTrends + hasActiveBudgets
│   │   ├── budget/
│   │   │   ├── BudgetScreen.kt            ✅ Budget CRUD: list with progress bars, FAB, add/edit/delete, hierarchical category picker
│   │   │   ├── BudgetViewModel.kt         ✅ Budget progress loading, CRUD ops, hierarchical category picker (groups + sub-categories)
│   │   │   └── BudgetUiState.kt           ✅ Progress list, BudgetCategoryOption (hierarchical), dialog state
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
│   │   └── settings/
│   │       ├── SettingsScreen.kt             ✅ Security (PIN, biometric, timeout) + Category mgmt + Budget mgmt + Bank SMS toggles
│   │       ├── SettingsViewModel.kt          ✅ Bank + PIN/biometric preferences management
│   │       └── SettingsUiState.kt            ✅ BankToggle + PIN/biometric/timeout state
│   ├── components/
│   │   ├── ExpenseCard.kt                   ✅ Payment type icons, category title
│   │   ├── CategoryChip.kt                  ✅ Selection chip
│   │   └── GroupedCategoryPicker.kt         ✅ Hierarchical selector
│   └── theme/
│       ├── Theme.kt                         ✅ Material 3
│       ├── Color.kt                         ✅ getCategoryColor()
│       └── Type.kt                          ✅ Typography
├── services/
│   ├── SmsReceiver.kt                       ✅ Multi-source BroadcastReceiver + budget alert check after save
│   ├── SmsImportService.kt                  ✅ Multi-source historical import
│   ├── ExcelImportService.kt                ✅ Excel import orchestration (match + standalone)
│   ├── AiCategorizationService.kt           ✅ CategorizationService — two-pass: user rules first, then built-in engine
│   ├── KeywordRulesEngine.kt                ✅ 100+ business names, keyword rules, PaymentType heuristics
│   ├── BudgetService.kt                     ✅ Budget threshold checking after expense save, notification dispatch
│   ├── NotificationHelper.kt               ✅ Expense channel + Budget Alerts channel (80%/100% thresholds)
│   ├── PinManager.kt                        ✅ SHA-256 + salt PIN hashing, verification, timeout logic
│   └── AppLockLifecycleObserver.kt          ✅ ProcessLifecycleOwner observer — background/foreground lock management
└── utils/
    ├── SmsParser.kt                         ✅ Backward-compat facade → SmsParserRegistry
    ├── Constants.kt                         ✅ formatAsCurrency()
    ├── excel/
    │   ├── ExcelParser.kt                   ✅ Apache POI .xlsx parser (dual date formats)
    │   └── ExcelCategoryMapper.kt           ✅ 55+ label→category ID mappings
    └── parsers/
        ├── SmsParserStrategy.kt             ✅ Strategy interface for SMS parsers
        ├── SmsParserRegistry.kt             ✅ Central dispatcher (sender → parser)
        ├── MpesaSmsParser.kt                ✅ M-PESA parser (8 expense types)
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

### Completed Bug Fixes

1. **`PaymentType.fromString()`** — Was only matching display names ("Send Money") but DB stores enum names ("SEND_MONEY"). Fixed to try `valueOf()` first, then fall back to display name matching.
2. **Seed Category** — Moved from Shopping (id=506) to Faith & Giving (id=905) with DB migration 2→3.
3. **ExpenseCard Title** — Changed from showing phone number to: category name → recipient name → recipient (priority order). Notes deliberately excluded (can be lengthy).
4. **SMS Parser coverage** — Expanded from 3 types (Send Money, Buy Goods, Pay Bill) to 7 types (+ Withdraw, Airtime, M-PESA Card, Fuliza).
5. **Non-expense filtering** — Added skip logic for Receive Money, Deposit, and Reversal SMS (not expenses).
6. **Transaction cost tracking** — Auto-extracted from SMS and saved as separate expense under category 811 with `isCategorized = true`.
7. **Icons.AutoMirrored.Filled.Send** — Not available in compose icons version; fixed with `@Suppress("DEPRECATION") Icons.Filled.Send`.
8. **NCBA Paybill regex (Format B)** — Original regex expected a standalone paybill number (digits) before "account" (e.g. `"to CHURCH 87 account number Offering"`). Failed on NCBA SMS where the business name directly precedes "account number" with no separate paybill number (e.g. `"to Lipa na KCB account number 7575077"`). Fixed by splitting into `paybillPatternA` (with paybill number) and `paybillPatternB` (without), tried in order of specificity.

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
9. **Investment % on Home Screen** — MonthlySummaryCard now shows a muted secondary line with the investment total and percentage (e.g. "📈 KES 50,000 (42%) invested") when any expenses are categorized under Investment & Savings (group 18). Uses a dedicated DAO query joining expenses with categories where `parentId = 18`. Displayed at `alpha = 0.5` to keep focus on the main expense total.
10. **Custom Categories & Auto-Rules (M8)** — Full category management UI: add/edit/delete custom groups and sub-categories with icon/color pickers; user-defined auto-categorization rules (EXACT/CONTAINS/STARTS_WITH match types) checked before built-in KeywordRulesEngine; DB migration v9→v10 adds `category_rules` table; Settings entry point "Manage Categories" with tab-based screen (Categories + Auto-Rules); default categories protected from deletion; categories with expenses cannot be deleted.
11. **Sub-category Budgets (M7 enhancement)** — Extended budgets from group-level to sub-category-level. Three tiers: Total Spending, Group (e.g. "Food & Dining ≤ 15K"), Sub-category (e.g. "Eating Out ≤ 5K"). DB migration v11→v12 renames `categoryGroupId`→`categoryId` and adds `isGroupBudget` column. Both group and sub-category budgets are tracked independently — an eating-out expense counts toward both "Food & Dining" and "Eating Out" budgets. Hierarchical category picker in add/edit dialog shows groups (bold) and indented sub-categories. Budget alerts fire for whichever threshold is reached first.
12. **Multi-Select Batch Categorize** — Long-press any recipient group on BatchCategorizeScreen to enter selection mode. Checkboxes appear on all cards; tap to select multiple groups across different recipients. "Select All" / "Deselect All" toggle in the top bar. Bottom "Categorize Selected (N)" button opens the category picker — applies the chosen category to ALL expenses from ALL selected groups in one action. Saves recipient→category mappings for future auto-categorization. Back press exits selection mode. Coexists with existing single-group, review, and auto-suggest modes.
13. **PIN Lock + Biometric Unlock** — App-level security with 4-digit PIN. PIN stored as SHA-256 + random salt in DataStore (never plaintext). Compose overlay in MainActivity blocks access when locked. Optional biometric (fingerprint/face) via `BiometricPrompt` — auto-launches on unlock screen, falls back to PIN. Lock triggers on cold start and after configurable background timeout (immediate/30s/1min/5min). Brute force protection: 5 wrong attempts → 30-second cooldown. Settings section: PIN enable/disable (verify current PIN first), change PIN (verify → enter → confirm), biometric toggle (only shown when device supports it), timeout picker. `ProcessLifecycleOwner` tracks background/foreground transitions via `AppLockLifecycleObserver`. No PIN recovery by design (clear app data to reset).
14. **First-Launch Onboarding Flow** — 4-page HorizontalPager shown once on first install (tracked via `KEY_ONBOARDING_COMPLETED` in DataStore). Pages: (1) Welcome — what the app does, (2) How It Works — SMS parsing explained, (3) SMS Permission — contextual permission grant with ✅ confirmation, (4) Import History — offer to import past M-PESA SMS. Dot indicators, Back/Next/Skip buttons, "Get Started" on final page. SMS permission requested in-context via `ActivityResultContracts.RequestMultiplePermissions()`. Notification permission requested on completion. Onboarding overlay in `MainActivity.AppEntryPoint()` — same pattern as PIN lock (full-screen, no bypass). Existing users see onboarding once (DataStore default is `false`).

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
| **M4** | Manual expense entry screen | ✅ Complete | Form: amount, recipient, payment type, date, category, notes; saves with recipient mapping |
| **M5** | Settings & Configuration | ✅ Complete | Bank toggles (M2); Category management (M8); About screen; Privacy policy (GitHub Pages); Data management (export CSV + reset categories); First-launch onboarding flow |
| — | Expense charts and analytics | ✅ Complete | Vico charts: monthly trend, **variable-spend category trends (CV detection, ≥3 months, KES 100 min)**, daily spending, category breakdown, top spenders, payment type breakdown, MoM comparison |
| — | Year-over-Year analytics | ✅ Complete | Tab-based Monthly/Yearly view: annual total card, YoY % change, 12-month overlay chart (this year vs last year), yearly category breakdown, top recipients by year, payment type breakdown by year |
| — | Monthly/weekly summaries | ✅ Complete | Month selector + daily/monthly aggregation in analytics |
| **M6** | Investment Category Deep-Dive | ✅ Complete | New group 18 "Investment & Savings" (13 sub-categories); Financial trimmed to expense-only (6 sub-categories); DB migration v7→v8 remaps IDs; KeywordRulesEngine + ExcelCategoryMapper updated with Kenyan investment paybills |
| **M7** | Category & Sub-Category Budgets | ✅ Complete | Group-level + sub-category-level + total budgets (weekly/monthly/yearly); DB v8→v9 (budgets table), v11→v12 (sub-category support: renamed categoryGroupId→categoryId, added isGroupBudget); BudgetScreen CRUD with hierarchical category picker; Budget Alerts at 80%/100% for all levels; Home budget summary + data-driven prompt; Analytics setup banner; Settings entry point |
| **M8** | Custom Categories & Auto-Rules | ✅ Complete | Custom groups + sub-categories CRUD with icon/color pickers; user-defined auto-categorization rules (EXACT/CONTAINS/STARTS_WITH); DB v9→v10 (category_rules table); rules integrated into CategorizationService (checked before built-in engine); Settings entry point |
| — | Forecasting | ⏳ Deferred | Budget burn rate projections — [plan](../plans/forecasting-plan.md). Revisit after budget adoption validated. |
| — | Export to CSV | ⏳ Pending | Shareable reports |
| — | Cloud sync | ⏳ Pending | Backup/restore across devices |
| — | Recurring expense tracking | ⏳ Pending | Detect repeating patterns |

---

## Next Steps (Recommended)

### High Priority
- [x] Test on a real Android device with actual M-PESA + NCBA SMS messages
- [x] Fix any parsing bugs discovered from real-world SMS formats
- [x] Pre-release cleanup (removed unused permissions, dead code, configured signing) — see [`plans/signed-apk-playstore-plan.md`](../plans/signed-apk-playstore-plan.md)
- [ ] Generate signed AAB for Play Store distribution
- [ ] Submit to Google Play Store (SMS Permission Declaration required) — see [`plans/signed-apk-playstore-plan.md`](../plans/signed-apk-playstore-plan.md)

### Medium Priority — Phase 2 Milestone 5
- [x] About section (app version, privacy policy link, contact, data practices summary)
- [x] Privacy policy page (GitHub Pages: `docs/privacy-policy.html`)
- [x] Data management — Export all expenses as CSV (via share sheet) + Reset categories to defaults (removes custom categories & rules)
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
- [ ] Export to CSV
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

## Brainstorm: Forecasting (Deferred)

> **Status:** Deferred — revisit after budgets are tested with real users.
> **Full plan:** [`plans/forecasting-plan.md`](../plans/forecasting-plan.md)

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

### Trigger to Revisit

- Real users have used budgets for ≥1 month
- OR recurring expense detection is in progress
