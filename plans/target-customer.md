# PesaTrack — Target Customer (business decision anchor)

Decided 2026-06-23. Use this when prioritizing features, copy, channels, or pricing.

> Also stored in agent repo memory at `/memories/repo/target-customer.md`. Keep the two in sync when this document is updated.

## Primary persona — "Salaried NGO/INGO Mary"

- **Age:** 26–38
- **Geo:** Nairobi (Westlands, Kilimani, Lavington, Kileleshwa, Karen) > Mombasa CBD > field offices (Kisumu, Garissa, Lodwar)
- **Job:** Full-time at INGO/NGO. Roles: Programme/M&E/Comms/Project/Operations/Finance Officer; Coordinator; Country Director track.
- **Employers (targeting list):** UNICEF, Save the Children, Oxfam, IRC, World Vision, FHI 360, MSF, Plan International, Mercy Corps, Action Against Hunger, Concern Worldwide, GIZ, USAID partners, plus local NGOs (KCDF, Akili Dada, Aga Khan Foundation, etc.)
- **Net pay:** KES 80k–250k (INGO local hires can be KES 300k+). Salary into bank → M-PESA in chunks.
- **Pay date:** 25th–end of month (justifies our `monthStartDay` wedge).
- **Phone:** Mid-to-upper Android (Samsung A/S, Pixel, Tecno Camon, Xiaomi). Android 11+.
- **Already pays for:** Notion, Spotify/Apple Music, ChatGPT Plus, Headspace, a Cytonn/Sanlam/Britam MMF. KES 200–500/month for a finance tool is a non-event.

### Distinctive traits (vs other Marys)

1. **Privacy is a job skill, not a preference.** GDPR/data-protection training is muscle memory. The "no internet permission" line *celebrated*, not just tolerated.
2. **Per-diem / field-expense reconciliation is a monthly ritual.** Half of weekly M-PESA charges may be claimable from work. The Exclude flag + custom categories serve this exactly. **Product-marketing gap: we don't acknowledge this use case in the listing or onboarding.**
3. **Irregular income on top of salary** (per diems, consultancy, honoraria). Our v1.4 income tracking + savings-rate features have a real edge here.
4. **High advocacy density** — Devex/INGO WhatsApp + alumni groups. 1 enthusiastic user ≈ 5–15 high-quality referrals.
5. **Cheaply addressable** on LinkedIn (job-title targeting works), geo-targeted Meta (postcode-dense), and KE finance creators (Abojani, Centonomy, Wakanai).

### Jobs to be done (priority order)

1. "Show me where my M-PESA went last month without me typing it in."
2. "Match my budget cycle to my salary cycle." ← unique to us
3. "Tell me how much went to fees so I can feel something." (cat 606)
4. "Tell me if I'm actually saving or just feeling like I am."
5. "Don't take my data anywhere."

## Secondary — "Tech Mary"

24–34 software/PM/designer. Higher pay, lower paranoia, lower referral density. **Best treated as organic-only target** (X threads, README, HN post). Don't pay to acquire her.

## Tertiary — "Privacy Power-User"

HN/Aurora Store/GrapheneOS crowd. Most of first 50 installs. Finite pool — don't size paid spend around her, but keep her happy with the no-internet stance and open posture.

## Anti-personas (do NOT spend on)

- iPhone users (no app — see [ios-implementation-plan.md](ios-implementation-plan.md))
- Cash earners / informal sector (no SMS to parse)
- Joint/family/business-invoicing seekers (product doesn't serve)
- Under-22 students (low M-PESA volume, low retention)
- High-net-worth KES 500k+ (use Stash/Spendee or private bankers)
- **Teacher Mary** for now — real audience but conversion economics weak until we ship referral mechanics + monetization

## Real competition (status quo)

1. Doing nothing / M-PESA mini-statement (~80% of market)
2. Self-made Excel that lasted 6 weeks (~10%)
3. Money Manager / Wallet by BudgetBakers / Spendee (generic, no M-PESA parsing)
4. Other Kenyan attempts (mostly abandoned or ad-laden)

## Primary success metric

**D7-retained installs**, not raw installs. We already instrument first-value events (Stage 1 in [HomeViewModel.kt](../android/app/src/main/java/com/pesatrack/presentation/screens/home/HomeViewModel.kt)). Pair install UTM with D7 cohort in Play Console.

## Implications for product decisions

- **Yes-build** features that serve NGO Mary's per-diem reconciliation, field-trip categorization, and consultancy-income tracking.
- **Yes-build** features that strengthen the privacy moat (e.g. visible "permissions audit" screen in About).
- **Question** features whose only beneficiary is a different persona (e.g. multi-user, family budgeting, business invoicing — Secondary/Tertiary, not Mary).
- **Pricing:** KES 200–500/month is the sweet spot when we monetize ([pro-launch-plan.md](pro-launch-plan.md)).
- **Copy tone:** professional, factual, slightly understated. NGO Mary distrusts marketing-speak; she trusts numbers and verifiable claims.
