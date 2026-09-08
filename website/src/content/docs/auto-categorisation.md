---
title: "How auto-categorisation works"
dek: "Three passes decide which category each expense lands in. Here is the order, the fallbacks, and how to teach PesaTrack a recipient."
section: "using"
order: 30
updatedAt: 2026-09-15
---

## The three passes

When a new expense is parsed, PesaTrack runs three passes in order. The first
pass that matches wins.

### 1. Keyword rules

The `KeywordRulesEngine` matches the recipient name (or merchant) against a
built-in list of high-confidence keywords.

Examples:

- Recipient contains "Uber", "Bolt", "Little" → **Transport**
- Recipient contains "Naivas", "Carrefour", "Quickmart" → **Groceries**
- Recipient contains "KPLC" → **Utilities**

Rules are opinionated but conservative: only names with a single unambiguous
meaning are keyed. Ambiguous names fall through to the next pass.

### 2. Learned recipient mapping

If you have ever manually re-categorised an expense for a given recipient,
PesaTrack remembers. The next time that recipient appears, it is auto-tagged
with the same category.

This is how the app learns your habits. After one or two weeks of light
correcting, most of your recurring recipients auto-tag correctly.

You can review or clear learned mappings in **Settings → Categorisation**.

### 3. Payment-type heuristics

Fallback: if neither rules nor learning match, the payment type is used.

- Send Money → **Personal / Transfers**
- Buy Goods → **Shopping**
- Pay Bill → **Utilities / Bills**
- Withdrawal → **Cash**
- Airtime → **Airtime**
- Deposit → **Income** (may be re-labelled as Transfer once you set your income rules)
- Fuliza / M-Shwari → **Loans**

### 4. Unmatched

If none of the passes fire, the expense lands in `UNCATEGORIZED` with a
low-priority notification. Tap it to assign a category; PesaTrack learns that
recipient going forward.

## Excel-label mapping

If you import an Excel spreadsheet with a `Category` column, those labels
short-circuit the three passes and are used as-is. See
[Importing an M-PESA PDF statement](/docs/importing-mpesa-pdf-statement) for
Excel details.

## Fees are always category 606

Regardless of what the parent transaction lands in, transaction costs are
always saved to reserved **category 606 — M-PESA fees**. That way "Transport"
never quietly includes the fees you paid on rides. See the
[fees blog post](/blog/mpesa-fees-deserve-own-line) for the reasoning.

## Correcting a wrong category

Long-press any expense → **Edit category**. Two things happen:

1. The expense moves to the new category.
2. The recipient is added to your learned mapping. Future expenses to the same
   recipient auto-tag to the new category.

You can undo the learning in **Settings → Categorisation → Learned recipients**.
