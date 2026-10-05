---
title: "Backup and restore"
dek: "Export a full copy of your PesaTrack database and restore it on the same or a new device."
section: "privacy-security"
order: 60
updatedAt: 2026-10-06
---

## Why back up

PesaTrack stores everything on-device. That is a privacy feature — but it
means you own the durability. If you factory-reset your phone or move to a new
one, you need a backup to keep your history.

## Creating a backup

1. Open **Settings → Data Management → Backup Data**.
2. Choose where to save: your phone's local storage or a folder you have
   sync'd (Google Drive, OneDrive, etc.). PesaTrack does not upload — you
   control the destination.
3. Confirm.

The backup is a single file containing the full Room database (all expenses,
categories, budgets, income and learned recipient mappings) and selected settings
(budget month start day and bank tracking choices). It is not a copy of all
DataStore preferences and does not include your PIN hash.

**Pending release:** the upcoming selective-reminder update additionally includes
the master reminder switch and versioned individual choices. These identifiers
can reveal recipient/account metadata; treat the backup as sensitive.

## Where the file goes

You choose the destination using Android's file picker. The app does not maintain
a public backup folder or upload the file itself.

Suggested filename format: `PesaTrack_Backup_yyyyMMdd_HHmmss.db`.

The file is **not encrypted at rest**. Treat it like any other financial
document. If you sync it to Drive or share it, that copy is only as private as
the destination you chose.

## Restoring a backup

1. On the target device, install PesaTrack from the Play Store.
2. Open the app once so the database is initialised.
3. **Settings → Data Management → Restore Data**.
4. Pick the `.db` backup file.
5. Confirm the overwrite. Restoring replaces the current database entirely.

If the backup file was created on a newer version of PesaTrack than what is
installed, update the app first — old versions may not understand new schema.

## What the backup does not include

- SMS content. PesaTrack never stores raw SMS; only the parsed transaction rows.
- Firebase analytics history. The anonymous analytics identifier resets on
  install and is scoped to that install.
- Your PIN hash or biometric secret.
- Reminder cooldown timestamps. In the upcoming selective-reminder update,
  restore deliberately waits one cooldown cycle before reminders resume (28 days
  for a monthly pattern). This avoids a burst but can delay useful reminders.

## Reminder choices after restore — pending release

New backups restore the master switch and individual choices together. Older
backups without those fields reset individual choices to automatic category
defaults and preserve the current master switch. Invalid or future-schema reminder
metadata resets choices and pauses reminders; the app discloses this fallback.
Category reset alone does not remove explicit reminder choices.

## Automating

There is no auto-backup schedule yet. A weekly reminder to back up is on the
[roadmap](/roadmap). In the meantime, a good habit is: back up after any
month-end review.
