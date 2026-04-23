# PesaTrack Account & Identity Policy

## Overview

PesaTrack is built on a **no-mandatory-account architecture**. This document defines:

1. What stays permanently local and account-free
2. What can be tied to an optional identity layer
3. How and when to introduce optional identity
4. User-facing messaging and trust commitments

---

## Core Principle

> **The core value of PesaTrack must always be accessible without an account.**  
> An account is a convenience upgrade, never an access gate.

This principle is non-negotiable — both because it is the right design for user trust and because it is now part of PesaTrack's public brand promise ("no account needed, no internet required").

---

## Part 1: What Stays Local-Only Forever

These features must **never** require an account, even once optional sign-in exists.

| Feature | Why it stays local |
|---------|--------------------|
| SMS expense capture | Core mechanic. Requiring an account here kills adoption and trust. |
| All expense/category/budget data | User financial data is the most sensitive class. Always device-bound by default. |
| Smart categorization (keyword rules) | Fully on-device computation. No server benefit. |
| Budgets and alerts | Personal financial planning. Zero server-side need. |
| Analytics and charts | Computed locally from local data. |
| PIN lock / biometrics | Security layer on local data. Must not depend on connectivity. |
| Settings and preferences | All stored in local DataStore. |
| Manual backup/restore (local .db) | Already implemented via SAF. Stays free and offline. |
| Onboarding flow | First experience must never require an account. |
| PesaTrack Pro features (on-device) | All Pro v1 features — insights, recommendations, PDF reports — run locally. Pro is gated by Play Billing, not by identity. |

**Rule:** If a feature works today without an account, it must continue to work without one, always, even if an account later becomes available.

---

## Part 2: Features Where Optional Identity Adds Value

These features can be enhanced or unlocked through optional Google sign-in, without removing the local-only alternative where one exists.

| Feature | Local default | Optional-account enhancement |
|---------|--------------|-------------------------------|
| **Backup / Restore** | Manual .db via SAF | Automatic encrypted backup to user's own Google Drive |
| **Cross-device continuity** | Manual backup + restore on new device | Instant restore on new device after sign-in |
| **Pro entitlement portability** | Handled by Google Play Billing automatically — same Google Play account = same purchases | Optional Drive backup of entitlement token for edge cases |
| **Recurring expense coaching** | On-device pattern detection, one device | Persistent coaching history with continuity |
| **Advanced reports / exports** | One-time local export or PDF | Archived reports accessible across devices |
| **Server-side AI features** | Template-based on-device insights | LLM-powered narratives, natural language queries, coaching chat |
| **Support / recovery** | Clear-app-data only | Account-linked recovery mechanism |

**Important constraint:** For every account-enhanced feature, a **local fallback must exist or be explained**.  
e.g., "Sign in with Google to back up automatically — or use manual backup in Settings anytime."

---

## Part 3: What Must Never Happen (Hard Limits)

Regardless of any future commercial decision:

1. **No behavioral or financial data transmitted to PesaTrack servers** unless user has explicitly consented with full understanding.
2. **No profiling, advertising targeting, or data brokering** using any form of user identity.
3. **No required account for core app functionality** — ever.
4. **No silent collection of usage analytics** linked to identity — any telemetry must be anonymous, aggregated, and opt-in.
5. **No account creation via email/password** unless a strong security case exists. Google sign-in (OAuth2) only — no credentials stored on PesaTrack servers.
6. **No required account for Pro features** — Pro is gated by Play Billing purchase status, not by sign-in.

These limits protect the product's trust differentiation, which is a stronger long-term moat than any individual feature.

---

## Part 4: Phased Rollout Plan

### Phase 0 — Current State (Shipped v1.1.0)
- No accounts at all
- Local backup/restore via SAF (manual .db)
- PIN lock + biometric unlock
- All data device-bound
- No INTERNET permission

**Business impact:** Maximum trust, minimum complexity. Right for v1 user base.

---

### Phase 1 — PesaTrack Pro (No Identity Required)

> **Full plan:** [`plans/pro-launch-plan.md`](pro-launch-plan.md)

**What you build:**
- Google Play Billing integration (subscription + lifetime products)
- Template-based recommendation and insight engine (pure Kotlin, on-device)
- Pro feature gates: actionable spending recommendations, deep financial insights with health score, category trend notifications, custom date range analytics, unlimited auto-categorization rules, PDF financial reports
- `ProUpgradeScreen` — paywall with feature showcase
- `InsightsScreen` — financial coaching feed (Pro-gated with soft gate)
- Pro gating across existing screens (Home, Analytics, Settings, Category Management)

**What you do NOT build:**
- No Google Sign-In or identity layer
- No PesaTrack user database
- No server-side anything
- No INTERNET permission
- No Data Safety form changes
- No privacy policy changes
- No new database tables or schema migrations

**Why this first:**
- Pro revenue funds future development (Drive backup, AI features)
- All features are on-device — zero marginal cost per Pro user
- No identity complexity — Play Billing handles everything
- No privacy/policy friction — the smoothest possible Play Store update
- Validates willingness to pay before building expensive infrastructure

**Pricing:**
- Monthly: KES 149 (~$1.15)
- Yearly: KES 999 (~$7.70) — 44% discount
- Lifetime: KES 2,499 (~$19)

---

### Phase 2 — Optional Google Drive Backup (Requires Identity)

**What you build:**
- Google Sign-In via Credential Manager API
- "Back up to Google Drive" toggle in Settings → Data Management
- Uses Google Drive API with `drive.appdata` scope (user's own Drive — not PesaTrack's storage)
- Encrypted with a user-derived key before upload
- "Restore from Drive" option on fresh install
- Account screen in Settings (signed-in state, sign-out, Drive backup toggle)

**What you do NOT build:**
- No PesaTrack user database
- No account creation or credentials
- No server-side data store
- No user lookup by email

**Why this second:**
- Solves the biggest real-world pain (phone change, accidental loss)
- Requires INTERNET permission and Google Cloud Console setup — more complex than Pro
- Revenue from Pro Phase 1 validates investment in this infrastructure
- Does not change Data Safety story meaningfully (data stays in user's own Drive)

**Places to update:**
- `plans/cloud-sync-playstore-impact.md` — already covers this scenario as the recommended path
- `AppPreferences.kt` — add `cloudBackupEnabled` key
- `DataManagementService.kt` — add Google Drive backup/restore methods
- `SettingsScreen.kt` — add "Back up to Google Drive" toggle with sign-in prompt
- `AndroidManifest.xml` — re-add INTERNET and ACCESS_NETWORK_STATE permissions
- Data Safety form — update to note data stored in user's own Drive
- Privacy policy — mention Google Drive API usage

---

### Phase 3 — Server-Side AI Features (Requires Identity + Backend)

**What you build:**
- Server-side LLM integration for premium AI features
- Natural language spending queries ("How much did I spend on food last week?")
- AI-generated monthly narrative summaries
- Coaching chat interface
- Backend authentication via Google OAuth token verification
- Rate limiting and quota management per user

**What you do NOT build:**
- No PesaTrack user database for identity (Google token verification only)
- No storage of user financial data on PesaTrack servers (data sent per-request, not persisted)

**Why this third:**
- Requires both identity (Phase 2) and server infrastructure
- Has per-user marginal cost (LLM API calls)
- Needs Pro revenue to fund API costs
- Template-based insights from Phase 1 cover 80% of the value — this is the premium-premium upgrade

---

### Phase 4 — Adaptive Coaching Continuity (Future)

**What you build:**
- Persistent coaching/insight history linked to Drive-backed user state
- "Your spending trends over 2+ years" insights that survive device changes
- Optional advisor/household view (if household member signs in on their device with shared Drive folder)

**Constraint:** All data stored in **user's own Drive folder**, not PesaTrack infrastructure. PesaTrack acts as a reader/writer of a user-controlled file store.

---

## Part 5: User-Facing Messaging Framework

Every place identity or Pro is introduced must follow this copy pattern:

### Introducing Pro (Phase 1 — no sign-in)

> **Get smarter with your money.**  
> PesaTrack Pro gives you personalized spending advice, deep financial insights, and powerful tools.  
> Everything works offline. No account needed.  
> Monthly: KES 149 | Yearly: KES 999 — save 44%

### Introducing optional sign-in (Phase 2)

> **Want to keep your data safe if you change phones?**  
> Sign in with Google to back up automatically to your own Google Drive.  
> Your financial data never goes to PesaTrack's servers.  
> You can always use manual backup instead — Settings → Data Management.

### Dismissing sign-in

> **That's fine.** You can always enable it later in Settings → Account.  
> Everything works locally without signing in.

### After Pro cancellation

> **Your Pro subscription has ended.**  
> All your data is still here. Core tracking, budgets, and alerts continue to work.  
> Pro insights and recommendations are paused until you resubscribe.

### Never use:

- "Create an account to continue"
- "Sign in to access your data"
- "Your account stores your expenses"
- "Sign in to use Pro features"

---

## Part 6: Play Store & Privacy Policy Impact

| Change | Action required |
|--------|----------------|
| Phase 1 (Pro — Play Billing) | **No Data Safety changes.** No new data collected or shared. Minor note about purchase history (standard for all paid apps). Update store listing to mention Pro features. |
| Phase 2 (Drive backup) | Update Data Safety: data stored in user's own Google Drive. Update privacy policy to mention Google Drive API usage. Add INTERNET permission. |
| Phase 3 (Server AI) | Update Data Safety: financial data sent to PesaTrack server for AI processing (per-request, not stored). Update privacy policy with AI data handling section. |
| Phase 4 (coaching continuity) | Expanded Drive data types. Update policy section on what is stored in Drive. |

No phase requires building a PesaTrack user identity database. This deliberately keeps the legal surface minimal.

---

## Summary Decision Matrix

| Scenario | Requires account? | Requires Pro? | Why |
|----------|-----------------|--------------|-----|
| Track M-PESA expenses | ❌ Never | ❌ No | Core value |
| Set budgets and get alerts | ❌ Never | ❌ No | Core value |
| Basic analytics and forecasting | ❌ Never | ❌ No | Core value |
| Manual backup / restore | ❌ Never | ❌ No | Always available locally |
| Actionable spending recommendations | ❌ No | ✅ Pro | On-device computation, gated by Play Billing |
| Deep financial insights and coaching | ❌ No | ✅ Pro | On-device computation, gated by Play Billing |
| PDF financial reports | ❌ No | ✅ Pro | On-device generation, gated by Play Billing |
| Custom date range analytics | ❌ No | ✅ Pro | On-device computation, gated by Play Billing |
| Unlimited auto-rules | ❌ No | ✅ Pro | On-device, free tier = 10 rules |
| Google Drive auto-backup | ✅ Optional — Google OAuth | ✅ Pro | Needs Drive access |
| AI coaching chat and narratives | ✅ Optional — Google OAuth | ✅ Pro | Needs server-side LLM |
| Cross-device data continuity | ✅ Optional — Google OAuth | ✅ Pro | Needs Drive for data sync |

> **The product rule in plain language:**  
> Every feature is either free without an account, or gated only on Play Billing purchase (no account) or optional sign-in for user-benefit reasons.  
> PesaTrack never uses identity for business intelligence, advertising, or data processing.

---

## Changelog

| Date | Change |
|------|--------|
| 2026-04-18 | Reordered phases: Pro (Phase 1, no identity) now comes before Drive backup (Phase 2, needs identity). Added server-side AI as Phase 3. Updated decision matrix to include Pro column. Updated messaging framework for Pro. Added Pro feature details referencing `plans/pro-launch-plan.md`. |
