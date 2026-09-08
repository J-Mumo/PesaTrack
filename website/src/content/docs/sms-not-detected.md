---
title: "SMS not detected"
dek: "Checklist for when a new M-PESA or NCBA SMS arrives but no expense appears in PesaTrack."
section: "troubleshooting"
order: 80
updatedAt: 2026-09-15
---

## Quick checks

Run through these in order. Each one takes under a minute.

### 1. Is the SMS permission granted?

**Android Settings → Apps → PesaTrack → Permissions → SMS**. If it reads
*Denied* or *Ask every time*, tap **Allow**.

Some Android updates reset SMS permission silently. If detection stopped after
an OS update, this is usually why. See
[Granting SMS permission](/docs/granting-sms-permission) for the full flow.

### 2. Is the sender exactly "MPESA" or "NCBA"?

PesaTrack matches on sender name. Safaricom occasionally routes some marketing
SMS through senders like `Safaricom` or `M-Sasa` — those are ignored on
purpose. Real transaction SMS come from `MPESA`.

Some phones display the sender in lowercase or with a suffix (e.g. `MPESA -
Safaricom`). Both still match. If the sender is something else entirely,
that message is not a transaction SMS.

### 3. Is battery optimisation restricting PesaTrack?

Xiaomi, Oppo, Huawei, Samsung (One UI), and some other vendors kill background
receivers aggressively. Fix:

**Android Settings → Apps → PesaTrack → Battery → Unrestricted**.

On MIUI/HyperOS phones, also toggle **Autostart** on for PesaTrack.

### 4. Send yourself a test

Send KES 10 to a friend (or yourself, if your line supports it). The SMS should
arrive, and within a second an expense should appear on the Home screen.

If the SMS arrives but no expense appears, the parser did not recognise the
format — proceed to the next section.

## The SMS arrived but no expense

If step 4 fails, the message body may be in an unrecognised format. Safaricom
and NCBA occasionally roll out new phrasings.

Please email [support](/support) with:

- The app version (**Settings → About**).
- The exact SMS body, redacting any sensitive names or numbers you don't want
  shared. Leave amounts and the transaction code — those help match the parser
  to the format.
- Your phone model and Android version.

Format changes are usually fixed in the next release.

## The SMS never arrived

If the SMS itself does not arrive in your messaging app, the issue is with
your network or SMS delivery — not PesaTrack. Contact Safaricom / NCBA.

## Related

- [Granting SMS permission](/docs/granting-sms-permission)
- [How auto-categorisation works](/docs/auto-categorisation) — a detected but
  uncategorised expense is a different problem.
