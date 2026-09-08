---
title: "Backup and restore"
dek: "Export a full copy of your PesaTrack database and restore it on the same or a new device."
section: "privacy-security"
order: 60
updatedAt: 2026-09-15
---

## Why back up

PesaTrack stores everything on-device. That is a privacy feature — but it
means you own the durability. If you factory-reset your phone or move to a new
one, you need a backup to keep your history.

## Creating a backup

1. Open **Settings → Backup & Restore → Create backup**.
2. Choose where to save: your phone's local storage or a folder you have
   sync'd (Google Drive, OneDrive, etc.). PesaTrack does not upload — you
   control the destination.
3. Confirm.

The backup is a single file containing the full Room database (all expenses,
categories, budgets, learned recipient mappings) and DataStore preferences
(budget cycle, PIN settings hashed).

## Where the file goes

Backups are written under `Android/data/com.pesatrack/files/backups/` on the
phone unless you pick a different destination via the Android share sheet.

Filename format: `pesatrack-backup-YYYY-MM-DD-HHMM.pesatrack`.

The file is **not encrypted at rest**. Treat it like any other financial
document. If you sync it to Drive or share it, that copy is only as private as
the destination you chose.

## Restoring a backup

1. On the target device, install PesaTrack from the Play Store.
2. Open the app once so the database is initialised.
3. **Settings → Backup & Restore → Restore from file**.
4. Pick the `.pesatrack` file.
5. Confirm the overwrite. Restoring replaces the current database entirely.

If the backup file was created on a newer version of PesaTrack than what is
installed, update the app first — old versions may not understand new schema.

## What the backup does not include

- SMS content. PesaTrack never stores raw SMS; only the parsed transaction rows.
- Firebase analytics history. The anonymous analytics identifier resets on
  install and is scoped to that install.
- Your PIN or biometric secret. The salted PIN hash is included, but biometrics
  are re-enrolled per device by Android.

## Automating

There is no auto-backup schedule yet. A weekly reminder to back up is on the
[roadmap](/roadmap). In the meantime, a good habit is: back up after any
month-end review.
