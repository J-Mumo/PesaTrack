# Selective recurring-payment reminders

**Created:** 2026-10-06  
**Status:** Proposed — implementation not started  
**Base:** `main`, after recurring-reminder repair `3401446`  
**Scope:** Local reminder selection only; no cloud, payment initiation, new permission, or deployment.

## 1. Goal and confirmed intent

Let users turn reminders on/off for **individual detected recurring payments**, not just the whole feature or a whole category. Start with rent and common utilities ON; all other payments OFF unless the user explicitly enables them. Preserve the existing master switch as an overriding pause.

Examples:
- Rent to a landlord: ON by default; can individually turn OFF.
- Electricity and water bills: ON by default; either can individually turn OFF.
- Netflix, groceries, transport, transfers, savings contributions: OFF by default; user may enable a specific detected payment.
- Turning the master switch OFF pauses all reminders without losing individual choices.

This does not create manually scheduled bills. An ON setting cannot manufacture a recurrence pattern or bypass the existing high-confidence requirement (0.7), two-day overdue grace, Android notification permission, or per-type cooldowns.

## 2. Default policy (proposed exact allow-list)

Match built-in category IDs, not names or all of parent group 10. Names are editable; the Home & Utilities group also contains repairs, furnishing, gardening and appliances.

| Built-in category | ID | Initial individual reminder state |
|---|---:|---|
| Rent | 1009 | ON |
| Electricity | 1002 | ON |
| Water Bill | 1012 | ON |
| Home WiFi | 1007 | ON |
| Gas | 1004 | ON |
| All other categories, custom categories, uncategorized | — | OFF |
| M-PESA transaction fees | 606 | Ineligible; never offer a fee-only reminder |

These IDs are defined in [CategoryEntity.kt](../android/app/src/main/java/com/pesatrack/data/local/database/entities/CategoryEntity.kt). WiFi and gas are proposed utility defaults; they must still satisfy recurrence detection. No whole-group inheritance and no matching by recipient name (e.g. a merchant called “Rent” is not sufficient).

For mixed-category histories, auto-enable only when the inferred dominant category is on the allow-list **and at least 70% of the eligible occurrences have that same category**. Count uncategorized occurrences in the denominator. The existing detector currently selects a modal category without exposing that support ratio; add it for the policy rather than assuming the category is reliable. An explicit individual override can enable a mixed/unknown-category pattern.

## 3. Source inspection and identity prerequisites

- [RecurringReminderWorker.kt](../android/app/src/main/java/com/pesatrack/services/RecurringReminderWorker.kt) currently checks only the master preference and all high-confidence patterns.
- [AppPreferences.kt](../android/app/src/main/java/com/pesatrack/data/local/preferences/AppPreferences.kt) stores the master switch (default ON) and cooldown timestamps, not individual overrides.
- [ExpenseDao.kt](../android/app/src/main/java/com/pesatrack/data/local/database/dao/ExpenseDao.kt) currently groups recurrence candidates using `COALESCE(recipientName, recipient)`. It does **not** use the more specific paybill/account mapping identity. Aggregator accounts can consequently collapse into one pattern.
- [RecurringExpenseService.kt](../android/app/src/main/java/com/pesatrack/services/RecurringExpenseService.kt) currently produces one pattern per recipient group. Individual selection must use the same canonical identity for detection, UI, preference lookup and notification throttling.

### Identity design

Add a versioned local recurring-payment identity (`recurring_identity_v1`) based on normalized payment type plus recipient identity; include the paybill account when applicable. Inspect actual stored recipient/account conventions and reuse the existing mapping/merchant identity normalizer where compatible. Never concatenate unescaped user-derived values with a delimiter; use a structured encoding or collision-safe serialization.

Rules:
- Two accounts behind the same aggregator must have different identities and switches.
- Identity excludes category, inferred amount, display name and due date: editing these should not discard the user's choice. For simple recipients, prefer a stable stored number/till if available; explicitly document name-only fallback and how edits affect it.
- Cycle is not part of the v1 preference identity: changing an inferred cycle should not discard an individual choice. Supporting multiple independent bills to the same account requires a separate future user-defined bill identity; do not claim v1 handles that.
- Display the recipient name and a masked account hint when necessary to distinguish rows; do not show raw identity strings or log them.
- Local identifiers are not anonymization. Never send override identifiers or values to telemetry/backend; backups may contain sensitive recipient metadata.
- Filter fee category 606 out of reminder detection candidates **before** grouping and interval/amount analysis; a post-detection filter alone cannot undo fee contamination of a mixed group. Do not recategorize or remove fees from normal analytics.

No Room schema change is expected if the identity/category ratio can be derived from existing columns. If an actual schema change is necessary, provide an explicit migration; no destructive fallback.

## 4. Preference model and precedence

Store a versioned DataStore override map: `paymentIdentity -> ENABLED | DISABLED`. Missing entry means **USE DEFAULT**, not false. Use a single atomic serialized value or atomic disjoint sets; validate schema and values. Provide Flow and snapshot APIs plus set/remove/reset operations through `AppPreferences` (or a narrowly scoped injected store wrapping it).

Shared pure policy resolver, used by both Settings and the worker:

1. Master OFF -> no delivery (individual selections remain stored).
2. Fee-only/ineligible candidate -> no delivery.
3. Explicit individual DISABLED -> no delivery.
4. Explicit individual ENABLED -> selected, still subject to detection confidence and delivery gates.
5. No override -> conservative category allow-list and category-support threshold.
6. Worker applies high-confidence, date/grace, permission/channel and cooldown checks.

Do not populate an explicit override for every detected payment: that would freeze automatic category defaults. Preserve absent vs intentionally empty override-map semantics. Retain choices when a pattern temporarily disappears from the detection window; do not silently delete them or auto-enable other payments.

### Upgrade and identity/throttle migration

- Preserve the existing master preference exactly, including explicit OFF. Missing master preference still defaults ON.
- No historical individual selections exist, so a missing override map starts with rent/utilities defaults and everything else OFF. This is intentionally less noisy than the previous all-patterns behavior; explain in Settings without a push announcement.
- Changing category recalculates the default only when no override exists. A saved individual choice wins, even after category changes.
- Do **not** clear cooldowns when disabling/re-enabling a payment or the master switch. No immediate notification from switch interaction.
- Identity refinement must not reset notification cooldowns unexpectedly. Carry forward a legacy recipient cooldown only for an unambiguous mapping; ambiguous paybill splits require a conservative cooldown floor or explicit migration decision to avoid a burst. Test this before activating the new policy.
- Resetting an individual choice removes its override; “Use defaults” removes overrides after confirmation and leaves the master switch unchanged. Re-check eligibility at send time after refresh; disabling should also dismiss existing notifications for that payment where possible, without resetting cooldown history.

## 5. Settings UX

Extend the existing **Settings -> Notifications -> Recurring payment reminders** card with “Choose payments.” Use a dedicated Settings-linked screen (recommended for long lists) with `*Screen`, Hilt `*ViewModel`, `*UiState`, StateFlow, and a navigation route consistent with existing screens. No new Home CTA or proactive insight.

Each detected-payment row shows:
- Recipient + masked account hint if needed.
- Category, cadence and estimated amount (e.g. `KES 12,450`), expected date when known.
- Switch showing the effective individual selection, and source text “On by default: Rent” / “Off by default” / “Your choice.”
- Optional “Use default” action when overridden. “ON” means selected, not guaranteed delivery.

Provide search and optionally All/On/Off filters. Master OFF must not erase or misrepresent the switches; show “All reminders paused” and keep selections editable. Explain blocked Android notifications and provide the existing platform settings recovery pattern if appropriate. Show medium-confidence patterns with “Not enough confidence to notify yet”; never bypass confidence because the switch is ON.

Copy: “Rent and utilities start on. Other detected payments start off. You choose which reminders to receive.” Include “Estimated from payment history. A missing payment is not proof that a bill is unpaid.” WorkManager remains best-effort, roughly daily, not an exact scheduled alarm.

Empty state: “No recurring payments detected yet. Patterns need at least three payments.” No invented sample bills. Loading/error must preserve stored overrides and existing state; failure to parse preferences must not enable non-default payments.

## 6. Backup, restore and deletion

The current [DataManagementService.kt](../android/app/src/main/java/com/pesatrack/services/DataManagementService.kt) embeds only selected settings into the raw database backup metadata. Extend it to back up the master preference and versioned individual overrides; add bounded validation and parameterized writes (no interpolation of merchant identities into SQL).

- New backups restore selections atomically; malformed/future-schema data yields a disclosed safe fallback rather than a partially applied map.
- Old backups without these fields retain the established master behavior and reset individual overrides to automatic category defaults (no selections carried over from an unrelated current database). Define this explicitly in restore tests/copy.
- Do not back up notification cooldown timestamps as user preferences; document restore delivery behavior and prevent an immediate catch-up notification burst.
- “Clear all data” clears per-payment overrides and related throttle keys so stale financial identifiers do not survive a reset. Preserve or reset the master toggle according to existing clear-data semantics, documented in tests.
- Category reset is not equivalent to resetting individual selections; preserve explicit choices unless the user specifically resets them.

## 7. Implementation slices

- [ ] **A — Identity and policy:** inspect account storage, centralize canonical identity, exclude fee candidates, expose dominant-category support and implement/test the pure resolver.
- [ ] **B — Preferences and migration:** atomic override APIs, absent-vs-empty behavior, retain master OFF, throttle migration and reset behavior.
- [ ] **C — Worker:** apply the resolver before reminders, do not mark disabled/blocked delivery as sent; same policy as UI, cancellation still propagated, no category-level shortcut replacing individual choices.
- [ ] **D — UI/navigation:** detected-payment list, individual switches, default-source labels, reset/search/empty state, master pause and permission explanation.
- [ ] **E — Backup/restore:** versioned metadata, safe parsing and old-backup semantics; no new Room table unless justified.
- [ ] **F — Docs/site and verification:** update implementation status, feature/navigation structure and website copy; tests/lint/build plus on-device checks before release.

Keep deployment deferred until the maintainer's wider bug-fix release is ready. No automatic commit, push, deployment, visibility change or version bump from this planning task.

## 8. Tests and acceptance

Required tests:
- Every allow-listed built-in category defaults ON; all others/custom/unknown OFF; fee 606 never a candidate.
- Mixed-category support below 70% defaults OFF; explicit ON still requires confidence ≥0.7.
- Individual OFF beats rent default; ON enables a non-default payment; removing override restores category defaults.
- Master OFF wins and preserves choices; same resolver output for UI and worker.
- Two paybill accounts never share identity/override/throttle; normalize whitespace/case consistently and test delimiter collisions, name-only fallback and recategorization.
- Disabled/permission-blocked candidates do not consume cooldown; toggling cannot cause repeat reminders; migration does not resend previously throttled payments.
- Existing date/grace/DST tests remain green; unpaid date is not silently rolled forward.
- Backup round-trip, old backup, malformed map, schema upgrade, clear-data and category-reset behavior.
- Compose/device: row-level switch persists after restart, master pause, Android notification permission denied/channel blocked, no notification for a disabled payment.

Acceptance: With detected rent, electricity and Netflix patterns, rent/electricity default ON and Netflix OFF; user can enable Netflix, disable rent and leave electricity ON. Separate accounts under one aggregator can be configured independently. All behavior works offline and preferences survive supported backup/restore without exposing data externally.

## 9. Product principles and website sync

Serves **nudge, don't nag**, **awareness before action**, **privacy**, and **local-first**. Behavior change: users select relevant reminders instead of receiving every inferred pattern. Honest downside: wrong categorization/recipient identity can affect defaults; recurrence inference cannot prove an unpaid bill and OS scheduling can delay delivery. Success is observable through persistent individual selections and fewer unwanted notifications.

**Site update required during implementation:** explain defaults and the new control in the appropriate public docs/FAQ and security page, update mirrored FAQ copy if touched, and verify factsheet claims stay accurate. No new data transmission or permission is proposed; do not change privacy-policy data-flow claims unless implementation actually changes them. Record pending-release status accurately until deployed.