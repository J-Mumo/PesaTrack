---
question: New SMS aren't being detected — what's wrong?
group: troubleshooting
order: 20
anchor: sms-not-detected
---

Three things to check. (1) In Android Settings, confirm PesaTrack still has SMS permission —
some OS updates revoke it. (2) Some manufacturers (Xiaomi, Oppo, Vivo) aggressively kill
background receivers; add PesaTrack to your battery whitelist / auto-start allow-list.
(3) Confirm the sender is exactly `MPESA` or `NCBA` — a bulk-SMS reseller that uses a
different sender ID won't match.
