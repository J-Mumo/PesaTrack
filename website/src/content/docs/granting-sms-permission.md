---
title: "Granting SMS permission"
dek: "Why PesaTrack asks for the SMS permission, what it does with it, and how to grant, revoke, or troubleshoot it."
section: "getting-started"
order: 20
updatedAt: 2026-09-15
---

## Why the permission is needed

PesaTrack is a **passive expense tracker**. It has to be able to read incoming
M-PESA and supported bank SMS to detect transactions the moment they happen.
Without SMS permission, automatic tracking cannot work.

The app requests two Android permissions:

- **RECEIVE_SMS** — deliver each incoming SMS to PesaTrack so its parser can
  check the sender and body.
- **READ_SMS** — read historical SMS from your inbox when you tap
  **Settings → Import from SMS**.

## What the permission is *not* used for

- Reading SMS from contacts or personal chats.
- Reading marketing SMS, OTPs, or non-financial senders.
- Sending, deleting, or modifying any SMS.
- Sharing SMS content with any server or third party.

The SmsReceiver checks the sender name first. Only `MPESA` and `NCBA` messages
are passed to the parser. Everything else is ignored at the receiver level.
See [the privacy write-up](/blog/pesatrack-and-your-sms) for the full audit.

## Granting the permission

On first launch PesaTrack shows the Android permission prompt. Tap **Allow**.

If you tapped **Deny** by mistake, grant it later at:

**Android Settings → Apps → PesaTrack → Permissions → SMS → Allow.**

Once granted, the next M-PESA SMS you receive will appear in the Home screen
within a second.

## Revoking the permission

You can revoke at any time from the same Android Settings screen. When revoked:

- Historical expenses stay in the app; no data is deleted.
- New M-PESA SMS are no longer detected automatically.
- You can still add expenses manually or import from PDF / Excel.

## Troubleshooting

**"I granted the permission but nothing appears."** Some phones aggressively kill
background receivers. Open **Android Settings → Apps → PesaTrack → Battery →
Unrestricted**, then send yourself a KES 10 M-PESA test. See
[SMS not detected](/docs/sms-not-detected) for a full checklist.

**"Android keeps re-prompting on updates."** Newer Android versions occasionally
reset SMS permission after a system update. If detection stops working after
an update, re-check Permissions → SMS.
