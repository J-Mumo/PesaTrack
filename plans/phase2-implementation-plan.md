# PesaTrack Phase 2 — Implementation Plan

## Overview

Phase 2 expands PesaTrack from an M-PESA-only passive tracker into a comprehensive expense tracking platform with:
1. **Historical SMS Import** — bulk import past M-PESA transactions with smart batch categorization
2. **Bank SMS Tracking** — expand beyond M-PESA to track bank expenses
3. **AI-Powered Categorization** — auto-categorize expenses using recipient learning + AI
4. **Manual Expense Entry** — add expenses that don't come through SMS

---

## Current State (Phase 1 Complete)

```mermaid
flowchart LR
    A["Live M-PESA SMS"] --> B["SmsReceiver"]
    B --> C["SmsParser\n7 expense types"]
    C --> D["Room DB"]
    D --> E["Manual\nCategorization"]
```

**What exists:**
- [`SmsReceiver.kt`](../android/app/src/main/java/com/pesatrack/services/SmsReceiver.kt:24) — BroadcastReceiver for live SMS
- [`SmsParser.kt`](../android/app/src/main/java/com/pesatrack/utils/SmsParser.kt:56) — M-PESA SMS regex parser (7 types)
- [`ExpenseEntity.kt`](../android/app/src/main/java/com/pesatrack/data/local/database/entities/ExpenseEntity.kt:11) — Room entity with transactionId uniqueness
- [`ExpenseDao.kt`](../android/app/src/main/java/com/pesatrack/data/local/database/dao/ExpenseDao.kt:10) — CRUD + duplicate check
- [`ExpenseRepository.kt`](../android/app/src/main/java/com/pesatrack/data/repository/ExpenseRepository.kt:17) — domain mapping
- 17 category groups, 80+ sub-categories in [`CategoryEntity.kt`](../android/app/src/main/java/com/pesatrack/data/local/database/entities/CategoryEntity.kt:57)
- `READ_SMS` permission already declared in [`AndroidManifest.xml`](../android/app/src/main/AndroidManifest.xml:10)
- No networking dependencies (no Retrofit, no OkHttp)

---

## Phase 2 Target Architecture

```mermaid
flowchart TB
    subgraph "SMS Sources"
        A["Live M-PESA SMS\n(BroadcastReceiver)"]
        B["Historical SMS\n(ContentResolver)"]
        C["Live Bank SMS\n(BroadcastReceiver)"]
    end
    
    subgraph "Parser Registry"
        D["SmsParserRegistry"]
        E["MpesaSmsParser"]
        F["EquityBankParser"]
        G["KcbBankParser"]
        H["...more bank parsers"]
    end
    
    subgraph "Smart Categorization"
        I["Rule Engine\n(deterministic)"]
        J["Recipient Mapping\n(learned)"]
        K["AI Categorizer\n(Gemini API)"]
    end
    
    subgraph "Data Layer"
        L["Room DB v4"]
        M["RecipientCategoryMapping"]
        N["ExpenseEntity\n(+ rawSms field)"]
    end
    
    subgraph "UI"
        O["Import Screen\n(date picker + progress)"]
        P["Batch Categorize Screen\n(grouped by recipient)"]
        Q["Manual Entry Screen"]
        R["Settings Screen\n(bank selection)"]
    end
    
    A --> D
    B --> D
    C --> D
    D --> E
    D --> F
    D --> G
    D --> H
    E --> I
    F --> I
    G --> I
    I --> J
    J -->|"unknown"| K
    K --> L
    J --> L
    I --> L
    L --> M
    L --> N
    L --> O
    L --> P
    L --> Q
    L --> R
```

---

## Implementation Milestones

### Milestone 1: Historical SMS Import + Recipient Learning

> **Goal:** Import existing M-PESA SMS from the inbox and auto-categorize using recipient-based learning.

#### 1.1 Data Layer — RecipientCategoryMapping

Create a new Room entity to store learned recipient→category mappings:

```kotlin
// New file: RecipientCategoryMappingEntity.kt
@Entity(tableName = "recipient_category_mapping")
data class RecipientCategoryMappingEntity(
    @PrimaryKey val recipientKey: String,  // normalized: "NAIVAS", "KPLC", "0712XXXXXX"
    val categoryId: Long,
    val recipientDisplayName: String?,     // "Naivas Supermarket"
    val timesUsed: Int = 1,
    val lastUsed: Long = System.currentTimeMillis()
)
```

**Files to create/modify:**
- Create `RecipientCategoryMappingEntity.kt` in `entities/`
- Create `RecipientCategoryMappingDao.kt` in `dao/`
- Update [`PesaTrackDatabase.kt`](../android/app/src/main/java/com/pesatrack/data/local/database/PesaTrackDatabase.kt:16) — add entity + migration 3→4
- Update [`AppModule.kt`](../android/app/src/main/java/com/pesatrack/di/AppModule.kt:19) — provide new DAO

#### 1.2 Data Layer — Add rawSms to ExpenseEntity

Store the raw SMS text so transactions can be re-parsed when parser patterns improve:

```kotlin
// Add to ExpenseEntity.kt
val rawSms: String? = null
```

This requires a Room migration 3→4 (combined with 1.1).

#### 1.3 Data Layer — Bulk Insert Support

Add batch operations to [`ExpenseDao.kt`](../android/app/src/main/java/com/pesatrack/data/local/database/dao/ExpenseDao.kt:10):

```kotlin
@Insert(onConflict = OnConflictStrategy.IGNORE)
suspend fun insertAll(expenses: List<ExpenseEntity>): List<Long>

@Query("SELECT transactionId FROM expenses WHERE transactionId IN (:ids)")
suspend fun getExistingTransactionIds(ids: List<String>): List<String>
```

#### 1.4 Service — SmsImportService

Create a service that reads historical SMS from the inbox via `ContentResolver`:

```kotlin
// New file: SmsImportService.kt
class SmsImportService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val expenseRepository: ExpenseRepository,
    private val recipientMappingRepository: RecipientMappingRepository
) {
    fun importHistoricalSms(
        fromDate: Long?,          // null = all history
        toDate: Long?,            // null = now
        onProgress: (imported: Int, total: Int) -> Unit
    ): ImportResult
}
```

**Key logic:**
1. Query `Telephony.Sms.Inbox.CONTENT_URI` where `address = 'MPESA'`
2. Parse each SMS using existing `SmsParser.parseSms()`
3. Batch deduplicate against existing `transactionId`s
4. Apply deterministic auto-categorization (Airtime→1001, Costs→811)
5. Look up recipient in `RecipientCategoryMapping` for learned categories
6. Return `ImportResult(total, imported, autoCategorized, needsReview)`

#### 1.5 Update CategorizeScreen — Batch Mode

Modify [`CategorizeScreen.kt`](../android/app/src/main/java/com/pesatrack/presentation/screens/categorize/CategorizeScreen.kt:1) to support batch categorization by recipient grouping:

- Group uncategorized expenses by normalized recipient
- Show count + total amount per recipient
- "Apply to all" button saves the mapping to `RecipientCategoryMapping`
- Future transactions from the same recipient are auto-categorized

#### 1.6 UI — Import Screen

Create a new screen accessible from Home or Settings:

- Date range picker (Last 30 days / 3 months / 6 months / All)
- Import progress indicator
- Summary: "Found 150 M-PESA transactions, 45 auto-categorized, 12 recipient groups need review"
- Button to navigate to batch categorize

#### 1.7 Update SmsReceiver — Use Recipient Mapping

Modify [`SmsReceiver.kt`](../android/app/src/main/java/com/pesatrack/services/SmsReceiver.kt:59) to check `RecipientCategoryMapping` before saving. If a mapping exists, auto-categorize the incoming expense (set `categoryId` and `isCategorized = true`).

#### 1.8 Deterministic Auto-Categorization Rules

Add auto-categorization logic beyond just Transaction Costs (category 811):

| PaymentType | Auto-Category | Category ID |
|-------------|--------------|-------------|
| `TRANSACTION_COST` | Mpesa Transaction Cost | 811 |
| `AIRTIME` (self/other) | Airtime | 1001 |

Airtime is always airtime — no user categorization needed.

---

### Milestone 2: Bank SMS Tracking

> **Goal:** Expand SMS parsing to handle bank transaction SMS, starting with Equity and KCB.

#### 2.1 Refactor — Parser Strategy Pattern

Extract current M-PESA logic from [`SmsParser.kt`](../android/app/src/main/java/com/pesatrack/utils/SmsParser.kt:56) into a strategy pattern:

```kotlin
// New file: SmsParserStrategy.kt
interface SmsParserStrategy {
    fun canHandle(sender: String, body: String): Boolean
    fun parse(body: String): SmsParser.ParsedTransaction?
    val source: ExpenseSource
    val senderIds: List<String>
}

// New file: MpesaSmsParser.kt — extract from current SmsParser.kt
class MpesaSmsParser : SmsParserStrategy { ... }

// Refactored SmsParser.kt becomes SmsParserRegistry
object SmsParserRegistry {
    private val parsers: List<SmsParserStrategy> = listOf(
        MpesaSmsParser(),
        // Future: EquityBankParser(), KcbBankParser(), etc.
    )
    
    fun findParser(sender: String, body: String): SmsParserStrategy?
    fun parseTransaction(sender: String, body: String): ParsedTransaction?
}
```

**Key requirement:** This refactor must NOT break existing functionality. The `SmsParser.parseSms()` and `SmsParser.isMpesaSms()` APIs should continue to work (delegate to registry internally).

#### 2.2 Extend Domain Models

Update [`Expense.kt`](../android/app/src/main/java/com/pesatrack/domain/models/Expense.kt:77):

```kotlin
enum class ExpenseSource {
    STK_PUSH,     // legacy
    SMS_MPESA,    // renamed from SMS_PARSED
    SMS_BANK,     // new: bank SMS
    MANUAL;       // manual entry
}

enum class PaymentType {
    // Existing M-PESA types
    SEND_MONEY, BUY_GOODS, PAY_BILL, WITHDRAW, AIRTIME, MPESA_CARD, TRANSACTION_COST,
    // New bank types
    BANK_TRANSFER, BANK_WITHDRAWAL, CARD_PURCHASE, BANK_CHARGE;
}
```

Note: `SMS_PARSED` → `SMS_MPESA` rename needs `fromString()` backward compat.

#### 2.3 Implement Bank Parsers (Start with Equity + KCB)

**Equity Bank SMS formats to handle:**

```
// Payment
"You have made a payment of KES 5,000.00 to NAIVAS at NAIVAS WESTLANDS on 11/03/26 14:30. Ref: TXN123456. Bal: KES 25,000.00"

// Transfer
"KES 10,000.00 has been transferred from your a/c *1234 to a/c *5678 on 11/03/26. Ref: FT123456. Bal: KES 15,000.00"

// Withdrawal
"Cash Withdrawal of KES 5,000.00 from ATM EQUITY TOWER on 11/03/26. Ref: ATM123456. Bal: KES 20,000.00"

// Bank charges
"Your a/c *1234 has been debited KES 30.00 for LEDGER FEE on 11/03/26. Bal: KES 19,970.00"
```

**KCB Bank SMS formats:**

```
// Payment
"Payment of KES 3,000.00 to JAVA HOUSE has been made from a/c ****1234. TXN ID: KCB123456"

// Withdrawal
"Withdrawal of KES 10,000.00 at KCB MOMBASA RD ATM from a/c ****1234. TXN ID: KCB789012"
```

**Files to create:**
- `EquityBankParser.kt` in `utils/parsers/`
- `KcbBankParser.kt` in `utils/parsers/`

#### 2.4 Update SmsReceiver for Multi-Source

Modify [`SmsReceiver.kt`](../android/app/src/main/java/com/pesatrack/services/SmsReceiver.kt:24) to use `SmsParserRegistry` instead of hardcoded M-PESA checks:

```kotlin
// Before:
if (SmsParser.isMpesaSms(sender) && SmsParser.isTransactionSms(body))

// After:
val parsed = SmsParserRegistry.parseTransaction(sender, body)
if (parsed != null) { ... }
```

#### 2.5 Settings — Bank Selection

Create a Settings screen where users can enable/disable which banks to track:
- Toggle: Track M-PESA transactions ✅
- Toggle: Track Equity Bank transactions ☐
- Toggle: Track KCB transactions ☐
- Each toggle configures which parsers are active in the registry

#### 2.6 Historical Import — Extend to Banks

Update `SmsImportService` to also query bank sender IDs when importing history:

```kotlin
val senderIds = listOf("MPESA") + enabledBankSenders  // e.g., ["EquityBnk", "KCB"]
```

---

### Milestone 3: AI-Powered Categorization

> **Goal:** Use Gemini API to auto-categorize expenses that can't be handled by rules or recipient mappings.

#### 3.1 Add Networking Dependencies

Add to [`build.gradle.kts`](../android/app/build.gradle.kts:65):

```kotlin
// Google Generative AI SDK (Gemini)
implementation("com.google.ai.client.generativeai:generativeai:0.9.0")
// or OkHttp for raw API calls
implementation("com.squareup.okhttp3:okhttp:4.12.0")
```

#### 3.2 AI Categorization Service

```kotlin
// New file: AiCategorizationService.kt
class AiCategorizationService @Inject constructor(
    private val categoryRepository: CategoryRepository
) {
    /**
     * Given a list of recipient names, suggest categories for each.
     * Uses Gemini API with the app's category list as context.
     */
    suspend fun suggestCategories(
        recipients: List<RecipientInfo>
    ): Map<String, CategorySuggestion>
}
```

**Prompt design:**
```
You are a Kenyan expense categorizer. Given the following expense categories and recipient names, 
suggest the most appropriate category for each recipient.

Categories: [Food & Dining > Groceries, Food & Dining > Restaurant, Transport > Uber/Bolt, ...]

Recipients to categorize:
1. "NAIVAS SUPERMARKET" (Buy Goods, KES 47,500 total)
2. "JAVA HOUSE LTD" (Buy Goods, KES 12,000 total)
3. "KPLC PREPAID" (Pay Bill, KES 8,000 total)
4. "JOHN KAMAU 0712XXXXXX" (Send Money, KES 30,000 total)

Return JSON: { "NAIVAS SUPERMARKET": { "categoryId": 303, "confidence": 0.95 }, ... }
```

#### 3.3 Integration into Batch Categorize Flow

The batch categorize screen gets an "AI Suggest" button:

```mermaid
flowchart TD
    A["Uncategorized expenses\ngrouped by recipient"] --> B{"Has recipient mapping?"}
    B -->|"Yes"| C["Auto-apply saved category"]
    B -->|"No"| D["Show in batch review"]
    D --> E["User taps 'AI Suggest'"]
    E --> F["Call Gemini API\nwith recipient list"]
    F --> G["Show suggestions\nwith confidence"]
    G --> H{"User confirms?"}
    H -->|"Yes"| I["Save category\n+ recipient mapping"]
    H -->|"Edit"| J["User picks different\ncategory manually"]
    J --> I
```

#### 3.4 AI for Bank SMS Parsing (Future)

For banks where regex is unreliable or format changes frequently, use AI as a fallback parser:

```kotlin
// In SmsParserRegistry
fun parseTransaction(sender: String, body: String): ParsedTransaction? {
    // 1. Try registered regex parsers first
    for (parser in parsers) {
        if (parser.canHandle(sender, body)) {
            return parser.parse(body)
        }
    }
    
    // 2. If no parser matched but sender looks like a bank, try AI parsing
    if (isKnownBankSender(sender)) {
        return aiParserFallback.parse(sender, body)
    }
    
    return null
}
```

#### 3.5 Secure API Key Storage

- Store Gemini API key in `local.properties` (not committed)
- Access via `BuildConfig.GEMINI_API_KEY`
- Add to [`build.gradle.kts`](../android/app/build.gradle.kts:12):
  ```kotlin
  buildConfigField("String", "GEMINI_API_KEY", "\"${properties["GEMINI_API_KEY"]}\"")
  ```

---

### Milestone 4: Manual Expense Entry

> **Goal:** Allow users to add expenses that don't come through SMS (cash, card, etc.)

#### 4.1 Manual Entry Screen

Create `ManualEntryScreen.kt`:
- Amount input (KES)
- Recipient/Description text field
- Category picker (reuse [`GroupedCategoryPicker.kt`](../android/app/src/main/java/com/pesatrack/presentation/components/GroupedCategoryPicker.kt:1))
- Date/time picker
- Notes field
- Payment type selector (Send Money, Buy Goods, Cash, Card, etc.)
- Source = `ExpenseSource.MANUAL`

#### 4.2 Extend PaymentType for Manual Entries

```kotlin
enum class PaymentType {
    // ... existing types ...
    CASH,           // Cash payment
    CARD_PAYMENT,   // Debit/credit card (not M-PESA card)
    OTHER;          // Catch-all
}
```

#### 4.3 Navigation Update

Add manual entry route to [`NavGraph.kt`](../android/app/src/main/java/com/pesatrack/presentation/navigation/NavGraph.kt:17) and a FAB or "+" button on the expenses screen.

---

### Milestone 5: Settings & Configuration

#### 5.1 Settings Screen

- **Import History** — trigger historical SMS import
- **Bank Tracking** — enable/disable banks
- **AI Categorization** — enable/disable, API key entry
- **About** — app version, credits

#### 5.2 Onboarding Flow (First Launch)

On first launch:
1. Welcome screen explaining PesaTrack
2. Permission requests (SMS, Phone, Notifications)
3. Offer historical SMS import with date range picker
4. Import → auto-categorize → batch review
5. Redirect to home screen

---

## File Changes Summary

### New Files

| File | Location | Purpose |
|------|----------|---------|
| `RecipientCategoryMappingEntity.kt` | `entities/` | Learned recipient→category mappings |
| `RecipientCategoryMappingDao.kt` | `dao/` | CRUD for mapping table |
| `RecipientMappingRepository.kt` | `repository/` | Repository for mappings |
| `SmsParserStrategy.kt` | `utils/parsers/` | Parser interface |
| `MpesaSmsParser.kt` | `utils/parsers/` | Extracted M-PESA parser |
| `SmsParserRegistry.kt` | `utils/parsers/` | Parser registry |
| `EquityBankParser.kt` | `utils/parsers/` | Equity Bank SMS parser |
| `KcbBankParser.kt` | `utils/parsers/` | KCB SMS parser |
| `SmsImportService.kt` | `services/` | Historical SMS import logic |
| `AiCategorizationService.kt` | `services/` | Gemini AI categorization |
| `ImportScreen.kt` | `screens/import/` | Import history UI |
| `ImportViewModel.kt` | `screens/import/` | Import state management |
| `BatchCategorizeScreen.kt` | `screens/categorize/` | Batch categorize by recipient |
| `BatchCategorizeViewModel.kt` | `screens/categorize/` | Batch categorize state |
| `ManualEntryScreen.kt` | `screens/manual/` | Manual expense entry |
| `ManualEntryViewModel.kt` | `screens/manual/` | Manual entry state |
| `SettingsScreen.kt` | `screens/settings/` | App settings |
| `SettingsViewModel.kt` | `screens/settings/` | Settings state |

### Modified Files

| File | Changes |
|------|---------|
| [`PesaTrackDatabase.kt`](../android/app/src/main/java/com/pesatrack/data/local/database/PesaTrackDatabase.kt:16) | Add RecipientCategoryMappingEntity, migration 3→4, rawSms column |
| [`ExpenseEntity.kt`](../android/app/src/main/java/com/pesatrack/data/local/database/entities/ExpenseEntity.kt:11) | Add `rawSms: String?` field |
| [`ExpenseDao.kt`](../android/app/src/main/java/com/pesatrack/data/local/database/dao/ExpenseDao.kt:10) | Add `insertAll()`, `getExistingTransactionIds()` |
| [`Expense.kt`](../android/app/src/main/java/com/pesatrack/domain/models/Expense.kt:6) | Add rawSms field, new PaymentTypes, rename SMS_PARSED→SMS_MPESA |
| [`ExpenseRepository.kt`](../android/app/src/main/java/com/pesatrack/data/repository/ExpenseRepository.kt:17) | Add bulk insert, rawSms mapping |
| [`SmsParser.kt`](../android/app/src/main/java/com/pesatrack/utils/SmsParser.kt:56) | Delegate to SmsParserRegistry, keep backward-compat API |
| [`SmsReceiver.kt`](../android/app/src/main/java/com/pesatrack/services/SmsReceiver.kt:24) | Use registry, check recipient mapping for auto-categorize |
| [`AppModule.kt`](../android/app/src/main/java/com/pesatrack/di/AppModule.kt:19) | Provide new DAOs and services |
| [`NavGraph.kt`](../android/app/src/main/java/com/pesatrack/presentation/navigation/NavGraph.kt:17) | Add import, manual entry, settings routes |
| [`Screen.kt`](../android/app/src/main/java/com/pesatrack/presentation/navigation/Screen.kt:6) | Add new screen routes |
| [`HomeScreen.kt`](../android/app/src/main/java/com/pesatrack/presentation/screens/home/HomeScreen.kt:24) | Add import button, settings access |
| [`build.gradle.kts`](../android/app/build.gradle.kts:65) | Add Gemini AI SDK dependency |
| [`AppPreferences.kt`](../android/app/src/main/java/com/pesatrack/data/local/preferences/AppPreferences.kt:1) | Add bank tracking prefs, AI prefs, import status |

---

## Implementation Order

```mermaid
gantt
    title PesaTrack Phase 2 Implementation
    dateFormat X
    axisFormat %s
    
    section Milestone 1 - Historical Import
    RecipientCategoryMapping entity + DAO        :m1a, 0, 1
    Add rawSms to ExpenseEntity + migration 3→4  :m1b, 0, 1
    Bulk insert support in ExpenseDao             :m1c, 0, 1
    SmsImportService                              :m1d, 1, 2
    Deterministic auto-categorization rules       :m1e, 1, 2
    Recipient mapping repository                  :m1f, 1, 2
    Update SmsReceiver for auto-categorize        :m1g, 2, 3
    Batch Categorize Screen                       :m1h, 2, 4
    Import Screen with date picker                :m1i, 3, 4
    
    section Milestone 2 - Bank SMS
    SmsParserStrategy interface                   :m2a, 4, 5
    Extract MpesaSmsParser                        :m2b, 4, 5
    SmsParserRegistry                             :m2c, 5, 6
    EquityBankParser                              :m2d, 5, 6
    KcbBankParser                                 :m2e, 6, 7
    Update SmsReceiver for multi-source           :m2f, 6, 7
    Extend domain models                          :m2g, 4, 5
    Settings screen - bank selection              :m2h, 7, 8
    
    section Milestone 3 - AI
    Add Gemini SDK dependency                     :m3a, 8, 9
    AiCategorizationService                       :m3b, 8, 9
    Integrate into batch categorize               :m3c, 9, 10
    API key management                            :m3d, 8, 9
    
    section Milestone 4 - Manual Entry
    ManualEntryScreen + ViewModel                 :m4a, 10, 11
    Navigation updates                            :m4b, 10, 11
    Extend PaymentType                            :m4c, 10, 11
```

---

## Recommended Execution Strategy

### **Start with Milestone 1** (Historical Import + Recipient Learning)
This delivers the most user value immediately:
- Users get their entire M-PESA history in the app from day one
- The recipient mapping table enables auto-categorization for ALL future transactions
- The batch categorize UI reduces manual work by 90%+

### **Then Milestone 2** (Bank SMS)
Once the parser strategy pattern is in place and proven with M-PESA, adding banks is straightforward plug-in work.

### **Then Milestone 4** (Manual Entry)
Simple CRUD screen — low complexity, useful for cash/card expenses.

### **Milestone 3** (AI) can be started in parallel
AI categorization is additive — it enhances the batch categorize flow but isn't blocking.

---

## Key Design Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Import on first launch vs wait | **Import immediately** | Recipient mapping works from day one; deterministic rules handle ~40% of transactions without any user input |
| AI on-device vs API | **API first (Gemini)** | Gemini Nano requires Android 14+ (minSdk is 26); API works everywhere; can add on-device later |
| One parser per bank vs AI for all banks | **Regex first, AI fallback** | Regex is free, fast, offline; AI is a fallback for unknown formats |
| Rename SMS_PARSED to SMS_MPESA | **Yes, with backward compat** | Cleaner model as we add SMS_BANK; fromString() handles old records |
| Store raw SMS text | **Yes** | Enables re-parsing when patterns improve; useful for debugging |
| Batch categorize vs one-by-one | **Batch by recipient** | 200 transactions = ~15 taps instead of 200 |
