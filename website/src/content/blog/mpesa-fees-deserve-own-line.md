---
title: "Your M-PESA fees deserve their own line"
dek: "Transaction charges are real money. Here's what a year of them looks like — and why PesaTrack keeps them separate."
publishedAt: 2026-09-08
tags: ["awareness"]
author: "PesaTrack"
tldr: |
  M-PESA transaction fees are money leaving your account. PesaTrack tracks
  them in reserved category 606 so you can see the yearly total. A typical
  Kenyan spender lands around KES 8k–15k a year in fees. Numbers on the
  Analytics screens surface it — with visible assumptions, no advice.
---

## The invisible KES 22

You send KES 4,000 to a friend. Two SMS arrive: one for the send, one for the
`transaction cost`. Twenty-two shillings. Not a big deal.

Except that number happens every send, every till, every withdrawal, every M-Shwari
top-up. Add in the recipients you deal with a few times a week — landlord, matatu
station, your mum — and the fees compound quietly. A user we spoke to during
testing found KES 11,400 in fees over the last twelve months. Not because they were
careless. Because the number was never presented as one thing.

That's the problem PesaTrack was built for.

## What we do with fees

Every parser — M-PESA, NCBA — extracts the transaction cost from the SMS and
stores it as a **separate expense** in category `606`. Not tacked onto the parent
transaction. Not silently added to Transport or Groceries. Its own row, with its own
date, its own recipient (the mobile-money service).

```text
Sept 3   Send Money — Wanjiku       KES 4,000    (Transport? / Personal? — you decide)
Sept 3   Transaction cost           KES    22    (Category 606)
```

Two consequences:

1. **The category totals on Analytics are honest.** "Transport" doesn't quietly
   include KES 200 of fees you never noticed.
2. **You can see the fees as a single line.** Analytics → Category → 606 →
   *KES 11,400 over 12 months*. That number is now available for a decision.

## The illustration

Here's how the "what could this have been" math works. It's an illustration, not a
recommendation, and the assumptions are on the page.

- **Amount:** KES 11,400 (annual fees, hypothetical)
- **Rate:** 10% annual, compounded monthly
- **Horizon:** 5 years
- **One-time deposit** (not a recurring contribution)

`FV = 11400 × (1 + 0.10 / 12) ^ (12 × 5) ≈ KES 18,748`

If instead the same amount landed every year for 5 years, treated as five separate
one-year deposits:

`≈ KES 76,000 cumulative FV` (order-of-magnitude — actual figure depends on when in
the year the fees accrue)

We're not saying you'll get 10%. We're not saying this is guaranteed. We're saying
that unless the number is visible, you can't decide.

## What we won't do with this number

- We won't build a streak that "rewards" a low-fee month. Fees can spike because
  your income spiked; celebrating a low-fee week penalises normal cash flow.
- We won't push notifications the moment your fees cross a threshold. Real
  awareness is a monthly summary, not a live meter.
- We won't recommend a specific alternative service. If you decide bank transfers,
  in-app till payment, or cash for high-fee categories work better for you, great —
  that's your call, made from data.

## What we might do next

Two things on the [roadmap](/roadmap):

- A fees-by-payment-type breakdown ("your Send Money fees are 60% of the total").
- A fee-adjusted category view — "Transport, all in, was KES 8,200 not KES 8,000".

If you have a preference between the two, tell us at
[support](/support). We read every message.
