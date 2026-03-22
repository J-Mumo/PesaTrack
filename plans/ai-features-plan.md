# PesaTrack AI Features Plan

> **Created:** 2026-03-22
> **Status:** Planning
> **Constraints:** No large on-device model downloads (ruled out Gemini Nano's 2-4 GB), offline-first design, `minSdk 26`, zero/minimal APK size increase preferred.

---

## Table of Contents

1. [Feature Ideas Overview](#1-feature-ideas-overview)
2. [AI Provider Alternatives](#2-ai-provider-alternatives)
3. [On-Device Lightweight ML Options](#3-on-device-lightweight-ml-options)
4. [Feature Details](#4-feature-details)
   - [Smart Categorization](#41-smart-categorization-upgrade)
   - [NLP Spending Summaries](#42-nlp-spending-summaries)
   - [Anomaly Detection](#43-anomaly-detection)
   - [Recurring Expense Detection](#44-recurring-expense-detection)
   - [Smart Budget Suggestions](#45-smart-budget-suggestions)
   - [Conversational Expense Query](#46-conversational-expense-query)
   - [Predictive Cashflow](#47-predictive-cashflow)
5. [Recommended Architecture](#5-recommended-architecture)
6. [Implementation Priority](#6-implementation-priority)

---

## 1. Feature Ideas Overview

| # | Feature | AI Type | Offline? | APK Impact |
|---|---------|---------|----------|------------|
| 1 | **Smart Categorization** (upgrade rules engine) | Classification | Hybrid | 0–5 MB |
| 2 | **NLP Spending Summaries** | Generative LLM | Cloud only | 0 |
| 3 | **Anomaly Detection** | Statistical + heuristics | ✅ Fully | 0 |
| 4 | **Recurring Expense Detection** | Pattern matching | ✅ Fully | 0 |
| 5 | **Smart Budget Suggestions** | Statistical | ✅ Fully | 0 |
| 6 | **Conversational Expense Query** | Generative LLM | Cloud only | 0 |
| 7 | **Predictive Cashflow** | Time-series | ✅ Fully | 0 |

**Ruled out:** Receipt/Screenshot OCR (not needed), Gemini Nano (2-4 GB download unacceptable).

---

## 2. AI Provider Alternatives

### Cloud LLM APIs (Zero APK Size)

These require network but add **zero APK size**. The dormant Railway backend could proxy these.

| Provider | Model | Cost | Latency | Free Tier |
|----------|-------|------|---------|-----------|
| **Groq** | Llama 3.3 70B | Free (rate-limited) | ~100ms | **6,000 req/day free** |
| **Google** | Gemini 2.0 Flash | $0.10/1M input tokens | ~200ms | **15 RPM free** |
| **Mistral** | Mistral Small | $0.10/1M input tokens | ~300ms | **Free tier available** |
| **OpenRouter** | Multi-model gateway | Varies | Varies | **Free models available** (Llama, Mistral) |
| **Cohere** | Command R+ | $0.50/1M input tokens | ~500ms | **1,000 req/month free** |
| **OpenAI** | GPT-4o-mini | $0.15/1M input tokens | ~300ms | None (pay-as-go) |
| **Anthropic** | Claude 3.5 Haiku | $0.25/1M input tokens | ~400ms | None |

**Best fit for PesaTrack:** **Groq** (free, fastest) or **Gemini Flash** (free tier, Google ecosystem).

### Why Not Gemini Nano

- Requires 2-4 GB on-device model download via Android AICore
- Only works on Android 14+ with specific chipsets (Tensor G3+, Snapdragon 8 Gen 3+)
- PesaTrack's `minSdk 26` means most target devices can't use it
- Unacceptable UX to ask users to download gigabytes for expense categorization

---

## 3. On-Device Lightweight ML Options

For features that need offline capability (primarily categorization):

| Approach | Model Size | Accuracy | Speed | Framework |
|----------|-----------|----------|-------|-----------|
| **TFLite custom classifier** | ~1-5 MB | ⭐⭐⭐⭐ | <10ms | `tensorflow-lite:2.14.0` (~3 MB) |
| **MediaPipe Text Classifier** | ~1-3 MB model | ⭐⭐⭐⭐ | <10ms | `tasks-text:0.10.14` (~5 MB) |
| **ONNX MobileBERT** | ~25 MB | ⭐⭐⭐⭐½ | ~30ms | `onnxruntime-android:1.16.3` (~8 MB) |
| **Enhanced rules engine** | 0 | ⭐⭐⭐ | <1ms | Pure Kotlin (no dependency) |

### Enhanced Rules Engine (Zero-Dependency AI)

Expand the existing [`KeywordRulesEngine`](../android/app/src/main/java/com/pesatrack/services/KeywordRulesEngine.kt:1) with statistical techniques — no ML framework needed:

| Technique | How | Size Impact |
|-----------|-----|-------------|
| **N-gram matching** | "JAVA HOUSE" → match "JAVA" to Food | 0 |
| **Fuzzy string matching** | "NAVIAS" (typo) → match to "NAIVAS" via Levenshtein | ~50 KB lib or pure Kotlin |
| **User-trained frequency model** | Track which categories users pick for similar recipient patterns | 0 (stored in Room) |
| **Amount-range heuristics** | KES 50-200 via BUY_GOODS → likely Food/Snacks | 0 |
| **Time-based heuristics** | 7-9 AM + BUY_GOODS + KES < 500 → likely Food (breakfast) | 0 |

---

## 4. Feature Details

### 4.1 Smart Categorization Upgrade

**Current state:** [`KeywordRulesEngine`](../android/app/src/main/java/com/pesatrack/services/KeywordRulesEngine.kt:1) with 100+ hardcoded keyword rules + recipient mappings from user history.

**Proposed upgrade — three-tier fallback:**

```
SMS Received
    │
    ▼
┌─────────────────────────────────┐
│ Tier 1: Recipient Mapping       │  ← Instant, 100% accurate for known recipients
│ (Room DB lookup)                │
└────────────┬────────────────────┘
             │ No match
             ▼
┌─────────────────────────────────┐
│ Tier 2: KeywordRulesEngine      │  ← Instant, works offline, 100+ business names
│ + Fuzzy matching                │
│ + Amount/time heuristics        │
└────────────┬────────────────────┘
             │ No match
             ▼
┌─────────────────────────────────┐
│ Tier 3: Cloud API (optional)    │  ← 100-300ms, needs network, handles unknowns
│ Groq / Gemini Flash             │
└────────────┬────────────────────┘
             │ No network / disabled
             ▼
┌─────────────────────────────────┐
│ Fallback: Leave uncategorized   │
│ User categorizes manually       │
└─────────────────────────────────┘
```

**Cloud categorization prompt:**
```
You are a Kenyan expense categorizer. Given this M-PESA transaction, return ONLY 
the category ID number.

Categories: 1=Beekeeping, 2=Digital&Tech, 3=Education, 4=Entertainment, 
5=Faith&Giving, 6=Financial, 7=Food&Dining, 8=Government, 9=Health, 
10=Home&Utilities, 11=Life Events, 12=Miscellaneous, 13=Personal Care, 
14=Pets, 15=Shopping, 16=Transport, 17=Vehicle, 18=Investment

Transaction: Paid KES 2,340 to QUICKMART STORES via Buy Goods
Answer: 7
Transaction: Sent KES 15,000 to STIMA SACCO via Pay Bill  
Answer: 18
Transaction: Sent KES 850 to {RECIPIENT} via {TYPE}
Answer:
```

**Implementation:**
- Add `AiCategorizationProvider` interface with `CloudAiProvider` and `RulesEngineProvider` implementations
- Settings toggle: "Use cloud AI for categorization" (default: off)
- API key stored in [`AppPreferences`](../android/app/src/main/java/com/pesatrack/data/local/preferences/AppPreferences.kt:1) DataStore
- Graceful fallback: if cloud call fails, silently fall through to uncategorized

---

### 4.2 NLP Spending Summaries

Generate natural language insights from the user's spending data.

**Example outputs:**
- *"You spent 34% more on Food & Dining this month than your 3-month average. Eating Out drove most of the increase."*
- *"Your transport costs drop significantly on weekends — you could save ~KES 2,400/month by working from home one extra day."*
- *"3 of your top 5 expenses this month are recurring bills. Consider setting up budgets for them."*

**Implementation:**
- Aggregate spending data locally (already done in [`AnalyticsViewModel`](../android/app/src/main/java/com/pesatrack/presentation/screens/analytics/AnalyticsViewModel.kt:1))
- Send **only aggregated numbers** to cloud LLM (not raw transaction data — privacy)
- Display as a card on Home or Analytics screen

**Prompt template:**
```
Summarize this person's {MONTH} spending in 2-3 sentences. Be specific with numbers.
Mention any notable changes from last month. Keep it conversational and actionable.

Total: KES {TOTAL}. Last month: KES {LAST_MONTH_TOTAL}.
Top categories: {CATEGORY_BREAKDOWN}.
Budget status: {BUDGET_STATUS}.
```

**Requires:** Cloud API (Groq or Gemini Flash). Cannot be done by rules engine.

---

### 4.3 Anomaly Detection

Flag transactions that deviate significantly from the user's patterns. Builds on existing CV analysis in [`AnalyticsViewModel`](../android/app/src/main/java/com/pesatrack/presentation/screens/analytics/AnalyticsViewModel.kt:1).

**Detection signals:**

| Signal | Method | Threshold |
|--------|--------|-----------|
| Amount outlier | Z-score within category | > 2σ above mean |
| New large recipient | First time seeing recipient + amount > KES 5,000 | N/A |
| Unusual time | Transaction outside user's normal hours | > 2σ from mean hour |
| Category spike | Monthly category spend vs 3-month average | > 50% increase |
| Frequency spike | Transaction count per day vs average | > 3× daily average |

**Implementation:**
- Pure Kotlin statistical calculations — **no ML framework needed**
- Runs after each expense save in [`SmsReceiver`](../android/app/src/main/java/com/pesatrack/services/SmsReceiver.kt:30)
- Notification: "🔍 Unusual expense: KES 12,400 to NEW RECIPIENT (your average in Food is KES 1,200)"
- Uses existing [`NotificationHelper`](../android/app/src/main/java/com/pesatrack/services/NotificationHelper.kt:19) with new "Anomaly Alerts" channel

**Data model:**
```kotlin
data class AnomalyAlert(
    val expenseId: Long,
    val type: AnomalyType,  // AMOUNT_OUTLIER, NEW_LARGE_RECIPIENT, CATEGORY_SPIKE, etc.
    val severity: Float,    // 0.0 - 1.0
    val message: String
)
```

---

### 4.4 Recurring Expense Detection

Detect transactions that repeat with similar amount, same recipient, at regular intervals.

**Detection logic:**
1. Group expenses by normalized recipient name
2. For each recipient with ≥3 transactions:
   - Calculate intervals between consecutive transactions
   - Check if intervals cluster around a period (±3 days tolerance):
     - **Weekly:** ~7 days
     - **Bi-weekly:** ~14 days
     - **Monthly:** ~30 days
     - **Quarterly:** ~90 days
   - Check amount consistency (CV < 0.1 = stable, < 0.2 = semi-stable)
3. Tag matching expenses as recurring + predict next occurrence

**Output:**
```kotlin
data class RecurringExpense(
    val recipientName: String,
    val averageAmount: Double,
    val period: RecurrencePeriod,  // WEEKLY, BIWEEKLY, MONTHLY, QUARTERLY
    val confidence: Float,         // 0.0 - 1.0
    val nextExpectedDate: LocalDate,
    val categoryId: Long?
)
```

**Value:**
- Feeds into **Forecasting** (separates predictable from discretionary spending)
- Solves the "rent on day 1 skews projection" problem from [forecasting plan](../plans/forecasting-plan.md)
- UI: "Recurring" badge on expense cards, "Recurring Expenses" section on Home

**Implementation:** Pure Kotlin, queries [`ExpenseDao`](../android/app/src/main/java/com/pesatrack/data/local/database/dao/ExpenseDao.kt:10), no ML framework needed.

---

### 4.5 Smart Budget Suggestions

AI-assisted budget recommendations based on spending history.

| Feature | Approach | Data Needed |
|---------|----------|-------------|
| **Initial budget recommendation** | P75 of last 3-6 months per category | Monthly category totals |
| **Budget adjustment nudges** | Detect 3+ consecutive months over budget | Budget + spending history |
| **Seasonal awareness** | Compare current month to same month last year | 12+ months of data |
| **Stable category detection** | Reuse CV analysis from analytics | Existing [`AnalyticsViewModel`](../android/app/src/main/java/com/pesatrack/presentation/screens/analytics/AnalyticsViewModel.kt:1) data |

**Example nudges:**
- *"You typically spend KES 14,200/month on Food & Dining. Set budget at KES 15,000?"*
- *"You've exceeded your Food budget 3 months in a row. Increase to KES 18,000?"*
- *"December spending is typically 40% higher. Consider temporary budget increases."*

**Implementation:**
- Pure Kotlin statistical analysis — **no ML framework needed**
- Triggered when user opens Budget screen with no budgets set
- Also triggered monthly for existing budgets that are consistently exceeded
- Uses existing [`BudgetRepository`](../android/app/src/main/java/com/pesatrack/data/repository/BudgetRepository.kt:1) data

---

### 4.6 Conversational Expense Query

A lightweight chat interface for querying expenses in natural language.

**Example queries:**
- *"How much did I spend on transport last week?"*
- *"Show me all expenses over KES 5,000 in February"*
- *"Compare my food spending this month vs last month"*
- *"What's my biggest expense category this year?"*

**Implementation options:**

| Option | How | Pros | Cons |
|--------|-----|------|------|
| **Template matching** | Regex patterns → Room queries | Offline, fast, deterministic | Limited vocabulary |
| **Cloud LLM → SQL** | LLM generates Room query params | Flexible, natural | Needs network, latency |
| **Hybrid** | Template match first, cloud fallback | Best of both | More complex |

**Architecture (hybrid):**
```
User: "How much on food last month?"
    │
    ▼
Template Matcher (regex)
    │ Match: category="Food", period="last month"
    ▼
ExpenseDao.getByCategory(7, lastMonthStart, lastMonthEnd)
    │
    ▼
Response: "You spent KES 14,200 on Food & Dining in February."
```

**Requires:** Cloud API for flexible queries; template matching for common patterns.

---

### 4.7 Predictive Cashflow

Extends the deferred [forecasting plan](../plans/forecasting-plan.md) — depends on recurring expense detection (#4).

| Model | Input | Output | Complexity |
|-------|-------|--------|------------|
| **Linear burn rate** (MVP) | `spent / daysElapsed × totalDays` | Projected month-end total | Trivial |
| **Weighted recent days** | 60% last 7 days, 40% last 14 | Adjusted projection | Low |
| **Recurring-aware** | Separate recurring vs discretionary | More accurate projection | Medium |

**Key outputs:**

| Output | Surface | Description |
|--------|---------|-------------|
| Budget exhaustion date | Home card | "Food & Dining runs out ~March 25th" |
| Projected end-of-period spend | Home card | "Projected: KES 87,200 / 80,000 (109%)" |
| Safe daily budget | Home card | "KES 240/day to stay on track" |
| Projection line | Analytics chart | Dashed line from today → month-end |

**Implementation:** Pure Kotlin `ForecastService` — no ML framework. See [forecasting plan](../plans/forecasting-plan.md) for full details.

---

## 5. Recommended Architecture

```
┌────────────────────────────────────────────────────────────────────┐
│                        PesaTrack AI Layer                          │
│                                                                    │
│  ┌──────────────────┐  ┌──────────────────┐  ┌────────────────┐   │
│  │ SmartCategorizer  │  │ AnomalyDetector  │  │ RecurrenceDetector│
│  │ (3-tier fallback) │  │ (statistical)    │  │ (pattern match)   │
│  └────────┬─────────┘  └──────────────────┘  └────────────────┘   │
│           │                                                        │
│  ┌────────┴─────────────────────────────────────────────────────┐  │
│  │                     AI Provider Interface                     │  │
│  │  ┌─────────────┐  ┌──────────────┐  ┌───────────────────┐   │  │
│  │  │ RulesEngine │  │ CloudProvider│  │ TFLiteProvider    │   │  │
│  │  │ (always on) │  │ (optional)   │  │ (future, optional)│   │  │
│  │  └─────────────┘  └──────────────┘  └───────────────────┘   │  │
│  └──────────────────────────────────────────────────────────────┘  │
│                                                                    │
│  ┌──────────────────┐  ┌──────────────────┐  ┌────────────────┐   │
│  │ BudgetSuggester  │  │ ForecastService  │  │ NlpInsights    │   │
│  │ (statistical)    │  │ (time-series)    │  │ (cloud LLM)    │   │
│  └──────────────────┘  └──────────────────┘  └────────────────┘   │
│                                                                    │
│  ┌──────────────────────────────────────────────────────────────┐  │
│  │                    Settings & Preferences                     │  │
│  │  • Cloud AI toggle (default: off)                            │  │
│  │  • API key / provider selection                              │  │
│  │  • Anomaly alerts toggle                                     │  │
│  │  • NLP summaries toggle                                      │  │
│  └──────────────────────────────────────────────────────────────┘  │
└────────────────────────────────────────────────────────────────────┘
```

**Key principle:** Every AI feature has an **offline fallback**. Cloud AI is always optional and opt-in.

---

## 6. Implementation Priority

| # | Feature | Effort | Dependencies | APK Impact | Offline |
|---|---------|--------|-------------|------------|---------|
| 1 | **Anomaly Detection** | ~1 day | Existing CV analysis | 0 | ✅ |
| 2 | **Recurring Expense Detection** | ~2 days | ExpenseDao queries | 0 | ✅ |
| 3 | **Smart Budget Suggestions** | ~1 day | Existing analytics + BudgetRepository | 0 | ✅ |
| 4 | **Enhanced Rules Engine** (fuzzy + heuristics) | ~1 day | Existing KeywordRulesEngine | 0 | ✅ |
| 5 | **Predictive Cashflow** | ~2-3 days | #2 (recurring detection) | 0 | ✅ |
| 6 | **Cloud AI Categorization** (Groq/Gemini) | ~2 days | API key setup | 0 | ❌ (fallback: rules) |
| 7 | **NLP Spending Summaries** | ~2 days | Cloud API | 0 | ❌ |
| 8 | **Conversational Query** | ~5 days | Cloud API + template engine | 0 | Hybrid |

### Phase plan:

**Phase A — Statistical AI (offline, zero dependencies):**
Items 1-5. Pure Kotlin. No new libraries. No API keys. Works on all devices.

**Phase B — Cloud AI (optional, opt-in):**
Items 6-8. Requires Groq/Gemini API. Settings UI for key management. Graceful offline fallback.

---

## Comparison Matrix

| Approach | APK Impact | Offline | All Devices | Accuracy | Effort | Cost |
|----------|-----------|---------|-------------|----------|--------|------|
| **Groq API** (free) | 0 | ❌ | ✅ | ⭐⭐⭐⭐⭐ | Low | Free (6K req/day) |
| **Gemini Flash API** | 0 | ❌ | ✅ | ⭐⭐⭐⭐⭐ | Low | Free (15 RPM) |
| **TFLite custom classifier** | ~3-5 MB | ✅ | ✅ | ⭐⭐⭐⭐ | Medium | Free |
| **MediaPipe Text Classifier** | ~5-8 MB | ✅ | ✅ | ⭐⭐⭐⭐ | Medium | Free |
| **Enhanced rules engine** | 0 | ✅ | ✅ | ⭐⭐⭐ | Low | Free |
| ~~Gemini Nano~~ | ~~2-4 GB~~ | ~~✅~~ | ~~❌~~ | ~~⭐⭐⭐⭐⭐~~ | ~~Low~~ | ~~Free~~ |
| ~~ONNX MobileBERT~~ | ~~25 MB~~ | ~~✅~~ | ~~✅~~ | ~~⭐⭐⭐⭐½~~ | ~~High~~ | ~~Free~~ |

### Recommended combination:

1. **Enhanced rules engine** — expand KeywordRulesEngine with fuzzy matching + heuristics (zero cost, zero size)
2. **Groq or Gemini Flash API** — optional cloud fallback for unknown merchants + NLP summaries (free tier, zero APK size)
3. **Statistical AI** — anomaly detection, recurring detection, budget suggestions, forecasting (pure Kotlin, zero dependencies)
