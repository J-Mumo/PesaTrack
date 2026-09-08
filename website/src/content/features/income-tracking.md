---
title: Income tracking
dek: Salary, business receipts, refunds, and transfers-in — detected from SMS, categorised, and surfaced alongside your spend.
order: 40
icon: income
principle: save-and-invest
doesDo:
  - Detect income from M-PESA SMS (salary, business, funds received, peer receive, M-Shwari→M-PESA, agent deposit, Offnet B2C)
  - Detect bank credits from NCBA SMS
  - Categorise income by source (Salary, Business, Refund, Interest, Family, Transfer in, Other)
  - Learn sender→source mappings so future income from the same sender auto-categorises
  - Show a savings-rate card (received vs spent) on Analytics
  - Show an Income vs Spend 12-month overlay chart
doesNotDo:
  - Count bank→M-PESA self-transfers as new income (auto-excluded, visible for audit)
  - Treat all UNCATEGORIZED credits as income before you review them
  - Push high-priority notifications for income — the "new income" prompt uses a low-importance channel
---

## Why income matters

Every principle-4 saving/investment framing needs an income number to be
honest. Without income, "you could have saved X" is a stat about your
spending — useful, but not a savings rate. With income, the Savings Rate
insight card can tell you what fraction of what came in stayed in.
