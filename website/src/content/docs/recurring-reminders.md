---
title: "Choose recurring-payment reminders (pending release)"
dek: "Select individual detected payments, keep automatic defaults, or pause all reminders without losing your choices."
section: "using"
order: 45
updatedAt: 2026-10-06
---

**Release status:** implemented locally for the next app update; not yet
published to Google Play. No deployment or version bump accompanies this guide.

## Choose payments

Open **Settings → Notifications → Recurring payment reminders → Choose payments**.
Search recipients or categories, then use each payment's switch. Rows show a
masked account hint, category, cadence, estimated KES amount and expected date.
“On by default”, “Off by default” and “Your choice” distinguish automatic defaults
from saved individual choices. **Use default** removes one choice; **Use defaults
for all payments** asks for confirmation and removes all choices, including ones
whose patterns are no longer detected. Neither resets cooldowns or the master switch.

Only Rent, Electricity, Water Bill, Home WiFi and Gas start on, and only when the
dominant category accounts for at least 70% of eligible occurrences. Uncategorized
payments count in that denominator. Other categories start off. A saved individual
choice takes precedence over category changes. Transaction fees are excluded
before detection, without being removed from normal spending analytics.

## Selected is not scheduled

Patterns need at least three payments. Medium-confidence patterns can be selected,
but cannot notify until confidence reaches 70%. The master switch pauses delivery
while leaving individual switches editable. Android app/channel permission and
per-payment/per-type cooldowns also apply. Opening Android notification settings
does not send a notification.

Expected dates and amounts are estimates from payment history. A missing payment
is not proof that a bill is unpaid. Checks are best-effort, about once daily, not
exact alarms. Missing payments are not silently advanced into a later cycle;
missing-payment reminders apply only after a two-day grace period.

## Identity and privacy limits

Separate accounts at the same paybill have separate choices. Categories, amounts,
due dates and inferred cycles do not form part of the preference identity. Where a
stored number/till exists, display-name edits do not change that identity. Name-only
records (including paybill businesses without a stored business number) depend on
the stored name; editing that identifying name can produce a new pattern. Multiple
independent bills at exactly the same account cannot yet be configured separately.
Choice identifiers are sensitive local metadata, not anonymization; they are not
logged or transmitted to telemetry or a backend.

## Upgrade, backup and reset

Existing master settings are preserved. Previously all high-confidence patterns
were considered; the upcoming update deliberately starts non-utility payments off.
An unambiguous legacy cooldown carries forward. If one old recipient splits into
multiple accounts with prior cooldown history, each waits a fresh cooldown cycle.

New raw database backups include the master switch and individual choices. Old
backups reset individual choices to category defaults while retaining the current
master switch. Unreadable or future-schema reminder metadata resets choices and
pauses reminders with a disclosed message. Cooldown timestamps are not backed up:
after any restore, delivery waits one cooldown cycle (cycle length minus two days,
at least one day; 28 days for monthly patterns). This conservative wait prevents a
catch-up burst, but can delay useful reminders.

Category reset preserves explicit choices. The existing debug-only **Clear All
Data** also removes individual choices and related cooldown keys, while retaining
master settings. Android's app-data deletion removes all app preferences. Backups
are not encrypted by PesaTrack; keep sensitive recipient metadata in a safe location.