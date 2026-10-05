# Selective recurring reminders — implementation note

Date: 2026-10-06. Branch: `main`. Status: implemented locally, pending release
and on-device QA. The approved plan remains an unchanged reference.

## Behavior and decisions

- Only category IDs 1009/1002/1012/1007/1004 auto-select, with dominant-category
  support ≥0.7. Uncategorized occurrences count in the denominator. Fee 606 is
  excluded before interval/amount analysis, not from ordinary spending analytics.
- One collision-safe, versioned identity uses normalized payment type plus stored
  recipient and, for paybills, stored business/account. Normalization trims,
  collapses whitespace, uppercases with `Locale.ROOT` and removes trailing periods,
  matching the existing mapping normalizer's intent without reusing its unsafe
  delimiter encoding. No category, amount, cycle or due date forms part of identity.
- Simple payments prefer stored numbers/tills. Where only names exist, editing
  the identifying stored name can change identity. Paybill parsers store accounts
  in `recipient` and business names in `recipientName`; where there is no stored
  business number, a business-name edit similarly changes the identifying name.
  Multiple independent bills at one exact account remain a future limitation.
- Atomic DataStore map preserves missing versus explicitly empty state. A missing
  entry uses category defaults; choices remain when patterns disappear. Corrupt
  maps fail safely to category defaults and cannot be overwritten except through
  an explicit confirmed reset. Master switches remain at their existing values.
- Worker and UI share the pure resolver. Worker refreshes detection, migrates
  throttles, rechecks selections/permissions before sending and records only a
  successful send. Cancellation propagates. Switches do not schedule/send work or
  erase cooldowns. Disabling dismisses current notifications where possible.
- Unambiguous old cooldowns carry their timestamp. An ambiguous recipient split
  with prior history waits a fresh per-type cooldown cycle. Migration is idempotent.
- Backup metadata writes use SQL bind parameters. Reminder metadata has version,
  count, size, identity and enum validation; unsupported schemas are rejected as
  a whole. New backups restore master/map in one edit. Old backups (including no
  metadata table) retain current master and reset choices. Invalid metadata pauses
  reminders, resets choices and discloses the fallback in the restore UI.
- Cooldowns are not backup preferences. Any restore clears old throttle keys and
  applies a local restore floor: one usual cooldown (cycle days minus two, at least
  one day; monthly 28 days, yearly 363 days). This prevents catch-up bursts but may
  delay useful notifications. Debug-only clear-data clears choices/floors/throttles
  while preserving master settings. Category reset leaves explicit choices alone.
- Dedicated screen follows Screen/ViewModel/UiState + Hilt/StateFlow/navigation.
  No dialog deviation, Home CTA, app network call, new permission, dependency,
  Room migration or version change. No choices/identifiers sent to telemetry.

## Feature decision filter

Serves nudge-don't-nag, awareness before action, privacy and local-first. Changes
selection/awareness behavior, not payments. Honest failure modes: categorization
or name-only identity errors, recurrence not proving unpaid bills, and OS/cooldown
delivery delays. Success: independently persisted account-level choices and fewer
unwanted notifications.

## Exact implementation files

Android production:

- [ExpenseDao.kt](../android/app/src/main/java/com/pesatrack/data/local/database/dao/ExpenseDao.kt)
- [AppPreferences.kt](../android/app/src/main/java/com/pesatrack/data/local/preferences/AppPreferences.kt)
- [AppModule.kt](../android/app/src/main/java/com/pesatrack/di/AppModule.kt)
- [RecurringExpense.kt](../android/app/src/main/java/com/pesatrack/domain/models/RecurringExpense.kt)
- [NavGraph.kt](../android/app/src/main/java/com/pesatrack/presentation/navigation/NavGraph.kt)
- [Screen.kt](../android/app/src/main/java/com/pesatrack/presentation/navigation/Screen.kt)
- [SettingsScreen.kt](../android/app/src/main/java/com/pesatrack/presentation/screens/settings/SettingsScreen.kt)
- [SettingsViewModel.kt](../android/app/src/main/java/com/pesatrack/presentation/screens/settings/SettingsViewModel.kt)
- [RecurringRemindersScreen.kt](../android/app/src/main/java/com/pesatrack/presentation/screens/recurring_reminders/RecurringRemindersScreen.kt)
- [RecurringRemindersViewModel.kt](../android/app/src/main/java/com/pesatrack/presentation/screens/recurring_reminders/RecurringRemindersViewModel.kt)
- [RecurringRemindersUiState.kt](../android/app/src/main/java/com/pesatrack/presentation/screens/recurring_reminders/RecurringRemindersUiState.kt)
- [RecurringReminderPolicy.kt](../android/app/src/main/java/com/pesatrack/services/RecurringReminderPolicy.kt)
- [RecurringExpenseService.kt](../android/app/src/main/java/com/pesatrack/services/RecurringExpenseService.kt)
- [RecurringReminderWorker.kt](../android/app/src/main/java/com/pesatrack/services/RecurringReminderWorker.kt)
- [NotificationHelper.kt](../android/app/src/main/java/com/pesatrack/services/NotificationHelper.kt)
- [DataManagementService.kt](../android/app/src/main/java/com/pesatrack/services/DataManagementService.kt)
- [SampleDataService.kt](../android/app/src/main/java/com/pesatrack/services/SampleDataService.kt)

JVM tests:

- [RecurringReminderPolicyTest.kt](../android/app/src/test/java/com/pesatrack/services/RecurringReminderPolicyTest.kt)
- [RecurringExpenseServiceTest.kt](../android/app/src/test/java/com/pesatrack/services/RecurringExpenseServiceTest.kt)
- [RecurringReminderDeliveryTest.kt](../android/app/src/test/java/com/pesatrack/services/RecurringReminderDeliveryTest.kt)
- [RecurringReminderPreferencesTest.kt](../android/app/src/test/java/com/pesatrack/data/local/preferences/RecurringReminderPreferencesTest.kt)

Documentation/site:

- [Implementation status](implementation-status.md)
- [This implementation note](selective-recurring-reminders-implementation.md)
- [Reminder FAQ](../website/src/content/faq/recurring-reminders.md)
- [Backup FAQ](../website/src/content/faq/backup-restore.md)
- [Kiswahili FAQ](../website/src/pages/sw/faq.astro)
- [Security](../website/src/pages/security.astro)
- [Reminder guide](../website/src/content/docs/recurring-reminders.md)
- [Backup guide](../website/src/content/docs/backup-and-restore.md)
- [Feature page](../website/src/content/features/selective-recurring-reminders.md)

## Verification and release gates

- Final Android `testDebugUnitTest` + `lintDebug`: **passed**, exit 0, BUILD
  SUCCESSFUL (final post-review run: 2m 58s). Includes 22 new JVM tests across the four new test classes,
  plus the existing suite. Debug production/test compilation and Hilt generation
  also passed. No Android dependency was added.
- New JVM tests cover exact defaults, support/confidence boundaries, overrides,
  master pause, normalization/locale/delimiter collisions, name-only fallback,
  account splitting, pre-analysis fee removal, recategorization, versioned codec,
  malformed/future metadata, real DataStore concurrent edits, reset/clear and
  conservative cooldown migration. Existing calendar/grace/DST tests remain.
- Website `check` + `build`: **passed**, both exit 0; Astro checked 46 files with
  0 diagnostic errors/warnings/hints and built 44 pages plus Pagefind. The initial
  content sync emitted two transient duplicate-ID notices for updated entries;
  the following build sync did not repeat them. Site sync required and included;
  FAQ's Kiswahili mirror updated. No privacy data-flow claim changed. Factsheet
  still derives the published version and lists MPESA/NCBA and opt-in internet.
- `git diff --check`: **passed**. `adb devices` found **no connected device**.
- Before release: on-device row switch persistence/restart, master pause,
  denied Android permission/blocked channel, disabled-payment non-delivery,
  actual raw SQLite SAF backup/restore and notification dismissal. JVM tests do
  not substitute for Compose/device or SAF/SQLite integration tests.
- Existing Room schema-export/Hilt incremental and UI deprecation warnings are
  outside this change. Deployment remains explicitly deferred.