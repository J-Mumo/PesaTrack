---
title: "Importing an M-PESA PDF statement"
dek: "Backfill months or years of history in a single pass — entirely on-device."
section: "imports"
order: 50
updatedAt: 2026-09-15
---

## When to use this

You just installed PesaTrack and want your last few months of M-PESA history
inside the app, without waiting for new SMS.

Safaricom's official M-PESA statement (PDF) is the source of truth. PesaTrack
parses it on-device and inserts each transaction with the same
`transactionId`-based deduplication used for SMS — importing the same statement
twice is safe.

## Get the statement

1. Dial `*334#` → **M-PESA Statement** → **Full statement**.
2. Enter the M-PESA PIN.
3. Provide the email address to receive it on.
4. Open the email, save the attached PDF to your phone.

Safaricom charges a small fee for full statements. The email statement PDF is
password-protected — the password is emailed alongside.

## Import into PesaTrack

1. Open PesaTrack → **Settings → Import → From PDF statement**.
2. Tap **Choose file** and pick the PDF.
3. Enter the PDF password when prompted.
4. Review the parsed count and tap **Import**.

Everything happens on-device. The PDF is not uploaded anywhere.

## What gets imported

Every transaction row in the statement is parsed for:

- Amount (KES)
- Recipient name and paybill / till / phone number
- Date and time
- M-PESA transaction code
- Transaction cost (saved as its own expense in category 606)
- Payment type (Send Money, Buy Goods, Pay Bill, Withdrawal, Received, Deposit, Airtime)

## What if I import twice?

Safe. Deduplication is keyed on the M-PESA transaction code — no duplicate
expenses will be created. Categorisation runs on rows that are new to the DB.

## Excel imports

If you have kept your own spreadsheet, use **Settings → Import → From Excel**.
The importer expects at minimum columns `Date`, `Amount`, `Recipient`. An
optional `Category` column short-circuits auto-categorisation and uses the
label as-is.

## Troubleshooting

**"Invalid PDF password."** M-PESA statement passwords are case-sensitive. Copy
the password from the email rather than retyping.

**"No transactions parsed."** Safaricom occasionally changes the PDF layout. If
you get a fresh statement and the parser reports zero rows, email
[support](/support) with the app version and (if you can share it) a redacted
sample so we can update the parser.
