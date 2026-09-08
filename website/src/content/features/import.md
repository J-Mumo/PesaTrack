---
title: Import PDF statements & Excel
dek: Backfill months of history from your M-PESA PDF statement or a spreadsheet — parsed on-device, no upload.
order: 60
icon: import
principle: local-first
doesDo:
  - Parse official M-PESA PDF statements (regex-based, offline)
  - Import Excel spreadsheets (Apache POI) with configurable column mapping
  - Detect and skip duplicate transactions using the M-PESA transaction ID
  - Auto-categorise imported rows through the same pipeline as live SMS
  - Report import results — new expenses, new income, duplicates skipped, errors
  - Import historical SMS from your inbox on request (user-initiated only)
doesNotDo:
  - Upload the statement or spreadsheet to any server
  - Alter the source file
  - Import bank statements yet (M-PESA PDF only — see /roadmap)
---

## The M-PESA PDF flow

Open the M-PESA app → Statement → email the encrypted PDF to yourself,
open it on your phone, share to PesaTrack. The parser reads the file
locally, matches each row against the regex spec, and inserts new
transactions. Nothing about the file leaves the device.
