---
title: "Setting month starts on"
dek: "Align every monthly view — budgets, analytics, savings-rate, income — to the day your money actually lands."
section: "using"
order: 40
updatedAt: 2026-09-15
---

## Where to find it

**Settings → Month starts on → pick a day (1–28).**

Default is 1 (calendar month). The cap is 28 so the setting is unambiguous in
every calendar month.

## What changes when you set it

Every screen that displays a monthly period uses the same offset-aware bounds:

- **Budgets.** Period label reads e.g. *"Aug 25 – Sep 24, 2026"*. Remaining
  amount and days left use those bounds.
- **Analytics → Charts → Monthly.** The arrow buttons step by your offset
  month. The trend chart shows the last 6 offset months. Category, top-spender,
  and payment-type breakdowns are offset-aware.
- **Home savings-rate card.** *"This month you kept X% of what came in"* uses
  offset totals.
- **Income screen.** Monthly totals align to your budget cycle.
- **CSV export.** The `month starts on` setting controls the period you export.

If you never change the setting, PesaTrack behaves as it did before v1.4.1 —
calendar-month boundaries.

## What doesn't change

- **Individual expenses.** Each expense keeps the date it happened.
- **Year-to-date totals.** These still use the calendar year, for tax-report
  friendliness.
- **Category 606 fees.** Fees follow the same offset behaviour as everything else.

## Edge cases

**Months without your chosen day.** If you pick 28 and a month has only 28
days (February in a non-leap year), the period simply ends on day 27 of the
next month. No missing or duplicated days.

**"Pay on the last calendar day of the month."** Not currently supported as a
single setting. If you need it, email [support](/support) and we will track
demand for a "last day of month" mode.

**Retroactive re-labelling.** Changing the setting re-labels every past period
using the new bounds. Historical data is not modified — only how it is grouped.

## Related reading

- Blog: [The 'month starts on' setting, and why we built it](/blog/month-starts-on-setting)
- FAQ: [Why does the budget month not match the calendar month?](/faq)
