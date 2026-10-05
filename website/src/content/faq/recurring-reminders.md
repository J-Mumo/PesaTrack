---
question: Can I choose which recurring payments have reminders?
group: troubleshooting
order: 35
anchor: recurring-reminders
---

**Pending release — these individual controls are implemented for an upcoming
app update, not yet published to Google Play.**

Go to **Settings → Notifications → Recurring payment reminders → Choose payments**.
Rent, Electricity, Water Bill, Home WiFi and Gas start on only when at least 70%
of the detected payment history supports that category. Other detected payments
start off. Switch each payment on or off, or choose **Use default**. Your choice
overrides later category changes. Separate paybill accounts have separate choices;
account hints are masked.

The master switch pauses all reminders without deleting your choices. A selected
payment still needs at least three payments and high detection confidence (70%)
before it can notify. Android must allow both app notifications and the Recurring
Reminders channel. Checks are roughly daily and can be delayed; a missing payment
is not proof that a bill is unpaid. Transaction fees never generate reminders.

No payment is initiated. Choices remain local and are never sent to analytics.
See the [reminder guide](/docs/recurring-reminders) for backup and reset behavior.