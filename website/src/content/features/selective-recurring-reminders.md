---
title: "Selective reminders (pending release)"
dek: "An upcoming update lets you choose reminders per detected payment, with rent and common utilities on by default."
order: 65
principle: nudge-not-nag
doesDo:
  - "In the upcoming update: provide individual detected-payment switches in Settings"
  - "Start Rent, Electricity, Water Bill, Home WiFi and Gas on when category support is at least 70%"
  - "Keep choices local and preserve them while the master switch pauses delivery"
doesNotDo:
  - "Initiate payments or prove that a missing payment is unpaid"
  - "Create manually scheduled bills or guarantee exact notification delivery"
  - "Send recipient identities or reminder choices to analytics"
---

**Pending release:** these controls are implemented for the next app update, not
yet available in the published Play Store release.

Other payments start off unless you select them. Separate paybill accounts can
be selected independently. Estimated reminders still need high confidence and
Android notification permission. This reduces unwanted reminders without
requiring a connection or adding a permission.

See the [guide](/docs/recurring-reminders) for selection, inference limits,
cooldown migration and backup behavior.