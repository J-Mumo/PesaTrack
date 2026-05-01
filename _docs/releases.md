# PesaTrack Release History

> This document tracks all Play Store releases of PesaTrack, including version codes, release dates, and changelogs.

---

## Release Summary

| Version | Code | Date | Track | Status |
|---------|------|------|-------|--------|
| **1.2.0** | 5 | 2026-04-29 | Production | 🟡 Pending upload |
| **1.1.0** | 4 | 2026-04-17 | Production | ✅ Published |
| **1.0.2** | 3 | 2026-04-02 | Production | ✅ Published |
| **1.0.1** | 2 | 2026-04-01 | Production | ✅ Published |
| **1.0.0** | 1 | 2026-03-31 | Production + Internal Testing | ✅ Published |

---

## v1.2.0 (versionCode 5) — 2026-04-29

**Focus:** NCBA bank parser fix, budget notification fix, user feedback & engagement system

### ✨ New Features
- **Weekly Snapshot Card (Analytics)** — Moved weekly spending info from the Home screen to a richer card in the Analytics monthly tab. Shows: this-week total, daily average, week-over-week % change (color-coded ↑/↓), and top spending category for the week.
- **In-App Review Prompt (Stage 1B)** — Google Play In-App Review triggered for engaged users (≥5 qualified sessions + key milestones reached); throttled to avoid annoyance
- **Structured Feedback Prompt (Stage 1D)** — Home screen card asking "What would make PesaTrack more useful?" with 6 predefined options + free text; response saved locally and opens prefilled email draft
- **Low-Engagement Feedback (Stage 1E)** — Home screen card for users who haven't completed setup, asking "What blocked setup for you?" with 6 reasons; captures friction points locally + email draft
- **Usage Summary in Feedback Emails** — Contact & Feedback email from About screen now includes anonymized usage context (install date, session count, feature usage) to help diagnose issues
- **Share PesaTrack** — New share button in Settings screen to tell friends about the app
- **Usage Metrics Snapshot** — Local-only tracking of milestones (first SMS parsed, first import, etc.) and feature usage counters for improving the product; data never leaves device unless user explicitly shares

### 🐛 Bug Fixes
- **NCBA Bank SMS Not Importing** — NCBA changed their SMS format: Till messages no longer include the till number, and Paybill messages no longer include the "account" keyword. Added `tillPaymentPatternB` (name-only) and `paybillPatternC` (name-only) to handle the new format. Old patterns kept as fallbacks for backward compatibility.
- **Budget "Exceeded" at Exactly 100%** — Budget notification incorrectly said "Budget exceeded!" when spending was exactly at 100%. Now correctly says "Budget fully used!" at exactly 100%, and "Budget exceeded!" only when spending goes over 100%.

### 📦 Technical
- New `WeeklySnapshotCard` composable in Analytics; new `getTopCategoryInRange` DAO query + `TopCategoryResult` data class; `loadWeeklySnapshot()` in `AnalyticsViewModel`
- Removed `totalLast7Days` from `HomeUiState` / `HomeViewModel` / `MonthlySummaryCard` (moved to Analytics)
- New `UsageSummaryGenerator` utility (text + JSON output, Hilt-injected)
- New `AboutViewModel` for Hilt dependency injection in About screen
- `AppPreferences`: added review prompt, feedback, low-engagement, and usage metrics snapshot keys
- `DataManagementService`: embeds `usageMetrics` JSON in backup metadata
- Google Play In-App Review dependency (`review-ktx:2.0.2`)
- `NcbaBankParser`: renamed `tillPaymentPattern` → `tillPaymentPatternA`, added `tillPaymentPatternB` + `paybillPatternC`
- Privacy policy: removed misleading usage tracking paragraph

---

## v1.1.0 (versionCode 4) — 2026-04-17

**Focus:** Budget forecasting, notification improvements, stability fixes

### ✨ New Features
- **Budget Forecasting (4 phases)** — Linear burn rate projections across the entire budget system:
  - **Home Forecast Card** — Top 5 at-risk budgets with color-coded status (🔴 exhaustion imminent / 🟡 projected over / 🟢 on track), safe daily spend amount
  - **Budget Per-Card Forecast** — Each budget card shows projected % by period-end, exhaustion date warning, safe daily spend
  - **Forecast Notifications** — Fires when projected ≥110% with ≥7 days remaining OR exhaustion <5 days; 24-hour per-budget throttle
  - **Analytics Projection Chart** — Cumulative actual spending line + projected extension to month-end + budget ceiling reference line (Vico 2-series chart)
- **Deep-link Notification Taps** — Tapping expense notifications opens the categorize screen; tapping budget alert notifications opens the budget screen

### 🐛 Bug Fixes
- **Excel Import Crash** — Fixed crash when importing Excel files with large SMS datasets for matching
- **Home Screen Budget Reactivity** — Budget summary data on the Home screen now properly reacts to budget deletions via Room Flow (previously stale data persisted until manual refresh)

### 📦 Technical
- New `ForecastService` (`@Singleton`, Hilt-injected, pure Kotlin — no DB tables, no schema migration)
- `BudgetService.checkForecastsAfterExpense()` triggered from `SmsReceiver` after each SMS expense save
- Forecast notification throttle stored in `AppPreferences` DataStore (24h per-budget cooldown)
- `ForecastProjectionChart` composable using Vico 2-series line chart
- No database migration required — forecasting is pure computation on existing data

---

## v1.0.2 (versionCode 3) — 2026-04-02

**Focus:** Onboarding fix, SMS permission recovery

### 🐛 Bug Fixes
- **Onboarding "Import Now" Navigation** — Fixed the "Import Now" button on onboarding page 4 navigating to the Home screen instead of the Import screen. Root cause: `onImportNow` callback called both `onImportHistory()` (no-op) and `onComplete()` which finished onboarding and showed Home. Fix: `pendingImportNavigation` flag in `AppEntryPoint` → `LaunchedEffect` navigates to `Screen.ImportHistory` after onboarding completes.

### ✨ New Features
- **SMS Permission Recovery (Home Banner + Import Gate)** — Two new surfaces for users who skipped onboarding SMS permission or later revoked it:
  - **Home Screen banner** — Shown when SMS permission is missing and not permanently dismissed. Three actions: "Enable" (launches permission request), "Not now" (session dismiss), "Don't ask again" (permanent dismiss via DataStore)
  - **Import Screen gate** — Full-screen explanation card with "Grant SMS Permission" / "Open App Settings" buttons when `READ_SMS` not granted; Excel import remains accessible

---

## v1.0.1 (versionCode 2) — 2026-04-01

**Focus:** Post-launch cleanup, data safety

### ✨ New Features
- **Database Backup & Restore** — Full database backup/restore via SAF (Storage Access Framework):
  - Backup creates a `.db` file with embedded `_backup_metadata` table containing settings (month start day, bank tracking preferences)
  - Restore validates SQLite header, closes current DB, replaces files, restores preferences, restarts app process
  - Users can save backups to Downloads, Google Drive, etc.

### 🐛 Bug Fixes
- **Sample Data Hidden** — Removed `SampleDataService` visibility from production users (was accessible in debug builds and leaked into release)

---

## v1.0.0 (versionCode 1) — 2026-03-31

**Focus:** Initial Play Store release — full-featured passive M-PESA expense tracker

### 🎉 Launch Features

**Core — SMS Parsing & Tracking**
- M-PESA SMS parsing (8 expense types: Send Money, Buy Goods, Pay Bill, Withdraw, Airtime self/other, M-PESA Card, Fuliza)
- NCBA Bank SMS parsing (3 types: Send Money, Till Payment, Paybill)
- Transaction cost auto-extraction and tracking (saved under category 606)
- Non-expense SMS filtering (Receive Money, Deposit, Reversal silently skipped)
- Multi-part SMS concatenation
- Duplicate detection via `transactionId` uniqueness constraint

**Data & Categories**
- Room Database v14 with full migration chain
- 18 category groups with 90+ sub-categories (hierarchical)
- Investment & Savings as separate top-level group (13 sub-categories)
- Custom categories + sub-categories with icon/color pickers
- User-defined auto-categorization rules (EXACT/CONTAINS/STARTS_WITH)
- KeywordRulesEngine with 100+ business name matches + PaymentType heuristics

**Import & Entry**
- Historical SMS import (M-PESA + enabled banks) with date range picker
- Excel .xlsx import (match to SMS + standalone) with 55+ category mappings
- Manual expense entry form (amount, recipient, payment type, date, category, notes)
- Batch categorization by recipient with multi-select mode

**Budgets**
- 3-tier budgets: Total Spending, Group-level, Sub-category-level
- Weekly / Monthly / Yearly periods
- Global month-start-day setting (1–28) for salary-aligned budgets
- Budget alerts at 80% (warning) and 100% (exceeded) — notification channel
- Monthly income tracking with allocation summary
- Period-first Budget screen with searchable hierarchical category picker

**Analytics**
- Monthly tab: trend line, daily columns, category bars, top spenders, payment type breakdown, MoM comparison
- Yearly tab: YoY comparison, 12-month overlay chart, yearly category/recipient/payment breakdowns
- Variable-spend category trends (CV-based detection, ≥3 months, KES 100 minimum)

**Security**
- PIN lock (4-digit, SHA-256 + salt, never plaintext)
- Biometric unlock (fingerprint/face via BiometricPrompt)
- Brute force protection (5 attempts → 30s cooldown)
- Configurable background lock timeout (immediate/30s/1min/5min)

**UX**
- First-launch onboarding (4-page HorizontalPager)
- Notification system (expense alerts, budget alerts, tap-to-categorize)
- Category-aware expense cards with color coding
- Exclude pass-through expenses (long-press toggle)
- About screen + Privacy Policy (GitHub Pages)
- Data management (CSV export, category reset)
- Settings (bank toggles, security, budget month, categories)

**Distribution**
- Application ID: `com.pesatrack`
- Signed AAB: 13.9 MB (R8 minified + resource shrunk)
- Privacy Policy hosted via GitHub Pages
- Data Safety declaration (all data on-device, no collection/sharing)
- SMS Permission Declaration approved

---

## Version Naming Convention

| Pattern | Meaning |
|---------|---------|
| `1.x.y` | Major.Feature.Patch |
| `versionCode` | Monotonically increasing integer (Play Store requirement) |

- **Major (1.x.x):** Breaking changes, major redesign, or architectural shift
- **Feature (x.1.x):** New user-facing features (forecasting, cloud sync, new bank parsers)
- **Patch (x.x.1):** Bug fixes, UI tweaks, stability improvements

---

## Play Store Release Checklist

When publishing a new version:

1. [ ] Update `versionCode` (increment by 1) and `versionName` in [`build.gradle.kts`](../android/app/build.gradle.kts:26)
2. [ ] Build signed AAB: `./gradlew bundleRelease`
3. [ ] Test on physical device (install from AAB or use bundletool)
4. [ ] Update this document with the new version's changelog
5. [ ] Upload AAB to Play Console → Production track
6. [ ] Write release notes in Play Console (max 500 chars)
7. [ ] If Data Safety changes: update the form before or alongside the release
8. [ ] Consider staged rollout (10% → 50% → 100%) for feature releases
9. [ ] Commit version bump + release notes: `git commit -m "v{version}: {summary}"`
