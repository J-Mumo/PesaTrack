---
question: How do I back up and restore my data?
group: troubleshooting
order: 30
anchor: backup-restore
---

**Settings → Data Management → Backup Data** saves a raw database file to the
destination you choose using Android's file picker. **Restore Data** reads that
file and replaces the current database. The backup is **not encrypted by
PesaTrack**; keep it private and choose its destination carefully.

**Pending release:** the upcoming selective-reminder update also backs up the
master reminder switch and individual choices. Older backups reset individual
choices to category defaults and retain the current master switch. Unreadable
reminder settings pause delivery with a message. Reminder cooldown timestamps
are not backed up; restore waits one cooldown cycle to avoid a catch-up burst.
See [backup and restore](/docs/backup-and-restore).
