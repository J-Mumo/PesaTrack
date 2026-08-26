# PesaTrack v1.4.1 Ad Campaign — Creative + Targeting Spec

**Decided 2026-07-09.** First paid campaign. Ready-to-execute.

Anchor documents:
- Target customer: [plans/target-customer.md](target-customer.md)
- Product principles: [plans/product-principles.md](product-principles.md)

---

## 0. Pre-flight — ASO listing edits (do BEFORE the paid campaign)

Launch the ad run 3-4 days AFTER these listing edits go live. Play Store re-indexes for search ranking on that timescale, and paid clicks landing on an un-reindexed listing waste the paid+organic synergy.

### Title (30 char max)
- **From:** `PesaTrack`
- **To:** `PesaTrack: M-PESA Expenses` *(26 chars)*
- Rationale: Title is Play Store's heaviest ASO field. `M-PESA` + `expenses` match the highest-intent Kenyan queries.

### Short description (80 char max)
- **To:** `M-PESA expense tracker & budget app. Auto-reads SMS. On-device, no signup.` *(75 chars)*
- Hits `M-PESA expense tracker`, `budget app`, `SMS`, privacy hook.

### Full description — first 250 chars (visible before "Read more")
> PesaTrack is Kenya's private M-PESA expense tracker and budget app. It reads your M-PESA and bank SMS on your phone, auto-categorises spending, and lets you budget on your pay cycle — not the calendar month. No signup, no cloud, no ads.

### Full description body — section header rewrites
| Current | New |
|---|---|
| `💰 KEY FEATURES` | `💰 M-PESA EXPENSE TRACKER — KEY FEATURES` |
| `📊 ANALYTICS` | `📊 BUDGET & ANALYTICS` |
| `🏦 SUPPORTED SMS SOURCES` | `🏦 SUPPORTED SMS SOURCES (M-PESA + BANKS)` |
| `🇰🇪 BUILT FOR KENYA` | `🇰🇪 BUDGET APP BUILT FOR KENYA` |

Also insert one new bullet under `KEY FEATURES`:
> **Salary-Cycle Budgeting** — Set your "month starts on" day (e.g. 25th) so budgets track your pay cycle, not the calendar month. Ideal for salaried users and per-diem reconciliation.

### Screenshots — first 3 must carry keyword overlays
| Position | Screen | Overlay caption |
|---|---|---|
| 1 | Home with auto-tracked expenses | **"M-PESA expenses, tracked automatically"** |
| 2 | Budget screen (period `Apr 25 – May 24`) | **"Budget on your pay date, not the calendar"** |
| 3 | By-Category donut, Fees (606) highlighted | **"See where money really goes"** |
| 4 | Analytics Monthly | *(no overlay change)* |
| 5 | PIN lock / privacy | **"Your data. On your phone. Nowhere else."** |

### Where to edit
Play Console → PesaTrack → **Grow → Store presence → Main store listing**. Save → Send for review. Wait 3-4 days after review passes before launching paid ads.

---

## 1. Decisions locked

| Decision | Choice |
|---|---|
| Total budget | **KES 10,000** (MVP test) |
| Duration | **7 days** (full weekday/weekend + D1 tail) |
| Channels | Google App Campaign (ACi), WhatsApp Status |
| Format | One 15-second 9:16 vertical video (Script 1: The Tour) + one 1:1 hero still |
| Google Search | **Dropped for this run** (revisit for KES 30k round) |
| Primary metric | **D7-retained installs by UTM source** (from Play Console) |
| Secondary metric | Blended CPI, CTR per channel |
| Persona | NGO Mary (see target-customer.md) |
| Copy tone | Neutral, factual, no shame framing, no fear |

**Read the results directionally, not statistically.** At KES 10k you'll get ~85-170 installs total across channels — enough to see rank order, not enough for tight confidence intervals. The purpose of this run is to learn *which channel deserves the KES 30k next time*, not to declare a winner.

---

## 2. Budget allocation

| Channel | Budget | Daily cap | Est. CPI | Est. installs |
|---|---:|---:|---:|---:|
| Google App Campaign (ACi) | KES 4,000 | 570/day | 60-120 | 33-66 |
| WhatsApp Status (via Meta, 1 video ad) | KES 6,000 | 850/day | 80-180 | 33-75 |
| **Total** | **KES 10,000** | | | **66-141** |

Budget rationale: Google ACi needs ~30 conversions to exit learning phase; KES 4k covers that at target CPI KES 100. The KES 6k on WA Status all goes to the single Script 1 (Tour) video — without a second video we forfeit the per-script A/B, but the extra budget on one ad helps it exit Meta's per-ad learning phase confidently.

---

## 2b. Asset × placement matrix

The two channels use the assets differently.

### Google ACi — both videos + both hero stills in one asset group; Google's ML decides

You upload the 2 videos + 2 stills + headlines + descriptions into a single asset group. Google auto-assembles ads and picks which asset to serve in which placement. You cannot pin "Script A to Play Store only." You *see* per-asset performance in the Asset Report after 3-4 days and switch off losers.

| Google surface | What runs there | Why |
|---|---|---|
| Play Store — search results | Hero stills | Static asset serves as the search-result card |
| Play Store — "Similar apps" carousel | Either video or either still | ML picks per impression |
| In-app AdMob (games, other apps) | Either video (auto-trimmed if needed) | Where utility-app installs really come from |
| YouTube Shorts | Either video | Vertical short-form only |
| YouTube in-stream (skippable pre-roll) | — | Deliberately starved: no landscape asset uploaded (§6.2b bias tactic) |
| Google Discover feed | Video + still + headline | Feed-style card; ML picks |
| Gmail promotions tab | — | Deliberately starved: no HTML5 asset uploaded (§6.2b bias tactic) |

### WhatsApp Status — 1 video ad in one ad set (no A/B this round)

Only Script 1 (Tour) filmed. Full WA Status budget concentrates on that single ad. We lose the per-script comparison but gain enough spend on one ad to exit Meta's per-ad learning phase.

| WA Status ad | Asset | Hypothesis being tested |
|---|---|---|
| `wa_video_tour` | Script 1 video (Tour) | Does a feature walkthrough convert on WA Status at all? Value = visible completeness |

### Per-asset role summary

| Asset | Role in Google ACi | Role in WA Status |
|---|---|---|
| **Script 1 video (Tour)** | Primary asset for in-app + Shorts + Similar-apps carousel | Sole ad — tests whether WA Status converts at all |
| **Hero still 1** (Home screen frame) | Play Store search cards + Discover cards | Not used |

---

## 3. Creative assets — two 15-second vertical videos

Both videos: 9:16 vertical (1080×1920), 15 seconds, Kenyan-English voiceover, ambient music ‒20 dB under voice. Both open **directly on the PesaTrack Home screen** with populated demo data. Both end on the Play Store badge.

**Guiding principle for both:** show the product doing something valuable, not framing concepts. The value shown is the user *learning something about their own money without doing any work*.

**Filming setup:** Screen record on the demo device or emulator (see §3a). Record voiceover separately on your phone with any recorder app. Mix in CapCut (free).

### Script 1 — "The Tour" (value: everything sorted, no work)

Fastest to film — one continuous screen recording, no cuts required. Best asset for Google ACi's Play Store surfaces because it behaves like an animated extended screenshot.

| Sec | Visual | Voiceover |
|---|---|---|
| 0.0-2.0 | **Home screen open.** Recent transactions visible, month total at top: "This month: KES 43,120". Thumb rests on screen. | "This is PesaTrack. Every M-PESA transaction, already sorted." |
| 2.0-5.0 | Thumb taps one of the recent Transport expense cards. Categorize screen opens showing recipient, amount, date, transaction code, and the category picker. | "Tap any transaction. See the detail — or change the category." |
| 5.0-8.0 | Back to Home → tap **Budgets** tab. Two or three budget cards visible with progress bars — one green, one at 82% amber. | "Set a limit for a category. You get a warning at eighty percent." |
| 8.0-13.0 | Tap **Analytics** tab. By-Category donut animates in. Scroll down to Monthly Trend line chart showing six months. | "The whole month at a glance. Or six months. Or twelve." |
| 13.0-15.0 | Play Store badge with PesaTrack icon. | "PesaTrack. Free on Google Play." |

### Script 2 — "Where did it go?" (deferred)

Not filmed for this round. Preserved below for the KES 30k round — filming it then unlocks the per-script A/B on WA Status.

The insight-moment script. Requires accurate row highlighting — in CapCut, add a subtle yellow underline or box that animates onto each highlighted row for ~1s.

| Sec | Visual | Voiceover |
|---|---|---|
| 0.0-2.0 | **Home screen.** Camera holds on the month total: **KES 47,320**. | "Where did forty-seven thousand shillings go last month?" |
| 2.0-4.0 | Zoom into By Category card on Home. First row highlights: **Transport — KES 12,400**. | "Twelve thousand on transport." |
| 4.0-6.0 | Next row highlights: **Groceries — KES 8,150**. | "Eight thousand on groceries." |
| 6.0-8.0 | Next row highlights: **Fees — KES 1,240**. | "One thousand two hundred on M-PESA fees you never noticed." |
| 8.0-13.0 | Tap **Analytics** tab. Donut fills in with all categories, then pan to Top Spenders list showing named recipients with totals. | "Every transaction. Every recipient. Every category. Sorted automatically." |
| 13.0-15.0 | Play Store badge. | "PesaTrack. Free on Google Play." |

**Voiceover style for both:** Kenyan English, calm, medium pace. Not enthusiastic. Not salesy. Read it like you're describing it to a colleague.

**Captions:** Do NOT auto-caption the whole voiceover. Only burn in numbers or key words that need to be visually reinforced when the viewer's audio is off (a large share of WA Status viewers watch muted).

---

## 3a. Demo device prep

You need a device or emulator populated with realistic KE M-PESA transaction data before filming. Both scripts fail hard on obviously-fake data.

**Recommended setup:** Android Studio emulator (Pixel 7 profile, API 34) running a signed release APK of PesaTrack v1.4.1. Fresh install, no real data.

**Populate with SMS via `adb`:**

```powershell
# From PowerShell, with the emulator running (default console port 5554):
adb -s emulator-5554 emu sms send MPESA "SFA12X3D4 Confirmed. Ksh500.00 sent to JANE MUTHONI 254712345678 on 2/7/26 at 10:15 AM. New M-PESA balance is Ksh4,320.00. Transaction cost, Ksh6.00."
```

Alternative: Android Studio Extended Controls → **Phone → SMS message** → sender: `MPESA` → body: paste an M-PESA SMS. Slower for volume but easier for one-offs.

**Seed 40-60 SMS across 2-6 months** covering categories that need to appear on-screen in the videos:

| Category | Merchant / paybill examples |
|---|---|
| Transport | UBER, LITTLE CAB, boda paybills, matatu SACCOs |
| Groceries | NAIVAS, CARREFOUR, QUICKMART, CHANDARANA |
| Bills | KPLC (paybill 888880), NAIROBI WATER, DSTV (444900), ZUKU |
| Airtime | SAFARICOM PLC |
| Received | Salary from employer name ("OXFAM KENYA", "UNICEF KENYA") |
| Send Money | Personal names to generate cat 606 fees |

**Sample SMS bodies** (real M-PESA format, fabricated recipients — tune amounts to match Script 2's on-screen figures):

```
SFA12X3D4 Confirmed. Ksh4,500.00 paid to CARREFOUR TWO RIVERS. on 5/7/26 at 6:20 PM. New M-PESA balance is Ksh29,470.00.
SFB45Y6E7 Confirmed. You have received Ksh85,000.00 from OXFAM KENYA on 25/6/26 at 12:30 PM. New M-PESA balance is Ksh107,120.00.
SFC78Z9F0 Confirmed. Ksh350.00 paid to UBER on 2/7/26 at 8:45 AM. New M-PESA balance is Ksh3,970.00. Transaction cost, Ksh0.00.
SFD34W2A1 Confirmed. Ksh1,250.00 sent to JANE MUTHONI 254712345678 on 3/7/26 at 11:15 AM. New M-PESA balance is Ksh2,720.00. Transaction cost, Ksh23.00.
SFE67V8B2 Confirmed. Ksh100.00 sent to SAFARICOM PLC on 6/7/26 at 9:00 AM. Airtime purchase.
```

**After seeding**, open PesaTrack → Settings → Import from SMS → let the parser run → verify totals match Script 2's narrated numbers. Adjust seed amounts if needed and re-run.

**Alternative:** film on your own phone with your own real data. Faster, more honest, but be careful not to show identifying merchant names tied to real friends. Use the rename-recipient flow before filming to anonymise.

---

## 4. Hero still frames (2 stills, both 1:1 for Google ACi Play Store cards)

Each: 1080×1080 PNG. Each is a single frame lifted from its matching video, with one line of overlay text. Purpose: Google ACi's Play Store search-result cards + Discover cards. Not used on WhatsApp Status (WA Status runs the videos only).

### Hero Still 1 — from Script 1 (The Tour)

- **Image:** Screenshot of Home screen with populated data and the month total "KES 43,120" visible.
- **Overlay (top or bottom third):** *"Every M-PESA transaction, sorted for you."*

### Hero Still 2 — from Script 2 (Where did it go?)

- **Image:** Screenshot of the By Category card on Home with the top three rows visible (Transport, Groceries, Fees) and their KES amounts.
- **Overlay (top or bottom third):** *"Where did KES 47,320 go? PesaTrack knows."*

**Design notes:** Canva free tier is enough. Font: system sans-serif at 60-80pt for the overlay. High contrast (white text on translucent dark bar, or dark text on white bar). Do not obscure the app UI — the screenshot IS the ad. Keep overlay in the top or bottom 20% only.

---

## 5. UTM strings (copy-paste ready)

Play Store install URL with UTM. Replace `<var>` per ad.

```
https://play.google.com/store/apps/details?id=com.pesatrack&referrer=utm_source%3D<src>%26utm_medium%3Dcpc%26utm_campaign%3Dv141_launch%26utm_content%3D<variant>
```

| Ad | `src` | `variant` |
|---|---|---|
| Google App Campaign — video + hero still | `google_aci` | `mixed` (Google auto-assembles) |
| WhatsApp Status — Script 1 (Tour) | `wa_status` | `video_tour` |

Note: Google App Campaign doesn't let you set click-through URLs directly (it always sends to the Play Store). Instead, set the **campaign name** in Google Ads to `v141_launch_aci` — Google Play Referrer captures it automatically.

---

## 6. Google Ads setup (first-timer)

### 6.1 Prerequisites

1. Sign up at [ads.google.com](https://ads.google.com/) with the same Google account that owns your Play Console.
2. Add a payment method (KE M-PESA is supported for Google Ads billing).
3. In **Play Console → Setup → Integrations → Google Ads** → link the ads account. This unlocks App Campaigns.

### 6.2 Campaign A — App Campaign for Installs (ACi) — KES 4,000

1. Google Ads → **+ New Campaign** → Objective: **App promotion** → Subtype: **App installs** → Platform: **Android** → search "PesaTrack" and select your app.
2. Campaign name: `v141_launch_aci`
3. Locations: **Kenya**
4. Languages: **English, Swahili**
5. Budget: **KES 570/day** (7-day run = KES 3,990 ≈ 4k)
6. Bid strategy: **Target cost per install (tCPI)** → start at **KES 100**. Google needs 20-30 installs before it optimizes; expect first 2 days to be noisy.
7. Ad group: `mary_ke_android`
8. Ad assets (**placement-bias tactic — upload ONLY these formats, no others**):
   - **Videos:** upload the 15-second 9:16 video (Script 1 Tour). Also upload a 6-second cut as a bumper (still 9:16). **Do NOT upload a 16:9 landscape video** — this alone kills ~60% of YouTube in-stream eligibility.
   - **Images:** upload the 1:1 hero still. **Do NOT upload landscape (1.91:1) or HTML5 banners** — skipping these kills most Gmail-promotions serving.
   - **Headlines (max 30 chars each):** provide 5:
     - "Track M-PESA automatically"
     - "Budget for your pay cycle"
     - "See where money really goes"
     - "M-PESA expense tracker"
     - "No signup. Runs offline."
   - **Descriptions (max 90 chars each):** provide 5:
     - "Reads your M-PESA SMS on your phone. No cloud. No signup. Free."
     - "See spending, fees and savings on your salary cycle — not the calendar."
     - "Auto-categorise M-PESA transactions. Reconcile per-diems in one tap."
     - "Local-first. Your data never leaves your phone."
     - "Kenyan-built for M-PESA and mobile banking users."
9. Launch → wait 24-48 hours before touching anything.

### 6.2b Placement-bias tactics (why ACi will lean toward Play Store search + in-game video)

Google Ads doesn't let you hand-pick ACi surfaces. But you can bias the mix toward Play Store search results and in-app AdMob (games) inventory — the two surfaces that convert best for utility apps like PesaTrack — using three levers:

| Lever | Effect | How |
|---|---|---|
| **Creative constraints** | Kills 60-70% of unwanted YouTube in-stream + Gmail serving | Upload ONLY 9:16 video + 1:1 stills. No landscape, no HTML5 (per step 8 above). |
| **Day-3 asset pruning** | Cuts the remaining Discover / Gmail spend | Google Ads → Campaign → **Asset Details Report**. Turn off any asset showing high-CPI on Gmail or YouTube in-stream. |
| **ASO complement** | Wins Play Store search organically — free installs | See section 0. Play Store re-indexing takes 3-4 days, hence launch ads AFTER listing edits go live. |

With all three levers, expect ~60-75% of Google spend to land on Play Store surfaces + in-app inventory (per Google Ads benchmarks for finance apps in emerging markets). The remaining 25-40% goes to Discover / Shorts — acceptable spillover.

### 6.3 Google Search — deferred

Dropped for this run to concentrate spend where the ML has enough signal to learn (ACi) and where per-variant hook comparison is possible (WA Status). Revisit for the KES 30k round — the keyword list, negatives, and ad copy are preserved in git history and can be dropped back in verbatim.

---

## 7. Meta Ads setup — WhatsApp Status — KES 6,000

### 7.1 Prerequisites

1. Create a **Meta Business Account** at [business.facebook.com](https://business.facebook.com/) using your business email.
2. Create an **Ad Account** inside it. Currency: **KES**. Timezone: Africa/Nairobi.
3. Add payment method (Meta accepts M-PESA in KE via Cellulant integration, and Visa/Mastercard).
4. Create a **Facebook Page** for PesaTrack if you don't have one (Meta requires a Page to run ads, even for WA Status). Bare minimum: logo, one-line bio, link to Play Store.
5. Verify the domain if you have one (skip if not).

### 7.2 Campaign setup

1. **Ads Manager → + Create → Objective: App promotion → Continue**
2. Campaign name: `v141_launch_wa_status`
3. Buying type: Auction. Campaign budget: **KES 6,000 lifetime** (Meta prefers lifetime for short runs). Duration: 7 days.
4. Ad set:
   - App: link your Google Play app (Meta will fetch it by package name `com.pesatrack`).
   - Performance goal: **Maximize number of app installs**.
   - Cost per result goal: leave blank for first run (let Meta learn).
   - Schedule: 7-day window starting tomorrow midnight EAT.
   - Location: **Kenya**. Radius targeting: 25 km around Nairobi CBD (`-1.2921, 36.8219`). Also add Mombasa CBD 15 km. Also add Kisumu 15 km.
   - Age: **26-38**
   - Gender: All
   - Detailed targeting → Interests → search and add:
     - "Non-governmental organization"
     - "United Nations"
     - "Humanitarian aid"
     - "Devex"
     - "Save the Children"
     - "UNICEF"
     - "Oxfam"
     - "World Vision International"
     - "International development"
   - Detailed targeting → Behaviors → **Small business owners** (excludes — actually, do NOT add; keep audience broad enough to learn).
   - Placements: **Manual** → uncheck everything except **WhatsApp** → **Status**.
   - Optimization: automatic bidding.
5. Ad level — create 1 ad (single-video launch this round; Script 2 deferred to KES 30k round):

| Ad name | Format | Asset | Deep link URL |
|---|---|---|---|
| `wa_video_tour` | Video | Script 1 (The Tour) — 15-sec 9:16 | UTM URL with `variant=video_tour` |

6. Primary text (shows above the WA Status ad):
   - `wa_video_tour`: "Every M-PESA transaction, already sorted. No signup, no cloud."
7. CTA button: **Install Now**.
8. Publish → wait for review (usually 30 min to 24 h).

---

## 8. Launch checklist

Complete IN ORDER before spending:

- [ ] Play Store screenshots updated with v1.4.1 UI (salary-cycle label, income CSV entry visible)
- [ ] Play Store short description edited to include: *"Reconcile per-diems and field-trip spend with one tap."*
- [ ] ASO edits from §0 live in Play Console + 3-4 days of re-indexing elapsed
- [ ] Demo device / emulator prepped per §3a: 40-60 M-PESA SMS seeded across 2-6 months
- [ ] Script 1 (Tour) recorded, voiceover mixed, exported at 1080×1920 MP4 under 30 MB ✅ (done as of 2026-07-24)
- [ ] Video PII-scanned (no real recipient names, phone numbers, transaction codes on screen)
- [ ] One 1:1 hero still (§4 Hero Still 1) exported at 1080×1080 PNG
- [ ] Google Ads account created, billing added, linked to Play Console
- [ ] Meta Business Account + Ad Account created, Facebook Page created, billing added
- [ ] Both UTM URLs from §5 saved in a note (you'll paste them multiple times)
- [ ] Google Campaign A (ACi) built but paused
- [ ] Meta Campaign (WA Status, 1 video ad) built but paused
- [ ] Both campaigns unpaused within the same hour (so the 7-day windows align)

---

## 9. What to watch during the run

### Day 1-2 (learning phase)

Do **not** touch anything. Google ACi and Meta both need 24-48 hours to learn. Turning off ads early destroys the learning signal.

### Day 3 checkpoint

Open each dashboard:

- **Google Ads → Campaigns:** check CTR (target >2%), CPI (target <KES 150).
- **Meta Ads Manager:** is the single ad's CTR >0.5% and CPI trending toward <KES 180? If yes, let it run. If CTR is <0.3% by day 4, pause — the creative isn't landing on this audience.
- **Play Console → Acquisition Reports → filter by UTM source:** raw installs per channel.

### Day 7 (end of run)

Pull these numbers into a comparison table:

| Channel | Spend | Installs | CPI | D7-retained | D7 CPI |
|---|---:|---:|---:|---:|---:|
| Google ACi | | | | | |
| WA Status — video_tour | | | | | |

**Decision rule for the next run:**
- Channel with the lowest **D7 CPI** (not lowest CPI) wins 60% of the next budget.
- Second-place gets 30%. Third gets 10%.
- Any variant with CTR <0.5% or D7 retention <10% gets dropped.

### Kill switches (during the run, not after)

- If any campaign spends >KES 500 with zero installs by end of day 2 → **pause it**, check the audience and creative approval status.
- If Meta ad-set rejects for policy → check the Play Store link is exactly `https://play.google.com/store/apps/details?id=com.pesatrack` (WA Status ads are strict about destinations).
- If Google ACi CPI is above KES 200 at day 4 → lower target CPI to KES 80 and let it re-learn.

---

## 10. What we're deliberately NOT doing (and why)

| Not doing | Why |
|---|---|
| Firebase Analytics for UTM→D7 attribution | Violates local-first principle. Play Console retention-by-source is good enough for a 10k test. |
| TikTok ads | NGO Mary uses TikTok to unwind, not to find finance tools. Skew is too young + wrong intent. |
| LinkedIn paid ads | Right persona, wrong buying moment. KES 2,500-5,000 per install kills unit economics. Do organic instead. |
| Google Display Network banners | Cheap impressions, near-zero intent. Wastes budget. |
| YouTube standalone campaigns | ACi already covers YouTube inventory. |
| Retargeting pixel | We don't want to build a Meta pixel audience of PesaTrack users — that's tracking-adjacent and off-brand. |
| Discount codes / "first month free" hooks | Product is free; we have nothing to discount. And once we monetize, we don't want price-shoppers as our first cohort. |
| Emotional / fear framing ("Are you wasting money?") | Off-brand. Violates copy principle in AGENTS.md. |
| Streaks / gamification in ads | Off-brand. See product principles. |

---

## 11. Follow-ups (out of scope for this run)

- Organic LinkedIn: 1 post from your personal profile explaining v1.4.1 with the salary-cycle GIF. Zero cost.
- Devex / NGO KE WhatsApp groups: message 3-5 friends in the persona and ask them to try it (no group blast — that's spam).
- KE finance creator outreach (Abojani, Centonomy, Wakanai): reserve for the KES 30k+ round after we have D7 numbers to quote.
- Play Store listing A/B test (Play Console → Store Listing Experiments) — try the "per-diem" short description vs current, run for 14 days.

---

_Update this doc with actual numbers on day 7; keep as the running record of ad experiments._
