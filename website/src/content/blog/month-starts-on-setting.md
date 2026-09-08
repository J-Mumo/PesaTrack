---
title: "The 'month starts on' setting, and why we built it"
dek: "If your payday is the 25th, calendar months don't match your budget cycle. Here's how PesaTrack aligns everything to the day money actually lands."
publishedAt: 2026-09-08
tags: ["guides"]
author: "PesaTrack"
tldr: |
  Settings → Month starts on lets you pick the day your budget month begins.
  Budgets, Analytics Monthly charts, the savings-rate card, Income totals,
  and CSV exports all use the same offset-aware period. Cap is 28 to avoid
  the short-month ambiguity.
---

## Why calendar months are wrong for most people

Kenya runs on payday. Salaries land around the end of the month; some employers pay
mid-month; some run the 25th. Whichever it is, your **budget cycle** starts when
your money lands and ends the day before the next arrival.

Calendar months don't know about payday. If you're paid on the 25th and your rent
posts on the 1st, a calendar-month view breaks the rent off from the salary that
paid it. Your "November" spend looks weirdly high because it caught two rent
cycles and one salary. You end up scrolling three months of history to reason
about a single pay cycle.

PesaTrack fixes this at the source. Set your month-start day and every screen
that shows "this month" agrees.

## How to set it

**Settings → Month starts on → pick a day (1–28).**

We cap at 28 because 29, 30, and 31 don't exist in every month and the ambiguity
isn't worth it. (See the edge case below.)

## What actually changes

Every surface that displays a monthly period uses the same offset-aware bounds:

- **Budgets.** The period label reads *"Aug 25 – Sep 24, 2026"*, not
  "September 2026". Remaining amount and days left are calculated against those
  bounds.
- **Analytics → Charts → Monthly.** The period selector arrow buttons step by
  offset month, not calendar month. The 6-period trend chart uses the same
  bounds. Category breakdowns, top spenders, payment-type splits — all offset-aware.
- **Home screen savings-rate card.** *"This month you kept 18% of what came in"*
  uses the offset-aware totals.
- **Income screen.** Monthly totals align to your budget cycle.
- **CSV export.** The bounds you export against are the same bounds you see in
  the app.

If you never touch the setting, the default is the 1st — behaviour identical to
what PesaTrack did before v1.4.1.

## The one edge case

If you pick the 28th and a month has 30 days, no problem — the next period runs
28 → 27. But **for months where your chosen day doesn't exist** (like the 31st in
February) we cap at 28. That's why the setting maxes at 28.

If you're paid on the last calendar day of the month regardless of length, tell
us at [support](/support). "Pay on the last day" is a distinct configuration and
we're tracking demand for it separately.

## What this doesn't change

- **Individual expenses.** Each expense keeps the date it happened. We don't
  retroactively shift anything.
- **Year-to-date totals.** Those still use the calendar year for tax-report
  friendliness.
- **Category 606 fees.** Same offset behaviour as everything else. Fees for a
  send on the 27th land in the correct cycle.

## Why we shipped this

Two principles again. **Awareness before action** — you can't decide about your
spend if the number in the app doesn't match how you actually think about your
money. **Honest numbers** — a monthly total that mixes two paydays is not the
number you want, no matter how neat the label looks.

If the setting saved you scrolling — or you'd like a variant that isn't a fixed
day — write in.
