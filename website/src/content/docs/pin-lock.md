---
title: "PIN lock and biometric unlock"
dek: "Prevent casual access to your PesaTrack expense history with an optional 4- or 6-digit PIN, plus fingerprint or face unlock."
section: "privacy-security"
order: 70
updatedAt: 2026-09-15
---

## What the PIN protects against

The PIN lock protects against **casual access** — someone else picking up your
unlocked phone and opening PesaTrack out of curiosity. It is not a
forensic-level control and does not encrypt the underlying database. See the
[Security page](/security) for the full threat model.

If your phone itself is not locked with a screen lock, add one first. PesaTrack's
PIN complements the device screen lock; it does not replace it.

## Enabling the PIN

1. **Settings → Security → PIN lock → Enable**.
2. Pick a 4-digit or 6-digit PIN. 6 digits is recommended.
3. Confirm by entering the same PIN again.

The PIN is stored as a **salted SHA-256 hash** in DataStore. The raw PIN is
never persisted.

## Enabling biometrics

Once a PIN is set, you can enable **Fingerprint / Face unlock** in the same
Security screen. This uses Android's `BiometricPrompt` API. Your biometric
templates never touch PesaTrack — Android brokers the check.

Biometrics are a convenience layer over the PIN. The PIN remains the
authoritative fallback.

## When PesaTrack asks for the PIN

- Every cold start (app is opened after being fully closed).
- After a configurable timeout when returning to the foreground —
  **Settings → Security → Auto-lock after**. Options: immediately, 1 minute, 5
  minutes, 15 minutes, never.

## Forgetting the PIN

There is **no recovery flow**. If you forget the PIN and biometrics are not
enrolled, you have to uninstall the app and lose the local database (or
restore from a backup you made earlier).

This is intentional: a recovery flow that we controlled would defeat the
"nothing leaves the device" guarantee. See the
[Privacy policy](/privacy) for the reasoning.

## Disabling

**Settings → Security → PIN lock → Disable**. Enter the current PIN once to
confirm. Biometrics are disabled automatically when the PIN is disabled.
