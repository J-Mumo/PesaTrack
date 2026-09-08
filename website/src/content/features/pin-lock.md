---
title: PIN lock & biometric unlock
dek: Optional PIN + fingerprint or face unlock. Stops casual access on a shared or lost device.
order: 50
icon: lock
principle: privacy
doesDo:
  - Let you set a 4- or 6-digit PIN in Settings
  - Store the PIN as a salted SHA-256 hash — never the raw PIN
  - Allow biometric unlock (fingerprint / face) if your device supports it
  - Lock the app after configurable inactivity
  - Persist through reboots
doesNotDo:
  - Encrypt the entire database (see [/security](/security) for why)
  - Provide remote wipe or a "find my PesaTrack" service
  - Store a recovery code — if you forget the PIN, you must clear app data (which deletes local data)
---

## Threat model in one sentence

PesaTrack's PIN lock is designed to stop **casual access** — a family
member picking up your unlocked phone, a lost device — not a determined
attacker with forensic tools. If you need that level of protection, keep
your device encrypted (Android does this by default on modern phones)
and set a strong device passcode.
