---
question: How does the app auto-categorise my expenses?
group: budgets-categories
order: 10
anchor: how-auto-categorise
---

Three passes, in order: (1) explicit keyword rules (`KeywordRulesEngine`) that match
common merchants — Uber, KPLC, Naivas, OpenAI, and so on; (2) learned recipient mappings
— if you've categorised a recipient before, PesaTrack remembers; (3) payment-type
heuristics as a fallback. Unmatched expenses land in `UNCATEGORIZED` and get a low-priority
notification so you can tag them when it's convenient.
