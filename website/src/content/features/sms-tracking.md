---
title: Automatic SMS tracking
dek: PesaTrack detects M-PESA and NCBA bank SMS the moment they arrive and turns them into itemised expenses — no typing, no exports.
order: 10
icon: sms
principle: awareness-before-action
doesDo:
  - Read incoming and outgoing M-PESA transaction SMS (sender "MPESA")
  - Read NCBA bank SMS for card, funds-transfer, and receive transactions
  - Extract amount, recipient, till/paybill, date, and M-PESA transaction code
  - Save each transaction to the local Room database with a unique transaction ID (no duplicates)
  - Detect transaction costs and save them as a separate line item in category 606
  - Auto-categorise using keyword rules, learned recipient mappings, and payment-type heuristics
doesNotDo:
  - Read personal SMS from contacts, marketing SMS, or unrelated senders
  - Send SMS content, amounts, or recipient names off the device — ever
  - Modify or delete the original SMS
  - Work without SMS permission — you can still enter expenses manually
---

## What "automatic" actually means

When an M-PESA or NCBA SMS arrives, the `SmsReceiver` runs the correct parser
against the body, extracts the fields, and inserts a row into the local
database. There is no round-trip to a server — parsing happens on-device in
milliseconds. Duplicate detection uses the transaction ID (`transactionId`),
so re-parsing the same SMS never creates two rows.

## Supported senders today

- **MPESA** — Send Money, Buy Goods, Pay Bill, Withdrawal, Received, Deposit,
  Airtime, Fuliza, M-Shwari.
- **NCBA** — Card purchases, funds transfers, credits, and Loop wallet.

More banks land as they're requested and their SMS formats are stable —
see [Roadmap](/roadmap).

## NCBA capture fixes — pending app release

Enable **NCBA Bank** in Settings → SMS Sources; bank tracking is off by default
and SMS permission is required. Historical SMS import uses the enabled sources too.

The next app release recognises successful direct **Kenya Power Prepaid**
confirmations as electricity paybill expenses, with the meter number and bank
reference. The companion generic debit is not a second expense. Token numbers
and units are not copied into expense notes.

For card approvals in **KES**, the approval amount stays authoritative, even
without a companion debit. Live capture and historical import share conservative
pairing: only recognised NCBA card-reference debits within ten minutes, exact
KES amount equality, and a unique match in both directions. Service/M-PESA
references are not attached to card purchases. Card IDs remain stable when a
debit arrives later, so re-import does not duplicate the purchase.

Foreign-currency approvals need an unambiguous companion **KES debit**; USD and
other currencies are never treated as KES. If no safe match is available, import
reports the skipped approvals. You can retry after the debit arrives or enter the
KES amount manually. Unknown reference formats, ambiguous or delayed alerts can
remain unpaired. A later debit retries live capture only if the approval is
already in the inbox; historical import is the recovery path if inbox timing
prevents this. Existing incorrect expenses are not automatically rewritten.
