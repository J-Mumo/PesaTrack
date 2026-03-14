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
| **Room Database (v3)** | ✅ Complete | 100% |
| **Category System (17 groups)** | ✅ Complete | 100% |
| **Expense Management UI** | ✅ Complete | 100% |
| **Notifications** | ✅ Complete | 100% |
| **Runtime Permissions** | ✅ Complete | 100% |
| **Backend Server (unused)** | 🟡 Dormant | N/A |
| **Phase 2 M1: Historical SMS Import + Recipient Learning** | ✅ Complete | 100% |
| **Phase 2 M2: Bank SMS Tracking (NCBA)** | ✅ Complete | 100% |
| **Phase 2 M3: AI-Powered Categorization** | ⏳ Pending | 0% |
| **Phase 2 M4: Manual Expense Entry** | ⏳ Pending | 0% |
| **Phase 2 M5: Settings & Configuration** | ⏳ Pending | 0% |

---

## System Architecture (Current)

```
SMS Sources ──────────────────────────────────────────────────────────────────
│                                                                            │
│  M-PESA SMS ──► SmsReceiver ──► SmsParserRegistry ──► MpesaSmsParser       │
│  NCBA  SMS ──► SmsReceiver ──► SmsParserRegistry ──► NcbaBankParser        │
│  Historical ──► SmsImportService ──► SmsParserRegistry                     │
│                                        │                                   │
│                                  ParsedTransaction                         │
│                                   ├── expense (main)                       │
│                                   └── transactionCost (optional)           │
│                                        │                                   │
│                         Auto-Categorization                                │
│                          ├── Deterministic rules                           │
│                          └── Recipient mapping                             │
│                                        │                                   │
│                               ExpenseRepository                            │
│                                        │                                   │
│                                   Room Database                            │
│                                        │                                   │
│                    ┌───────────┬────────┼──────────┬──────────┐             │
│               HomeScreen  ExpenseList  Categorize  Batch   Settings        │
──────────────────────────────────────────────────────────────────────────────
```

**Key design decisions:**
- No backend communication — all data is local (Room + DataStore)
- SMS parsing is the sole source of expense data (M-PESA + bank SMS, plus future manual entry)
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
| NCBA Paybill | [`NcbaBankParser.kt`](../android/app/src/main/java/com/pesatrack/utils/parsers/NcbaBankParser.kt:82) | Pattern: `"Mpesa Paybill transfer of KES to"` |
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
| Database Setup | [`PesaTrackDatabase.kt`](../android/app/src/main/java/com/pesatrack/data/local/database/PesaTrackDatabase.kt:16) | Version 7 with migrations |
| Migration 2→3 | [`PesaTrackDatabase.kt`](../android/app/src/main/java/com/pesatrack/data/local/database/PesaTrackDatabase.kt:34) | Moved Seed category to Faith & Giving |
| Migration 6→7 | [`PesaTrackDatabase.kt`](../android/app/src/main/java/com/pesatrack/data/local/database/PesaTrackDatabase.kt:469) | Added `isExcluded` column to expenses |
| Expense Entity | [`ExpenseEntity.kt`](../android/app/src/main/java/com/pesatrack/data/local/database/entities/ExpenseEntity.kt:11) | Full schema with FK to categories + isExcluded flag |
| Category Entity | [`CategoryEntity.kt`](../android/app/src/main/java/com/pesatrack/data/local/database/entities/CategoryEntity.kt:12) | Hierarchical categories with parent-child |
| Default Categories | [`CategoryEntity.kt`](../android/app/src/main/java/com/pesatrack/data/local/database/entities/CategoryEntity.kt:57) | 17 groups, 80+ sub-categories |
| Expense DAO | [`ExpenseDao.kt`](../android/app/src/main/java/com/pesatrack/data/local/database/dao/ExpenseDao.kt:10) | CRUD + month queries + duplicate check |
| Category DAO | [`CategoryDao.kt`](../android/app/src/main/java/com/pesatrack/data/local/database/dao/CategoryDao.kt:11) | CRUD + search + default seeding |
| **Preferences** | | |
| AppPreferences | [`AppPreferences.kt`](../android/app/src/main/java/com/pesatrack/data/local/preferences/AppPreferences.kt:1) | DataStore for phone number persistence |
| **Repositories** | | |
| Expense Repository | [`ExpenseRepository.kt`](../android/app/src/main/java/com/pesatrack/data/repository/ExpenseRepository.kt:17) | CRUD, month range, domain mapping |
| Category Repository | [`CategoryRepository.kt`](../android/app/src/main/java/com/pesatrack/data/repository/CategoryRepository.kt:18) | Category management, default init |
| **Dependency Injection** | | |
| Hilt App Module | [`AppModule.kt`](../android/app/src/main/java/com/pesatrack/di/AppModule.kt:19) | Database, DAOs, TelephonyManager |

---

### 3. Android App — Domain Layer

#### ✅ Implemented

| Feature | File | Description |
|---------|------|-------------|
| Expense Model | [`Expense.kt`](../android/app/src/main/java/com/pesatrack/domain/models/Expense.kt:6) | Domain model with `isCategorized` + `isExcluded` flags |
| Category Model | [`Category.kt`](../android/app/src/main/java/com/pesatrack/domain/models/Category.kt:1) | Domain model |
| PaymentType Enum | [`Expense.kt`](../android/app/src/main/java/com/pesatrack/domain/models/Expense.kt:30) | 8 values: SEND_MONEY, BUY_GOODS, PAY_BILL, WITHDRAW, AIRTIME, MPESA_CARD, TRANSACTION_COST, BANK_DEBIT |
| ExpenseSource Enum | [`Expense.kt`](../android/app/src/main/java/com/pesatrack/domain/models/Expense.kt:81) | STK_PUSH (legacy), SMS_PARSED, SMS_BANK, MANUAL |

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

**Legacy backward compatibility:** `fromString()` maps old values `"REVERSAL"`, `"RECEIVE_MONEY"`, `"DEPOSIT"` to `SEND_MONEY` for existing DB records.

---

### 4. Android App — Presentation Layer

#### ✅ Implemented

| Feature | File | Description |
|---------|------|-------------|
| **Navigation** | | |
| Nav Graph | [`NavGraph.kt`](../android/app/src/main/java/com/pesatrack/presentation/navigation/NavGraph.kt:17) | 6 routes: Home, Expenses, Categorize, Import, BatchCategorize, Settings |
| Screen Routes | [`Screen.kt`](../android/app/src/main/java/com/pesatrack/presentation/navigation/Screen.kt:6) | Sealed class with route definitions |
| Bottom Nav | [`Screen.kt`](../android/app/src/main/java/com/pesatrack/presentation/navigation/Screen.kt:17) | 2 tabs: Home, Expenses |
| **Main Activity** | | |
| MainActivity | [`MainActivity.kt`](../android/app/src/main/java/com/pesatrack/presentation/MainActivity.kt:32) | Runtime permissions, notification channel, bottom nav scaffold |
| MainScreen | [`MainActivity.kt`](../android/app/src/main/java/com/pesatrack/presentation/MainActivity.kt:108) | Scaffold with NavigationBar + NavGraph |
| **Home Screen** | | |
| HomeScreen | [`HomeScreen.kt`](../android/app/src/main/java/com/pesatrack/presentation/screens/home/HomeScreen.kt:24) | Monthly summary, recent expenses, uncategorized alert |
| HomeViewModel | [`HomeViewModel.kt`](../android/app/src/main/java/com/pesatrack/presentation/screens/home/HomeViewModel.kt:15) | Category-aware state management, default category init |
| HomeUiState | [`HomeUiState.kt`](../android/app/src/main/java/com/pesatrack/presentation/screens/home/HomeUiState.kt:1) | Uses `ExpenseWithCategory` for rich display |
| **Expenses Screen** | | |
| ExpenseListScreen | [`ExpenseListScreen.kt`](../android/app/src/main/java/com/pesatrack/presentation/screens/expenses/ExpenseListScreen.kt:1) | Full expense history with category colours |
| ExpensesViewModel | [`ExpensesViewModel.kt`](../android/app/src/main/java/com/pesatrack/presentation/screens/expenses/ExpensesViewModel.kt:1) | Category mapping for display |
| ExpensesUiState | [`ExpensesUiState.kt`](../android/app/src/main/java/com/pesatrack/presentation/screens/expenses/ExpensesUiState.kt:1) | `ExpenseWithCategory` data class |
| **Categorize Screen** | | |
| CategorizeScreen | [`CategorizeScreen.kt`](../android/app/src/main/java/com/pesatrack/presentation/screens/categorize/CategorizeScreen.kt:1) | Category assignment with grouped picker |
| CategorizeViewModel | [`CategorizeViewModel.kt`](../android/app/src/main/java/com/pesatrack/presentation/screens/categorize/CategorizeViewModel.kt:1) | State management |
| CategorizeUiState | [`CategorizeUiState.kt`](../android/app/src/main/java/com/pesatrack/presentation/screens/categorize/CategorizeUiState.kt:1) | UI state model |
| **Components** | | |
| ExpenseCard | [`ExpenseCard.kt`](../android/app/src/main/java/com/pesatrack/presentation/components/ExpenseCard.kt:31) | Category name as title, payment type icons, colour-coded, long-press exclude toggle |
| CategoryChip | [`CategoryChip.kt`](../android/app/src/main/java/com/pesatrack/presentation/components/CategoryChip.kt:1) | Category selection chip |
| GroupedCategoryPicker | [`GroupedCategoryPicker.kt`](../android/app/src/main/java/com/pesatrack/presentation/components/GroupedCategoryPicker.kt:1) | Hierarchical category selector |
| **Theme** | | |
| Theme | [`Theme.kt`](../android/app/src/main/java/com/pesatrack/presentation/theme/Theme.kt:1) | Material 3 theming |
| Colors | [`Color.kt`](../android/app/src/main/java/com/pesatrack/presentation/theme/Color.kt:1) | Colour palette with `getCategoryColor()` |
| Typography | [`Type.kt`](../android/app/src/main/java/com/pesatrack/presentation/theme/Type.kt:1) | Typography definitions |

| **Settings Screen** | | |
| SettingsScreen | [`SettingsScreen.kt`](../android/app/src/main/java/com/pesatrack/presentation/screens/settings/SettingsScreen.kt:1) | Bank SMS tracking toggles |
| SettingsViewModel | [`SettingsViewModel.kt`](../android/app/src/main/java/com/pesatrack/presentation/screens/settings/SettingsViewModel.kt:1) | Preferences management |
| SettingsUiState | [`SettingsUiState.kt`](../android/app/src/main/java/com/pesatrack/presentation/screens/settings/SettingsUiState.kt:1) | BankToggle data model |

#### ⏳ Pending

| Feature | Priority | Notes |
|---------|----------|-------|
| Manual Expense Entry Screen | Medium | No UI to manually add expenses yet |

---

### 5. Notification System

#### ✅ Implemented

| Feature | File | Description |
|---------|------|-------------|
| Notification Helper | [`NotificationHelper.kt`](../android/app/src/main/java/com/pesatrack/services/NotificationHelper.kt:19) | Channel creation + expense alerts |
| Notification Channel | [`NotificationHelper.kt`](../android/app/src/main/java/com/pesatrack/services/NotificationHelper.kt:29) | "Expense Notifications" channel (Android 8+) |
| Expense Notification | [`NotificationHelper.kt`](../android/app/src/main/java/com/pesatrack/services/NotificationHelper.kt:54) | Shows "New Expense: KES X,XXX.XX" + "To recipient" |
| Tap-to-Categorize | [`NotificationHelper.kt`](../android/app/src/main/java/com/pesatrack/services/NotificationHelper.kt:64) | PendingIntent opens categorize screen |
| Channel Init on Launch | [`MainActivity.kt`](../android/app/src/main/java/com/pesatrack/presentation/MainActivity.kt:51) | Created in `onCreate()` |

---

### 6. Utilities

#### ✅ Implemented

| Feature | File | Description |
|---------|------|-------------|
| Constants | [`Constants.kt`](../android/app/src/main/java/com/pesatrack/utils/Constants.kt:6) | `formatAsCurrency()` extension |
| Phone Number Helper | [`PhoneNumberHelper.kt`](../android/app/src/main/java/com/pesatrack/utils/PhoneNumberHelper.kt:1) | SIM number reading, Kenyan phone normalization |

---

### 7. Default Categories

#### ✅ Implemented

17 category groups with hierarchical sub-categories defined in [`DefaultCategories`](../android/app/src/main/java/com/pesatrack/data/local/database/entities/CategoryEntity.kt:57):

| ID | Group Name | Sub-categories |
|----|-----------|----------------|
| 1 | Vehicle & Fuel | Fuel, Car Service, Parking, Insurance, Toll, Car Wash |
| 2 | Home & Utilities | Rent, Electricity, Water, Gas, Internet, House Help |
| 3 | Food & Dining | Groceries, Restaurant, Snacks, Drinks |
| 4 | Transport | Uber/Taxi, Matatu/Bus, Boda Boda, Train, Parking |
| 5 | Shopping | Shopping, Clothing, Phone/Accessories, Books, Art, Shipping |
| 6 | Personal Care | Barber, Shave, Salon |
| 7 | Health | Medical Checkup, Pharmacy, Dental, Optical, Gym, Insurance |
| 8 | Financial | Savings, Invest, Loan, SACCO, NSSF, Bank Charges, **Mpesa Transaction Cost** (811) |
| 9 | Faith & Giving | Tithe, Offering, Give, Heaven's Gate, **Seed** |
| 10 | Digital & Tech | **Airtime** (1001), Internet Bundles, Domain, Hosting, etc. |
| 11 | Beekeeping | Hive Gear, Queen Cells, Feeds, Harvest |
| 12 | Pets | Pet Food, Vet, Grooming |
| 13 | Entertainment | Movies, Events, Games, Streaming |
| 14 | Education | Tuition, Books, Online Course |
| 15 | Government | KRA Tax, NHIF, County Fees, Stamps, Court Fees, Huduma |
| 16 | Life Events | Wedding, Funeral, Baby Shower, Dowry, Birthday |
| 17 | Miscellaneous | Gift, Donation, Other |

**Special auto-categorized categories:**
- **Category 811** ("Mpesa Transaction Cost") — Transaction costs are auto-saved here with `isCategorized = true`

---

### 8. Android Configuration

#### ✅ Implemented

| Feature | File | Description |
|---------|------|-------------|
| Manifest | [`AndroidManifest.xml`](../android/app/src/main/AndroidManifest.xml:1) | All permissions declared |
| SMS Permissions | [`AndroidManifest.xml`](../android/app/src/main/AndroidManifest.xml:10) | `READ_SMS`, `RECEIVE_SMS` |
| Phone Permissions | [`AndroidManifest.xml`](../android/app/src/main/AndroidManifest.xml:14) | `READ_PHONE_STATE`, `READ_PHONE_NUMBERS` |
| Contact Permission | [`AndroidManifest.xml`](../android/app/src/main/AndroidManifest.xml:18) | `READ_CONTACTS` |
| Notification Permission | [`AndroidManifest.xml`](../android/app/src/main/AndroidManifest.xml:21) | `POST_NOTIFICATIONS` (Android 13+) |
| SMS BroadcastReceiver | [`AndroidManifest.xml`](../android/app/src/main/AndroidManifest.xml:47) | Priority 999, `BROADCAST_SMS` permission |
| Runtime Permissions | [`MainActivity.kt`](../android/app/src/main/java/com/pesatrack/presentation/MainActivity.kt:69) | Requests all permissions on startup |
| Gradle Build | [`build.gradle.kts`](../android/app/build.gradle.kts:1) | compileSdk 34, minSdk 26, Kotlin 17 |
| Build Config | [`build.gradle.kts`](../android/app/build.gradle.kts:26) | Release minify + shrink enabled |
| ProGuard Rules | [`proguard-rules.pro`](../android/app/proguard-rules.pro:1) | Code obfuscation rules |

**Dependencies (no networking libraries):**
- Jetpack Compose (BOM 2024.02.00) + Material 3
- Navigation Compose 2.7.6
- Hilt 2.50 (DI)
- Room 2.6.1 (database)
- DataStore 1.0.0 (preferences)
- Coroutines 1.7.3

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
| Quick Action buttons | `HomeScreen.kt` | Send Money/Buy Goods/Pay Bill buttons removed |
| `PaymentType.RECEIVE_MONEY` | `Expense.kt` | Not an expense |
| `PaymentType.DEPOSIT` | `Expense.kt` | Not an expense |
| `PaymentType.REVERSAL` | `Expense.kt` | Not an expense |

---

## Current File Structure

### Android App

```
app/src/main/java/com/pesatrack/
├── PesaTrackApp.kt                          ✅ Hilt Application class
├── di/
│   └── AppModule.kt                         ✅ Database, DAOs, TelephonyManager
├── data/
│   ├── local/
│   │   ├── database/
│   │   │   ├── PesaTrackDatabase.kt         ✅ Room v3 with migrations
│   │   │   ├── dao/
│   │   │   │   ├── ExpenseDao.kt            ✅ CRUD + month queries + duplicate check
│   │   │   │   └── CategoryDao.kt           ✅ CRUD + search + default seeding
│   │   │   └── entities/
│   │   │       ├── ExpenseEntity.kt          ✅ Full schema with FK to categories
│   │   │       └── CategoryEntity.kt         ✅ 17 groups, 80+ categories
│   │   └── preferences/
│   │       └── AppPreferences.kt            ✅ DataStore (phone number)
│   └── repository/
│       ├── ExpenseRepository.kt             ✅ Domain mapping, CRUD
│       └── CategoryRepository.kt            ✅ Category management
├── domain/models/
│   ├── Expense.kt                           ✅ PaymentType (8) + ExpenseSource (4)
│   └── Category.kt                          ✅ Domain model
├── presentation/
│   ├── MainActivity.kt                      ✅ Permissions + Scaffold + bottom nav
│   ├── navigation/
│   │   ├── NavGraph.kt                      ✅ 6 routes: Home, Expenses, Categorize, Import, BatchCategorize, Settings
│   │   └── Screen.kt                        ✅ Sealed class + BottomNavItem enum
│   ├── screens/
│   │   ├── home/
│   │   │   ├── HomeScreen.kt                ✅ Monthly summary + recent expenses
│   │   │   ├── HomeViewModel.kt             ✅ Category-aware state + default init
│   │   │   └── HomeUiState.kt               ✅ ExpenseWithCategory
│   │   ├── expenses/
│   │   │   ├── ExpenseListScreen.kt          ✅ Full expense list
│   │   │   ├── ExpensesViewModel.kt          ✅ Category mapping
│   │   │   └── ExpensesUiState.kt            ✅ ExpenseWithCategory model
│   │   ├── categorize/
│   │   │   ├── CategorizeScreen.kt           ✅ Category assignment
│   │   │   ├── CategorizeViewModel.kt        ✅ State management
│   │   │   └── CategorizeUiState.kt          ✅ UI state
│   │   └── settings/
│   │       ├── SettingsScreen.kt             ✅ Bank SMS tracking toggles
│   │       ├── SettingsViewModel.kt          ✅ Preferences management
│   │       └── SettingsUiState.kt            ✅ BankToggle model
│   ├── components/
│   │   ├── ExpenseCard.kt                   ✅ Payment type icons, category title
│   │   ├── CategoryChip.kt                  ✅ Selection chip
│   │   └── GroupedCategoryPicker.kt         ✅ Hierarchical selector
│   └── theme/
│       ├── Theme.kt                         ✅ Material 3
│       ├── Color.kt                         ✅ getCategoryColor()
│       └── Type.kt                          ✅ Typography
├── services/
│   ├── SmsReceiver.kt                       ✅ Multi-source BroadcastReceiver
│   ├── SmsImportService.kt                  ✅ Multi-source historical import
│   └── NotificationHelper.kt               ✅ Channel + expense alerts
└── utils/
    ├── SmsParser.kt                         ✅ Backward-compat facade → SmsParserRegistry
    ├── Constants.kt                         ✅ formatAsCurrency()
    ├── PhoneNumberHelper.kt                 ✅ SIM number reading
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

### Implemented Features

1. **Notification System** — SMS-parsed expenses trigger notification with amount + recipient; tap opens categorize screen.
2. **Runtime Permissions** — MainActivity requests SMS, phone state, and notification permissions on first launch.
3. **Category-Aware Views** — Home screen and expense list show category name and colour alongside expenses.
4. **Phone Auto-Fill** — SIM number read via TelephonyManager, persisted in DataStore.
5. **Multi-part SMS** — SmsReceiver concatenates multi-part messages before parsing.
6. **Dual expense saving** — Each SMS can produce main expense + transaction cost (both saved).
7. **Duplicate detection** — transactionId checked before saving to avoid double entries.

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
| Runtime permission flow | ✅ Complete | All permissions requested on launch |
| Manual expense entry | ⏳ Pending | No manual add screen yet |
| Historical SMS import | ✅ Complete | ContentResolver-based import with date range picker |
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
| **M3** | AI-Powered Categorization (Gemini) | ⏳ Pending | API-based suggestions |
| **M4** | Manual expense entry screen | ⏳ Pending | Add expense without SMS |
| **M5** | Settings & Configuration | ⏳ Pending | Bank selection, AI prefs |
| — | Expense charts and analytics | ⏳ Pending | Category breakdown, trends |
| — | Monthly/weekly summaries | ⏳ Pending | Time-based grouping |
| — | Category-based budgets | ⏳ Pending | Set spending limits |
| — | Export to CSV | ⏳ Pending | Shareable reports |
| — | Cloud sync | ⏳ Pending | Backup/restore across devices |
| — | Recurring expense tracking | ⏳ Pending | Detect repeating patterns |

---

## Next Steps (Recommended)

### High Priority
- [ ] Test on a real Android device with actual M-PESA + NCBA SMS messages
- [ ] Fix any parsing bugs discovered from real-world SMS formats
- [ ] Generate signed APK for distribution

### Medium Priority — Phase 2 Milestone 3
- [ ] Phase 2 M3: AI-Powered Categorization (Gemini API)
- [ ] Phase 2 M4: Manual expense entry screen

### Lower Priority
- [ ] Add more bank parsers (Equity, KCB, Cooperative, etc.)
- [ ] Expense charts/analytics
- [ ] Export to CSV
- [ ] Clean up unused backend deployment on Railway
