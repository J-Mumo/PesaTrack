---
title: "PesaTrack and your SMS: exactly what we read, exactly what we don't"
dek: "The plain-English breakdown of which SMS PesaTrack looks at, what happens on the device, and what leaves it. Spoiler: transaction data doesn't."
publishedAt: 2026-09-08
tags: ["privacy"]
author: "PesaTrack"
tldr: |
  PesaTrack requests READ_SMS and RECEIVE_SMS. The receiver only processes
  MPESA and NCBA. Parsing runs on-device; amounts, recipients, categories,
  and notes are never transmitted. INTERNET is used only for opt-in Firebase
  Analytics (off by default) — event names, not content.
---

## Why we're writing this post

The single most common question about PesaTrack — from journalists, from users,
from AI answer engines quoting the app — is *"what does it actually do with my SMS?"*
The answer is on the [Privacy page](/privacy) and it's boring, which is the point.
This post is the story version, for readers who want to see the reasoning.

## The three questions we get asked

### 1. Does it read all my SMS?

No. The Android `SmsReceiver` — the class that runs when a text arrives — checks
the sender name **before** the message body is passed to a parser. If the sender
isn't `MPESA` or `NCBA`, the receiver ignores it and the message body is never
inspected. Marketing SMS, OTPs from other services, texts from contacts — all
ignored at the receiver level.

We could make the receiver more clever ("look at the body, guess the sender"), but
we chose not to. A strict sender allow-list is the honest guarantee. Anything more
liberal would need caveats we don't want to write.

### 2. Does anything leave my phone?

Your **transaction data** — amounts, recipients, category names, budgets, notes —
does not leave the device. It lives in a private Room database inside the app's
sandbox. Other apps on your phone can't read it, and nothing in the code ever
transmits it.

The app **does** hold the `INTERNET` permission. We were transparent about adding
it in v1.5.0 and we'll be transparent here: it exists for **opt-in anonymous
Firebase Analytics**. That's off by default. If you never toggle it on, the
socket is never opened. If you do toggle it on, what we send is:

- Screen-open events (`"opened analytics"`, `"opened budgets"`)
- Feature-use events (`"import_completed"`, `"budget_created"`)
- App version, Android version, device model, country
- Crash and error signals

What we don't send, ever:

- SMS content
- Transaction amounts, recipients, transaction codes, categories, notes
- Anything that could identify you personally
- Your phone number, M-PESA number, email, name

If that trade-off doesn't sit right, leave analytics off. The app works the same.

### 3. What if I uninstall?

The database is inside the app's sandbox. Android deletes both when you uninstall.
There's no server-side profile to close because there is no server-side profile.

## Why we designed it this way

Two principles from [our about page](/about) drove the shape of this:

- **Privacy is non-negotiable.** No feature that requires sending raw SMS or PII
  off-device without explicit, revocable consent.
- **Honest numbers.** That includes the honesty about what we do send when you
  opt in.

We could have shipped a "cloud sync" version that gave us richer analytics,
better crash reports, and cross-device continuity. We chose not to. If we ever
do offer sync it will be a separate, opt-in, revocable product — not a silent
update.

## The audit trail

If you want to verify any of this:

- [AndroidManifest.xml](https://github.com/J-Mumo/PesaTrack) lists every permission
  the app requests.
- The [`SmsReceiver`](https://github.com/J-Mumo/PesaTrack) class is the only entry
  point for SMS content.
- The [Privacy page](/privacy) is the same policy Google Play links to.
- The [factsheet](/factsheet.json) is the machine-readable version of the above.

If you spot a mismatch between any of those, we want to hear about it.
Email [support](/support) with "PRIVACY AUDIT" in the subject.
