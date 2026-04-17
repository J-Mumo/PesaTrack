# PesaTrack Virality Plan

## The Challenge

PesaTrack is a **private, offline utility** — there's no inherent social loop like messaging or social media apps. Virality for utility apps works differently; it's driven by **shareable outcomes** and **word-of-mouth triggers**, not network effects.

---

## Virality Framework

```
User gets value → User has a "wow" moment → User tells someone → That person installs
```

PesaTrack's "wow" moments:

| Moment | Emotional Trigger |
|--------|------------------|
| "I didn't know I spent THAT much on eating out" | Category breakdown shock |
| "I've been losing KES 2,000/month in transaction costs" | Hidden cost revelation |
| "It just works — I didn't enter anything" | Zero-effort magic |
| "My data never leaves my phone" | Trust / privacy relief |

**Key insight:** PesaTrack's viral loop is **outcome-based, not network-based**. Users don't need friends on the app — they share because the insights are surprising and personal.

---

## Part 1: In-App Viral Features

### 1.1 Shareable Monthly Summary Card ⭐ (Highest Priority)

Generate a beautiful, branded image the user can share to WhatsApp/Instagram/Twitter.

**Design:**

```
┌─────────────────────────────────┐
│  📊 My April Spending           │
│  ─────────────────────────      │
│  Total: KES 47,250              │
│                                 │
│  🍔 Food & Dining    KES 12,400 │
│  🚗 Transport        KES  8,200 │
│  📱 Digital & Tech   KES  5,100 │
│  📈 Invested         KES 15,000 │
│  ...                            │
│                                 │
│  💡 Transaction costs: KES 890  │
│                                 │
│  Tracked automatically by       │
│  PesaTrack 📱                   │
│  pesatrack.co.ke                │
└─────────────────────────────────┘
```

**Why it works:** People love sharing financial wins ("I invested 32% of my income!"). The card is branded with PesaTrack's name and URL — every share is a free ad. This is exactly how Spotify Wrapped, Strava, and Duolingo go viral.

**Implementation:**

| Component | Details |
|-----------|---------|
| **Image generation** | Compose `Canvas` → `Bitmap` → save to cache dir |
| **Share mechanism** | Android `ShareSheet` via `FileProvider` (no internet needed) |
| **Trigger** | "Share" button on Home screen monthly summary + Analytics screen |
| **Content** | Top 5 categories by spend, total, investment %, transaction costs total |
| **Branding** | PesaTrack logo, app name, website URL at bottom |
| **Privacy** | No actual transaction details, amounts, or recipients — only aggregated category totals. User controls what gets shared. |

**Files to create/modify:**

| File | Change |
|------|--------|
| `services/ShareCardService.kt` | New — generates Bitmap from spending data using Compose Canvas |
| `presentation/components/ShareableSummaryCard.kt` | New — Composable that renders the card visually |
| `presentation/screens/home/HomeScreen.kt` | Add "Share" icon button on Monthly Summary card |
| `presentation/screens/analytics/AnalyticsScreen.kt` | Add "Share" button in monthly view |
| `presentation/screens/home/HomeViewModel.kt` | Add share data preparation method |
| `res/xml/file_paths.xml` | Ensure cache path is registered for FileProvider |

**Effort:** ~4 hours

---

### 1.2 "Share PesaTrack" WhatsApp Button

One-tap share in Settings that generates a pre-written WhatsApp message:

> *"I've been using PesaTrack to automatically track my M-PESA spending. It reads your SMS locally — no internet, no servers. Try it: https://play.google.com/store/apps/details?id=com.pesatrack"*

**Implementation:**

| Component | Details |
|-----------|---------|
| **Location** | Settings screen — new "Share PesaTrack" row with share icon |
| **Mechanism** | Android `Intent.ACTION_SEND` with `text/plain` |
| **Message** | Pre-written, natural-sounding (not spammy) |

**Files to modify:**

| File | Change |
|------|--------|
| `presentation/screens/settings/SettingsScreen.kt` | Add "Share PesaTrack" row in a new "Spread the Word" section |

**Effort:** ~1 hour

---

### 1.3 "Rate on Play Store" Smart Prompt

Trigger a rating prompt when the user is most engaged — not on first launch, not randomly.

**Trigger conditions (ALL must be true):**
- User has categorized ≥20 expenses (invested in the app)
- User has used the app for ≥7 days
- User has NOT been prompted in the last 30 days
- User has NOT permanently dismissed the prompt

**Implementation:**

| Component | Details |
|-----------|---------|
| **API** | Google In-App Review API (`ReviewManager`) — shows native Play Store review dialog |
| **Tracking** | `AppPreferences`: `lastRatingPromptTime`, `ratingPromptDismissed` |
| **Trigger point** | After categorizing an expense (positive action = positive mood) |

**Files to create/modify:**

| File | Change |
|------|--------|
| `services/RatingPromptService.kt` | New — checks conditions, triggers ReviewManager |
| `data/local/preferences/AppPreferences.kt` | Add rating prompt tracking keys |
| `presentation/screens/categorize/CategorizeViewModel.kt` | Call rating check after successful categorization |
| `app/build.gradle.kts` | Add `com.google.android.play:review-ktx` dependency |

**Effort:** ~2 hours

---

### 1.4 Insight Notifications with Share CTA

After the app calculates something surprising, notify the user and offer a share action:

| Insight | Notification Text | Share Text |
|---------|-------------------|------------|
| High transaction costs | "💡 You spent KES 3,200 on M-PESA charges this month" | "M-PESA charges cost me KES 3,200 this month 😭 Track yours with PesaTrack" |
| Budget win | "🎯 You stayed under ALL your budgets this month!" | "Stayed under all my budgets this month! 💪 Tracked with PesaTrack" |
| Spending decrease | "📉 Food spending is down 18% from last month" | "Cut my food spending by 18% this month 📉 PesaTrack helped me see it" |
| Milestone | "📊 PesaTrack has tracked 500 transactions for you" | "500 M-PESA transactions tracked automatically by PesaTrack 📱" |

**Implementation:**

| Component | Details |
|-----------|---------|
| **Trigger** | End of month (or on first app open after month changes) |
| **Notification** | Existing `NotificationHelper` with new "Insights" channel |
| **Share action** | `PendingIntent` with share text via `Intent.ACTION_SEND` |

**Files to create/modify:**

| File | Change |
|------|--------|
| `services/InsightService.kt` | New — computes monthly insights, decides which to surface |
| `services/NotificationHelper.kt` | Add "Monthly Insights" channel + insight notification builder with share action |
| `presentation/screens/home/HomeViewModel.kt` | Trigger insight check on month change |

**Effort:** ~3 hours

---

### 1.5 Year-End Wrapped (Seasonal — December/January)

"PesaTrack Wrapped" — a shareable series of cards summarizing the year:

**Card sequence:**

1. "You made **1,247 M-PESA transactions** in 2026"
2. "Your biggest spending month was **December** (KES 98,000)"
3. "Your top category: **Food & Dining** (KES 156,000)"
4. "You invested **KES 420,000** this year 📈 (35% of spending)"
5. "Transaction costs ate **KES 11,200** — that's a flight to Mombasa ✈️"
6. "Your most diligent budget month was **March** (3/3 budgets met)"

**Implementation:**

| Component | Details |
|-----------|---------|
| **UI** | Full-screen HorizontalPager with animated cards (like Spotify Wrapped) |
| **Share** | Each card is individually shareable as an image |
| **Trigger** | Banner on Home screen in late December / early January |
| **Data source** | Existing DAO queries aggregated for the full year |

**Files to create/modify:**

| File | Change |
|------|--------|
| `presentation/screens/wrapped/WrappedScreen.kt` | New — HorizontalPager with animated stat cards |
| `presentation/screens/wrapped/WrappedViewModel.kt` | New — loads annual stats |
| `presentation/screens/wrapped/WrappedUiState.kt` | New — annual stats data class |
| `services/ShareCardService.kt` | Add wrapped card bitmap generation |
| `presentation/navigation/NavGraph.kt` | Add Wrapped route |
| `presentation/screens/home/HomeScreen.kt` | Add seasonal banner card |

**Effort:** ~8 hours

---

### 1.6 Budget Challenge Sharing (Future)

Let users set a public goal and share progress:

- "I'm cutting eating out to KES 5,000/month"
- Weekly progress images auto-generated
- Share to WhatsApp groups for accountability

Turns budgeting into a **social commitment** — people who publicly commit to goals are more likely to follow through AND to tell others about the tool.

**Effort:** ~6 hours (deferred)

---

## Part 2: External Viral Strategies

### 2.1 Transaction Cost Awareness Campaign

The **transaction cost revelation** is PesaTrack's single most viral topic. Kenyans care deeply about M-PESA charges.

**Campaign:**

| Platform | Content |
|----------|---------|
| **Twitter/X** | "How much have M-PESA transaction costs taken from you this year? I found out mine: KES 11,200 😭 #MpesaCharges #PesaTrack" |
| **TikTok / YouTube Shorts** | Screen recording: install PesaTrack → import SMS history → see transaction cost total → shocked reaction |
| **Blog post** | "Why Your M-PESA Transaction Costs Add Up (And How to See Them)" — SEO target |
| **WhatsApp status** | Share monthly transaction cost card |

**Why it works:** Transaction costs are an emotional, universal pain point for M-PESA users. The insight is genuinely useful AND shareable. It positions PesaTrack as the tool that reveals what Safaricom doesn't show you.

### 2.2 "Financial Awareness" Content Series

| Topic | Format | Viral Angle |
|-------|--------|-------------|
| "Where Kenyans Actually Spend" | Infographic (from anonymized benchmarks) | Relatable comparisons |
| "The Eating Out Tax" | Short video / blog | "I was spending KES 15K/month on takeaway without knowing" |
| "Invisible Spending" | Twitter thread | Categories people don't track: parking, data bundles, Uber tips |
| "M-PESA vs Bank: Which Costs More?" | Blog post | Comparative analysis drives engagement |

### 2.3 Chama / Group Finance Multiplier

PesaTrack already has a "Chama Contributions" category. Lean into this community angle:

- One Chama member recommends PesaTrack → potentially 10–30 members install
- Chama treasurers can use PesaTrack to track group contributions
- WhatsApp groups are the natural distribution channel

**Action:** Create a "Recommended for Chama members" callout in the app's share text.

### 2.4 Comparison Benchmarks (Future — Opt-In)

"Your eating out spend (KES 12,400) is higher than 70% of PesaTrack users in Nairobi."

**Caveat:** This conflicts with the offline-only model. Two possible approaches:
1. **Published benchmarks:** Use publicly available data (KNBS, FSD Kenya) as comparison points — no user data leaves the device
2. **Opt-in aggregation:** Users can anonymously contribute category totals to a public benchmark API — fully optional

---

## Part 3: Viral Loop Design

```
┌──────────────────────────────────────────────────────────────┐
│                                                              │
│  INSTALL ──► USE (auto-tracking) ──► INSIGHT ("wow" moment)  │
│                                          │                   │
│                                          ▼                   │
│                                    SHARE (card/text)         │
│                                          │                   │
│                              ┌───────────┼───────────┐       │
│                              ▼           ▼           ▼       │
│                          WhatsApp    Twitter    Instagram     │
│                              │           │           │       │
│                              └───────────┼───────────┘       │
│                                          ▼                   │
│                                   FRIEND SEES IT             │
│                                          │                   │
│                                          ▼                   │
│                              "What app is that?"             │
│                                          │                   │
│                                          ▼                   │
│                              Play Store ──► INSTALL           │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

**Key metric:** Viral coefficient (K-factor) = invites sent per user × conversion rate per invite

- **Target K > 0.3** means each 10 users bring 3 more → sustainable organic growth
- WhatsApp in Kenya has near-100% reach → high conversion potential

---

## Implementation Roadmap

### Phase 1: Quick Wins (Week 1 — ~8 hours total)

| # | Feature | Effort | Impact |
|---|---------|--------|--------|
| 1 | Shareable Monthly Summary Card | 4h | ⭐⭐⭐⭐⭐ |
| 2 | "Share PesaTrack" WhatsApp button in Settings | 1h | ⭐⭐⭐ |
| 3 | "Rate on Play Store" smart prompt | 2h | ⭐⭐⭐ |
| 4 | Transaction cost awareness social posts (content) | 1h | ⭐⭐⭐⭐ |

### Phase 2: Engagement Drivers (Month 1 — ~6 hours)

| # | Feature | Effort | Impact |
|---|---------|--------|--------|
| 5 | Monthly insight notifications with share CTA | 3h | ⭐⭐⭐⭐ |
| 6 | Share button on Analytics charts | 2h | ⭐⭐⭐ |
| 7 | "Financial awareness" blog post series (content) | 1h/post | ⭐⭐⭐ |

### Phase 3: Seasonal & Advanced (December or Later)

| # | Feature | Effort | Impact |
|---|---------|--------|--------|
| 8 | Year-End Wrapped | 8h | ⭐⭐⭐⭐⭐ (seasonal) |
| 9 | Budget challenge sharing | 6h | ⭐⭐⭐ |
| 10 | Comparison benchmarks (published data) | 4h | ⭐⭐⭐ |

---

## Privacy Considerations

All viral features must respect PesaTrack's core privacy promise:

| Principle | Implementation |
|-----------|---------------|
| **User controls what's shared** | Share cards show only aggregated category totals — never individual transactions, recipients, or phone numbers |
| **No automatic sharing** | Every share requires explicit user tap |
| **No tracking pixels** | Shared images contain no analytics or tracking |
| **No data leaves without consent** | All image generation happens on-device; share uses Android's native ShareSheet |
| **Opt-out friendly** | Share prompts are dismissible; no dark patterns |

---

## Success Metrics

| Metric | How to Measure | Target (Month 1) |
|--------|---------------|-------------------|
| Share card generations | In-app counter (local only) | 50+ |
| "Share PesaTrack" taps | In-app counter (local only) | 20+ |
| Play Store rating prompts shown | In-app counter (local only) | 30+ |
| Play Store rating | Play Console | ≥4.5 ⭐ |
| Organic installs (from shares) | Play Console acquisition reports | Growing week-over-week |
| Social media mentions | Manual Twitter/Reddit search | 10+ |

> **Note:** All metrics are tracked locally on-device via simple counters in DataStore — consistent with PesaTrack's no-analytics, no-internet architecture. Play Console provides install/rating data directly.

---

## The #1 Takeaway

**Build the shareable monthly summary card first.** It turns every engaged user into a potential billboard. The transaction cost total alone is the kind of thing Kenyans will screenshot and share in WhatsApp groups without any prompting — PesaTrack just needs to make it easy and beautiful to share.
