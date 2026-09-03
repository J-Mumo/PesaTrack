# PesaTrack v1.4.1 Ad Campaign — Post-Mortem & Forward Plan

*Written 2026-09-03. Analysis window: 5 Jun → 30 Aug 2026 · Campaign live: 29 Jul → 5 Aug 2026 (8 days).*

Anchor documents:
- Campaign spec: [plans/ad-campaign-v141.md](ad-campaign-v141.md)
- Target customer: [plans/target-customer.md](target-customer.md)
- Product principles: [plans/product-principles.md](product-principles.md)
- Pro monetization roadmap: [plans/pro-launch-plan.md](pro-launch-plan.md)

Data sources:
- Google Ads Overview export `Overview_cards_csv(2026-09-03_19_34_22).zip` (Time_series, Day_&_hour, Devices, Locations)
- Play Console statistics export `9-3-2026 Statistics/` (25 CSVs covering acquisition, first opens, retention, DAU/MAU, growth rate, store listing funnel, impressions)

---

## TL;DR

1. **Volume was cheap and worked.** Google ACi delivered ~1,405 clicks and ~303 store-listing installs for **~$35 (~KES 4.5k)** — effective CPI ~**KES 15**, CTR **11.4%**. That is 5-10× cheaper than the plan's KES 60-120 CPI target.
2. **Quality was poor.** ~40% of paid installs ever opened the app (vs ~58% organic), and **D7 retention on the campaign cohort is ~4-5%** — the primary success metric defined in the campaign plan came in badly.
3. **The app is bigger but not stickier.** Install base went from ~105 → peak 294 → **228 by 29 Aug** (still 2.2× the pre-campaign base). DAU baseline shifted from ~13 → **~18-19 sustained** (+40%). But post-campaign the base is bleeding down slowly.
4. **Organic discovery did not lift.** Google Play explore MAU stayed flat at ~62 throughout. The ASO change and paid burst produced no measurable search-rank halo (yet).
5. **Only ~KES 4.5k of a KES 10k budget shows up in this dataset.** Either WhatsApp Status wasn't reported here, wasn't spent, or wasn't tracked to the Play Console — that gap must be answered before the next round.
6. **Forward move:** stop optimizing for install volume, switch to install *quality* (tCPA on first-open or D1), fix whatever is breaking between install and first open, and put the next KES 10k on LinkedIn + Search + WA Status properly — not another blind Google ACi round.

---

## 1. What actually ran

| Attribute | Value |
|---|---|
| Campaign window | Wed 29 Jul → Wed 5 Aug 2026 (8 days) |
| Channel captured in data | Google App Campaign (ACi) only |
| Locations | Kenya only. Nairobi 56%, Kiambu 7%, Mombasa 5%, Nakuru 4%, Kajiado 3%, Meru 2.5%, Uasin Gishu 2.3% |
| Devices | Mobile 99.7% ($34.89), Tablet 0.3% ($0.17), Desktop/TV: 0 |
| Spend visible | **$35.07 ≈ KES 4,558** (at ~130 KES/$) |
| Planned spend | KES 10,000 (KES 4k Google ACi + KES 6k WA Status) |

**Data gap #1 — WhatsApp Status is missing.** The Overview_cards ZIP only contains Google Ads data. Either the WA campaign wasn't launched, was launched but under-delivered, or was launched but reports in a Meta dashboard that isn't in this export. Before diagnosing "the campaign," confirm what WA spent, delivered, and drove.

---

## 2. Direct campaign metrics (Google ACi)

| Metric | Value | Read |
|---|---:|---|
| Impressions | 12,366 | Small reach — ~12k ad views across 8 days |
| Clicks | 1,405 | Volume is decent given spend |
| CTR | **11.4%** | Very high. Almost certainly means most impressions were in Play Store *Similar apps* carousels or search-result cards (native placements) rather than banner display |
| Avg CPC | **$0.025 (~KES 3.24)** | Extraordinarily cheap. Suggests low-competition placements |
| Effective CPM | $2.83 (~KES 368) | Bargain — but see quality caveat below |

### Daily click / spend rhythm

Ads ran only mornings on most days (8am-2pm, peaks 9-10am), consistent with **daily-budget exhaustion by mid-day** — spending was too low to be always-on. Tuesday Aug 4 is the only day ads stayed alive all day (also the day retention shows an anomalous D7 spike of 5 users — probably genuine "afternoon browser" users who convert better).

**Actionable finding:** the daily cap was too low or delivery too front-loaded. Next campaign should either raise the daily cap or day-part to hit evening (post-work) hours when NGO Mary is on her phone.

### Geo distribution vs target persona

target-customer.md defines NGO Mary as Nairobi (Westlands/Kilimani/Lavington/Kileleshwa/Karen), then Mombasa CBD, then field offices (Kisumu/Garissa/Lodwar). Actual delivery:

| Region | Impressions | % | Persona fit |
|---|---:|---:|---|
| Nairobi County | 6,918 | 56% | ✅ Primary target |
| Kiambu County | 869 | 7% | ✅ Bedroom-community for Nairobi |
| Mombasa County | 601 | 5% | ✅ Secondary target |
| Nakuru County | 463 | 4% | ⚠️ Not in persona — mid-tier city |
| Kajiado County | 310 | 3% | ✅ Bedroom-community |
| Kwale/Kilifi | 419 | 3% | ⚠️ Coast tourism, low-income |
| Rest of Kenya | ~2,786 | 22% | ⚠️ Broad, likely low-intent |

Geo is *directionally* right (top 3 counties by pop = top 3 by impressions), but **~30% of impressions went to counties not in the persona**. Targeting was set to "Kenya" wholesale (not specific counties), so Google's ML broadened based on lookalike signals. Tightening geo to the 6-7 target counties would concentrate spend where LTV is highest.

---

## 3. Play Store funnel — what the campaign moved

Baseline: **Jul 21-28** (8 days pre-launch). Campaign: **Jul 29 - Aug 5**.

| Funnel step | Baseline daily avg | Campaign daily avg | Lift | Campaign total |
|---|---:|---:|---:|---:|
| Store listing visitors | 8.5 | 155 | **18×** | 1,239 |
| Store listing acquisitions (installs) | 2.0 | 37.9 | **19×** | 303 |
| Total user acquisitions | 2.4 | 41.4 | **17×** | 331 |
| User first opens (activated) | 1.4 | 16.5 | **12×** | 132 |
| Store listing conversion rate | ~22% | ~25% (weighted) | +3pp | — |

Two important observations:

**(a) Conversion rate held up.** Listing conversion during the campaign averaged 24-25% — actually slightly better than the noisy baseline. This means the ASO changes (title `PesaTrack: M-PESA Expenses`, short description, screenshots with keyword overlays) did their job. **The listing is not what's breaking. Do not touch it.**

**(b) Huge install → first-open gap.**

- Acquisitions during campaign: **331**
- First opens during campaign: **132**
- Conversion install→open: **40%**
- Baseline install→open: 11/19 = **58%**

**~200 people installed and never opened the app** in the campaign window. Some show up in later "first opens" (delayed opens), but the pattern is clear: paid installs are ~30% less likely to open than organic. This is the single biggest quality signal in the data and is the root cause of the retention weakness in §4.

---

## 4. The retention story (the critical part)

The campaign plan defined the primary success metric as **"D7-retained installs by UTM source."**

### D7 retention on campaign cohorts

For each install-day cohort during the campaign, D7 retained (7-day user retention metric) on the corresponding date:

| Cohort (first-open date) | Cohort size | D7 retained | D7 rate |
|---|---:|---:|---:|
| 29 Jul | 17 | 0 | 0% |
| 30 Jul | 11 | 0 | 0% |
| 31 Jul | 17 | 1 | 6% |
| 1 Aug | 21 | 2 | 10% |
| 2 Aug | 25 | 0 | 0% |
| 3 Aug | 13 | 1 | 8% |
| 4 Aug | 17 | 1 | 6% |
| 5 Aug | 11 | 1 | 9% |
| **Total** | **132** | **6** | **~4.5%** |

Industry benchmark for finance/utility apps is 10-25% D7. **~4.5% is a red flag.** For comparison, "Returning users %" metric (share of DAU who are returners) was 80-95% during quiet periods and **dropped to 35-57% during the campaign week** — the campaign flooded the DAU pool with one-visit users.

### What this tells us about who Google delivered

Combined with the huge install-to-open gap, the pattern is textbook **low-intent in-app promotion**:

- Very high CTR (11.4%) — clicks cheap and abundant
- Very low CPC (~KES 3) — no auction pressure
- 60% install-no-open — user clicked because a game rewarded them or the ad was misleadingly overlapped
- 4.5% D7 — of those who did open, most bounced immediately

Almost certainly **the dominant placement was in-app AdMob** on games/utility apps where users tap install to close the ad. Google ACi with a small budget can't compete for Play Store Search or YouTube inventory, so it fills the daily cap on cheap in-app inventory.

The "starve Gmail promotions / YouTube in-stream" tactic in §6.2b of the plan worked (no landscape asset uploaded), but the *other* cheap inventory Google leans on (in-app AdMob) doesn't need a landscape asset — that placement was open and got flooded.

---

## 5. Where the app stands right now (as of 30 Aug 2026)

### Install base trajectory

| Milestone | Date | Installed audience | Vs pre-campaign |
|---|---|---:|---:|
| Pre-campaign | 28 Jul | 105 | — |
| Campaign peak | 5 Aug | 294 | **+180%** |
| End of campaign +1w | 12 Aug | 260 | +148% |
| +3w | 25 Aug | 227 | +116% |
| Latest (29 Aug) | 29 Aug | 228 | **+117%** |

**Read:** ~120 net-new installed users retained. Real. But the base peaked at 294 and has been losing ~2.5 users/day net since — erosion has slowed but not stopped.

### Engagement — sustained lift is real, but smaller than headline

| DAU (Kenya) | Baseline (Jun-Jul avg) | Peak (2 Aug) | Recent (23-29 Aug) |
|---|---:|---:|---:|
| Users | ~13 | 51 | ~18-20 |

DAU settled ~40% above pre-campaign — a real, sustained lift of ~5-7 daily active users. Modest but not nothing.

However **DAU/MAU collapsed** from a healthy ~14% pre-campaign to ~7-8% now, because MAU is inflated by the paid cohort that never comes back. Will normalize as those users age out of the 30-day MAU window (end of Sep).

### MAU by attribution channel — the flat-organic problem

| Source | Pre-campaign (~28 Jul) | Peak (~20 Aug) | Latest (26 Aug) |
|---|---:|---:|---:|
| Google Play explore (organic) | 63 | 66 | 61 |
| Paid and direct | 23 | 157 | 150 |
| Not attributed | 7 | 26 | 30 |

**Google Play explore MAU is essentially flat.** The paid burst did NOT produce a measurable organic search-ranking halo. The ASO title change is a good long-term bet, but the paid velocity was too short (8 days) and possibly too low-quality to move Play Store's ranking model on target keywords.

"Not attributed" grew from 7 → 30 — likely word-of-mouth / direct-link installs (Play Store link shared in WhatsApp), which is a positive secondary signal.

### Current install rate (post-campaign steady state)

Aug 20-30 daily new users: 2, 2, 1, 2, 4, 2, 2, 6, 1, 1, 1. **Median ~2, mean ~2.2/day.** Basically identical to pre-campaign (~2.4/day). The campaign produced no lasting acquisition tailwind — daily install rate returned to trend within a week of ads stopping.

---

## 6. Scoring the campaign against its own success criteria

Per plans/ad-campaign-v141.md:

| Success criterion | Target | Actual | Verdict |
|---|---|---|---|
| Primary: D7-retained installs by UTM source | Learn which channel wins | ~6 D7 from ~132 first-opens (~4.5%); WA source absent | ❌ Below any reasonable threshold |
| Secondary: Blended CPI | KES 60-120 | ~KES 15 (Google-only, install-not-open) | ✅ Volumetrically, ❌ quality-adjusted |
| Secondary: CTR per channel | Directional | Google 11.4%; WA unknown | ⚠️ Read directionally, not statistically |
| Budget spend | KES 10,000 | ~KES 4,558 visible | ⚠️ Under-spent or under-reported |
| Installs range | 66-141 | 303 (installs) / 132 (first-opens) | ✅ On installs / ❓ on activated |

**Net:** the campaign learned *some* useful things (Google ACi at KES 4k gets you real volume; ASO conversion holds; Nairobi is where impressions land) but failed the primary goal of learning which channel deserves the next KES 30k because the D7 metric is close to zero and WA is missing from the data.

---

## 7. What worked / what didn't (crystallized)

### Worked ✅

1. **ASO listing edits** — conversion rate held ~25% under 18× visitor load. Keep them.
2. **Nairobi + urban Kenya geo skew** — Google's ML found the right cities even without county-level targeting.
3. **Cheap unit economics on install volume** — KES 15 CPI is real, if you only care about installs.
4. **DAU baseline shift** — went from ~13 to ~18-19 sustained (+40%). Some paid users are sticking.
5. **"Not attributed" MAU grew** (7 → 30) — hints at organic WOM referral, the highest-LTV source.

### Didn't work ❌

1. **D7 retention on paid installs is ~4.5%** — probably 5-8× below organic. Primary metric failed.
2. **Install-to-open gap of 40%** — 200 paid installs never opened the app. Almost certainly indicates in-app AdMob incentivized placements dominating delivery.
3. **Organic ranking halo did not materialize** — Google Play explore MAU flat throughout.
4. **Budget under-spent (~55% of plan)** or **WA Status not reported** — the multi-channel comparison the plan was designed to produce is not achievable from this data.
5. **Post-campaign install rate = baseline install rate** — no acquisition flywheel effect.
6. **No re-engagement mechanism for the 200 install-no-open users** — wasted asset sitting in the install base.

### Ambiguous ⚠️

1. **Uninstall rate post-campaign** is ~1.9%/day of base — not dramatically higher than pre-campaign (~2%/day), but base was inflated so absolute uninstalls hurt.
2. **Tanzania signal** is real but tiny (5-8 MAU steady). Not enough to build strategy around, but worth passive protection.

---

## 8. Diagnosing the retention leak — where to look in the code

Given only ~4.5% D7, the funnel between install and D7 is broken. In priority order:

1. **SMS permission grant rate** — check onboarding flow in [android/app/src/main/java/com/pesatrack/](../android/app/src/main/java/com/pesatrack/). If a paid install lands on a permission wall and 80% deny, they never see value.
2. **First-value event fire rate** — target-customer.md mentions Stage 1 first-value events already exist in [HomeViewModel.kt](../android/app/src/main/java/com/pesatrack/presentation/screens/home/HomeViewModel.kt). Pipe these to Play Console attribution or Firebase so you can see D1 by source.
3. **Empty-state handling for users with no M-PESA history** — if a user is on a fresh SIM or a phone with cleared SMS, Home is empty. Add a demo mode / seed example transactions.
4. **Onboarding completion rate** — if paid users abandon onboarding, that's the leak.
5. **First open crash rate** — Play Console → Vitals for the 29 Jul - 5 Aug cohort. A single ANR on Home would erase D1 across ad installs.

**Close the leak on channel #1 before spending on channel #2.**

---

## 9. Forward plan — what to do next

### Phase A — Fix the leak (0-4 weeks)

**Do not run another campaign yet.** Reasons: no measurable D7 win to double down on; low-quality inventory drained the last spend.

1. **Instrument install-source D1/D3/D7 opens.** Wire existing first-value events to a signal Play Console (or Firebase Analytics) exposes by acquisition source. Cost: 1-2 days engineering.
2. **Add a "welcome to a fresh install" onboarding path.** If SMS inbox has zero M-PESA messages, show a demo mode / seed a couple of example transactions with an "Import your own" CTA. Directly addresses the ad-install-with-no-history user.
3. **Audit SMS permission grant flow.** Add a "why we need this" screen with the privacy pitch from target-customer.md ("No signup. No cloud. Nowhere else.") — this is the value NGO Mary buys.
4. **Ship v1.4.2 with a compelling changelog within 14 days of the campaign** — users who see updates in their Play Store notifications convert to opens. Best chance of reactivating the 200 install-no-open users.

### Phase B — Better next campaign (weeks 4-8)

When the leak is fixed and instrumentation is in place, change *how* you spend:

| Choice | Prior campaign | Next campaign |
|---|---|---|
| Optimization goal | Installs | **In-app event (first-open OR SMS permission grant)** |
| Google ACi bid strategy | tCPI | **tCPA on first-open** |
| Placement exclusions | Gmail, YouTube in-stream | **Plus in-app AdMob (games)** — the single biggest quality lever |
| Daily budget | Too low, morning-only delivery | **Higher daily cap or day-parted to 5pm-9pm** |
| Geo targeting | "Kenya" wholesale | **Nairobi + Kiambu + Kajiado + Mombasa + Kisumu + Nakuru** (persona-fit counties only) |
| Language | English default | **Add Swahili** creative variants — Nairobi INGO staff read English but Kenyan Swahili tests well |

### Phase C — Channel diversification (weeks 4-12)

Google ACi is one lever. The persona and product argue for adding two more:

1. **LinkedIn ads targeting NGO/INGO job titles** — per target-customer.md this is where NGO Mary lives (UNICEF, Oxfam, Save the Children, IRC, WV, FHI 360, GIZ, KCDF, Aga Khan Foundation). Small test budget (KES 3-5k) to measure CPI on high-LTV cohort. Expect CPI ~KES 200-400 but D7 5-10× the ACi cohort → probably better LTV/CAC.
2. **WhatsApp Status via Meta** — the missing half of the last plan. Re-run properly with attribution set up, KES 3-5k budget, single ad, Nairobi + Mombasa geo.
3. **Google Search on high-intent keywords** — deferred in the last plan. Cheap to test now with KES 2k on `mpesa expense tracker`, `budget app kenya`, `mpesa statement analyzer`. Search-intent traffic almost always beats display-driven ACi on D7.

### Phase D — Structural moves (weeks 8-24)

1. **Referral mechanic.** NGO Mary has 5-15× advocacy density (target-customer.md). A referral loop — respecting the "no gamification that rewards more spending" principle — would let each retained user recruit 2-3 more. Referral for *awareness* actions, not spend actions.
2. **Pro tier launch** ([plans/pro-launch-plan.md](pro-launch-plan.md)). Once D7 by source is measurable, LTV is calculable, and a real CPA can be paid. Currently spending blind on CPI.
3. **iOS.** [plans/ios-implementation-plan.md](ios-implementation-plan.md). Many INGO staff carry work iPhones. Currently ~50% of the persona is unaddressable.

---

## 10. Data / process gaps to close before next campaign

1. **WhatsApp Status attribution.** Confirm whether it ran, what it spent, what it delivered. Without this you cannot claim "the campaign" was tested — only "the Google half."
2. **Cross-channel UTM discipline.** Every ad-set landing URL should carry a distinct `utm_source` / `utm_campaign` visible in Play Console → User acquisition → Third-party. The plan called this out; current data can't cleanly separate paid Google from Meta from "Direct" because the WA leg is missing.
3. **First-open + D7 instrumentation.** Discussed in Phase A — the blocker for everything else.
4. **Baseline sample size.** 8 days of pre-campaign baseline is fine for directional read but not statistical claims.

---

## Bottom line

The KES ~4.5k you spent bought a genuine but small permanent lift (~+120 users installed, ~+5-7 daily actives) plus one important lesson: **Google ACi on utility apps in Kenya, at this budget level, buys cheap in-app AdMob installs with ~5% D7 retention.** That is a known failure mode of the channel; you now have it in data, which is worth the KES 4.5k.

The move now is **not** to spend more on the same channel harder. It's to spend the next KES 5-8k on instrumentation and onboarding fixes (mostly engineering time, not media spend), then run a *smaller, better-instrumented, multi-channel* test with tCPA-on-first-open as the optimization goal — LinkedIn and Search as new channels, not another round of blind ACi.

---

## Appendix — Key numbers at a glance

| Metric | Value |
|---|---:|
| Campaign spend (Google ACi, visible) | $35.07 / ~KES 4,558 |
| Impressions | 12,366 |
| Clicks | 1,405 |
| CTR | 11.4% |
| CPC | ~$0.025 / ~KES 3.24 |
| Store listing visitors (campaign) | 1,239 |
| Store-listing acquisitions | 303 |
| Total user acquisitions | 331 |
| User first opens | 132 |
| Install → first-open rate | 40% |
| CPI (installs) | ~KES 15 |
| Cost per activated (first-open) user | ~KES 34.5 |
| D7 retention (campaign cohort, users) | ~4.5% |
| Install base 28 Jul → 5 Aug → 29 Aug | 105 → 294 → 228 |
| DAU baseline shift | ~13 → ~18-19 (+40%) |
| Google Play explore MAU change | ~63 → ~61 (flat) |
| Post-campaign install rate | ~2.2/day (baseline: ~2.4/day) |
