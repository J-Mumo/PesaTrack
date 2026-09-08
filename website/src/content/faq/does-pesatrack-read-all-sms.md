---
question: Does PesaTrack read all my SMS?
group: sms-permissions
order: 20
anchor: does-pesatrack-read-all-sms
---

No. PesaTrack only processes SMS whose sender matches a supported financial sender —
currently `MPESA` and `NCBA`. Personal SMS from your contacts, promotional messages,
OTPs from unrelated services, and any other traffic are ignored at the receiver level
and never reach the parser or the database.
