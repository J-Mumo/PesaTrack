# PesaTrack — Agent Instructions

## Project Identity

PesaTrack is a **passive M-PESA expense tracker** for Android (Kotlin). It intercepts SMS messages from M-PESA and supported banks, parses transaction details, and stores them locally for categorization, budgeting, and analytics. Published on Google Play Store (v1.1.0+).

**No cloud sync from the shipped app.** The [backend/](backend) folder contains a Node/Express + Prisma + Daraja service that is **not consumed by the current Play Store build**. Do not introduce calls to it from the Android app without explicit instruction (see [plans/daraja-production-migration.md](plans/daraja-production-migration.md) and [plans/business-transition-plan.md](plans/business-transition-plan.md) for the longer-term direction).

---

## Mission & Product Principles

**Mission:** Help people improve their finances by building a better **spending and investment culture**.

These principles are decision tiebreakers. When two implementations are both technically valid, pick the one that better serves the principles. The long-form version lives in [plans/product-principles.md](plans/product-principles.md).

1. **Awareness before action** — surface spending facts the user didn't know before suggesting changes.
2. **Nudge, don't nag** — at most one proactive insight per session; never use dark patterns or fear framing.
3. **Save and invest by default** — every analytics surface should, where honest, include a "what you could save / invest" framing.
4. **Privacy is non-negotiable** — never ship a feature that requires sending raw SMS or PII off-device without explicit, revocable consent.
5. **Honest numbers** — no projections without showing assumptions; no "savings" figures that ignore transaction costs (cat 606).
6. **Local-first** — features must degrade gracefully offline.

### Feature Decision Filter

Every new feature (and any non-trivial change) must answer these in its PR / plan / implementation note:

1. Which principle does this serve? (If none, reconsider.)
2. What user behavior does it change? (awareness / spending / saving / investing / none)
3. What is the honest downside or failure mode?
4. How is success observable to the user? (Even if just "the user can answer X question they couldn't before.")

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Kotlin |
| UI | Jetpack Compose (Material 3) |
| Architecture | MVVM (ViewModel + UiState data classes) |
| DI | Hilt (Dagger) |
| Database | Room (SQLite), currently v15 |
| Preferences | Jetpack DataStore |
| Async | Kotlin Coroutines + Flow |
| Navigation | Jetpack Navigation Compose |
| Background | WorkManager (recurring reminders) |
| PDF parsing | Custom regex-based (M-PESA statements) |
| Excel parsing | Apache POI |

---

## Architecture Overview

```
SMS/PDF/Excel Sources → Parser Layer → Repository → Room DB → ViewModel → Compose UI
```

- **Parser layer** uses Strategy pattern via `SmsParserRegistry` — each bank/source implements `SmsParserStrategy`
- **Auto-categorization** pipeline: KeywordRulesEngine → PaymentType heuristics → Recipient mapping (learned) → Excel label mapping
- **All data is local** — Room + DataStore only. No network calls from the Android app.
- Transaction costs are extracted and saved as separate expenses (category ID 606)

---

## Package Structure

```
com.pesatrack/
├── PesaTrackApp.kt           # @HiltAndroidApp Application class
├── data/
│   ├── local/                # Room (database/) + DataStore (preferences/)
│   └── repository/           # Repository classes
├── di/                       # Hilt modules (AppModule.kt)
├── domain/models/            # Domain/UI models (not Room entities)
├── presentation/
│   ├── components/           # Reusable Compose components
│   ├── navigation/           # NavGraph + Screen sealed class
│   ├── screens/              # Feature screens (each has Screen, ViewModel, UiState)
│   └── theme/                # Material 3 theme
├── services/                 # Business logic services, BroadcastReceiver, Workers
└── utils/
    ├── Constants.kt          # App-wide constants
    ├── SmsParser.kt          # Legacy/top-level SMS entry point
    ├── UsageSummaryGenerator.kt
    ├── parsers/              # SMS parser strategies (MpesaSmsParser, NcbaBankParser, etc.)
    └── excel/                # Excel import utilities
```

---

## Key Conventions

### Code Patterns
- Every screen follows: `*Screen.kt` + `*ViewModel.kt` + `*UiState.kt`
- UiState is a `data class` exposed as `StateFlow` from ViewModel
- Repositories are `@Singleton` Hilt-provided classes wrapping DAOs
- Database migrations are explicit (no destructive fallback in production)
- SMS parsers implement `SmsParserStrategy` and register in `SmsParserRegistry`

### Naming
- Entities: `*Entity.kt` (Room tables)
- DAOs: `*Dao.kt`
- Domain models: plain names (`Expense`, `Category`, `Budget`)
- Screens: `*Screen.kt` (composable), navigation via `Screen` sealed class

### Copy & UX Writing Guidelines

Applies to every user-facing string (Compose `Text`, notifications, dialogs, onboarding, empty states).

- **Neutral, factual framing.** "You spent KES 4,200 on transport this week" — not "You overspent."
- **Opportunity, not fear.** Prefer "KES 1,200 could have gone to savings" over "You're losing money."
- **Pair findings with an optional, dismissible action.** Never a mandatory CTA.
- **Numbers carry context.** Always show a comparison (last month, % of income, % of category) or unit.
- **Currency:** KES with thousands separators (e.g., `KES 12,450`). Use second person, present tense.
- **No advice on specific securities, brokers, or guaranteed returns.** Frame investment math as *illustrations* with visible assumptions.
- **No streaks, badges, or loops that reward more transactions.** Reward awareness and saving, not spending.

### Do NOT
- Reference or call the backend server from the Android app
- Use destructive database migrations
- Modify completed plan files in `plans/` directory
- Add network/cloud dependencies without explicit user request
- Use RxJava (project uses Coroutines + Flow exclusively)
- Add gamification that encourages more spending (streaks-for-transactions, etc.)
- Show projections (forecasts, "what if invested") without surfacing the underlying assumptions
- Recommend specific securities, brokers, or guaranteed returns — always frame as illustration
- Use fear-based or shaming copy ("you're losing money", "you overspent")
- Push notifications beyond the user's configured cadence, or mix marketing into insight notifications
- Re-classify transaction fees (cat 606) as ordinary discretionary spend — keep them surfaceable
- Ship a feature that requires connectivity for behavior that previously worked offline

### Do
- Update `_docs/implementation-status.md` after any feature/fix/structural change
- Use the Strategy pattern when adding new SMS source support
- Follow existing UiState pattern for new screens
- Use Hilt `@Inject constructor` for all ViewModels and services
- Keep transaction deduplication via `transactionId` uniqueness constraint
- Run every new feature through the **Feature Decision Filter** above

---

## Documentation Map

| File | Purpose |
|------|---------|
| `_docs/implementation-status.md` | **Source of truth** — full feature status, architecture, file structure |
| `_docs/releases.md` | Version changelog |
| `_docs/brainstorm.md` | Future feature ideas |
| `plans/product-principles.md` | **Mission & principles source** — long-form version of the principles above |
| `plans/*.md` | Feature specs (read-only reference for completed features) |
| `.roo/rules.md` | Roo-specific agent rules |
| `.roo/rules-code.md` | Roo code-mode rules (must update implementation-status after changes) |

---

## Current State

All core features are **complete** (SMS parsing, categorization, budgets, analytics, forecasting, recurring detection, PIN lock, onboarding, Play Store published). The app is in maintenance + incremental feature mode.

---

## Build & Verify Commands

Run from the repository root unless noted. Use the Gradle wrapper (`gradlew.bat` on Windows, `./gradlew` on Unix).

**Android (primary):**

```
cd android
./gradlew lint                # static analysis
./gradlew test                # unit tests (JVM)
./gradlew assembleDebug       # debug APK
./gradlew assembleRelease     # signed release APK (requires signing config)
./gradlew bundleRelease       # AAB for Play Store
```

**Backend (not consumed by the app — do not invoke unless explicitly working on it):**

```
cd backend
npm install
npm start                     # see backend/package.json for exact scripts
npx prisma migrate dev        # local DB migrations
```

After any code change, prefer running `./gradlew lint` and `./gradlew test` before reporting the task complete.

---

## Do Not Edit / Read

The following paths are generated, secret, or reference-only. Skip them when searching and never modify them as part of a task:

- `android/app/build/**` — generated build output
- `android/build/**`, `android/.gradle/**` — Gradle caches
- `android/local.properties` — local SDK paths / secrets
- `backend/src/generated/**` — generated Prisma client
- `backend/node_modules/**`
- `backend/.env`, any `*.env*` file — secrets
- `plans/*.md` — completed plan specs, treat as read-only reference
- `.roo/**` — agent-tool config (edit only if explicitly asked)

---

## Auto-Update Rule

After implementing any feature, bug fix, or structural change, the agent **MUST** update `_docs/implementation-status.md` before marking the task complete. Checklist:

- [ ] New/renamed/deleted files → Update "Current File Structure" section
- [ ] New feature → Update Executive Summary table + Detailed Implementation tables
- [ ] Bug fix → Add to "Bug Fixes & Improvements History"
- [ ] Database migration → Update Data Layer section
- [ ] New dependency → Update Dependencies list
- [ ] New screen/route → Update Navigation + Presentation Layer sections
- [ ] Architecture change → Update System Architecture diagram

---

---

_Compatibility: standard Markdown, vendor-neutral. Consumed by GitHub Copilot, Roo/Cline (alongside [.roo/rules.md](.roo/rules.md)), Cursor, and other LLM agents that read `AGENTS.md` at the repo root._
