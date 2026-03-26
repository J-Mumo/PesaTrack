# Play Store Listing — Complete Preparation Guide

## Overview

This document contains everything needed to create the Google Play Store listing for PesaTrack. It covers store copy, screenshot plan, feature graphic, Data Safety form, SMS Permission Declaration, and content rating.

---

## 1. App Identity

| Field | Value |
|-------|-------|
| **App name** | PesaTrack |
| **Developer name** | JMumo Technologies |
| **Package name** | `com.pesatrack` |
| **Default language** | English (United States) |
| **App type** | Application |
| **Category** | Finance |
| **Free / Paid** | Free |
| **Contact email** | joelmumo.jm@gmail.com |
| **Privacy policy URL** | https://j-mumo.github.io/PesaTrack/privacy-policy.html |

---

## 2. Store Listing Copy

### Short Description (max 80 characters)

```
Track M-PESA & bank expenses automatically from SMS. 100% offline & private.
```

**Character count:** 76 ✅

### Full Description (max 4000 characters)

```
PesaTrack automatically tracks your M-PESA and bank expenses by reading transaction SMS messages. No manual entry needed — just spend, and PesaTrack logs it.

🔒 100% PRIVATE — ALL DATA STAYS ON YOUR DEVICE
PesaTrack has no internet permission. Your financial data never leaves your phone. No servers, no cloud, no analytics, no ads. Just you and your data.

📱 HOW IT WORKS
When you send money via M-PESA, buy goods, pay a bill, or withdraw from an agent, PesaTrack reads the confirmation SMS and automatically extracts the transaction details — amount, recipient, date, and transaction code.

💰 KEY FEATURES

• Automatic SMS Tracking — Detects M-PESA and NCBA bank transactions in real time
• Historical Import — Import past M-PESA SMS to backfill your expense history
• Smart Categorization — 18 category groups with 90+ sub-categories; auto-categorizes common merchants using built-in rules or your own custom rules
• Batch Categorize — Group uncategorized expenses by recipient and categorize them all at once
• Budget Tracking — Set monthly, weekly, or yearly budgets per category with alerts at 80% and 100%
• Analytics & Charts — Monthly trends, category breakdowns, top spenders, payment type analysis, and year-over-year comparisons
• Excel Import — Import M-PESA statements from Excel spreadsheets
• Manual Entry — Add cash and other expenses manually
• Custom Categories — Create your own category groups and sub-categories
• Auto-Rules — Define custom rules to automatically categorize future transactions
• Export to CSV — Share your expense data via any app
• PIN Lock — Secure your financial data with a 4-digit PIN and optional fingerprint/face unlock

📊 ANALYTICS
See where your money goes with visual charts:
• Monthly spending trends
• Category breakdown with color-coded bars
• Daily spending patterns
• Top recipients by amount
• Payment type distribution
• Month-over-month and year-over-year comparisons
• Variable-spend category detection

🏦 SUPPORTED SMS SOURCES
• M-PESA (Safaricom)
• NCBA Bank
• More banks coming soon

🇰🇪 BUILT FOR KENYA
PesaTrack understands Kenyan financial services — M-PESA transaction formats, Kenyan bank SMS patterns, and local merchant names. Categories include Kenya-specific items like Chama contributions, SACCO savings, NHIF/NSSF, and M-PESA transaction costs.

⚡ GETTING STARTED
1. Install PesaTrack
2. Grant SMS permission (we only read M-PESA and bank messages)
3. Import your existing M-PESA SMS history
4. Start tracking automatically!

PesaTrack is free, open source, and respects your privacy. No account needed. No internet required. Your money, your data, your device.
```

**Character count:** ~1,950 ✅ (well under 4,000 limit)

### What's New (Release Notes for v1.0.0)

```
🎉 First release!

• Automatic M-PESA & NCBA bank SMS tracking
• Historical SMS import with date range picker
• 18 category groups with 90+ sub-categories
• Smart auto-categorization (100+ merchant rules)
• Custom categories and auto-rules
• Category & sub-category budgets with alerts
• Analytics: trends, breakdowns, YoY comparisons
• Excel spreadsheet import
• Manual expense entry
• Export to CSV
• PIN lock with biometric unlock
• First-launch onboarding
• 100% offline — no internet permission
```

---

## 3. Screenshots

### Requirements

| Device type | Min | Max | Size | Format |
|-------------|-----|-----|------|--------|
| **Phone** (required) | 2 | 8 | 16:9 or 9:16, min 320px, max 3840px | JPEG or PNG (24-bit, no alpha) |
| 7-inch tablet | 0 | 8 | Same ratios | Same |
| 10-inch tablet | 0 | 8 | Same ratios | Same |

### Recommended Screenshot Set (8 phone screenshots)

Capture these screens in order — they tell the story of the app:

| # | Screen | What to show | Caption overlay |
|---|--------|-------------|-----------------|
| 1 | **Onboarding — Welcome** | Welcome page with PesaTrack intro | "Track M-PESA expenses automatically" |
| 2 | **Home Screen** | Monthly summary, recent expenses, budget card | "See your monthly spending at a glance" |
| 3 | **Analytics — Monthly** | Trend chart + category breakdown | "Understand where your money goes" |
| 4 | **Expense List** | Scrollable expense list with category colors | "Every transaction, auto-categorized" |
| 5 | **Budget Screen** | Budget list with progress bars (green/amber/red) | "Set budgets and get alerted" |
| 6 | **Batch Categorize** | Recipient groups with category suggestions | "Categorize in bulk by recipient" |
| 7 | **Settings — Security** | PIN lock toggle, biometric, timeout | "Secure with PIN & fingerprint" |
| 8 | **Import History** | SMS import screen with date picker | "Import your M-PESA history" |

### Screenshot Tips

- Use a clean device or emulator with representative sample data
- Ensure the status bar shows a realistic time (e.g., 10:30), full battery, good signal
- Use dark or light theme consistently across all screenshots
- Overlay captions should use a consistent style: white text on a semi-transparent dark bar at top or bottom, or use a phone mockup frame
- Recommended tool for mockup frames: **screenshots.pro**, **AppLaunchpad**, or Figma

### How to Capture Screenshots

1. Build and install release APK on a real device or emulator (Pixel 7 recommended — 1080×2400)
2. Populate with sample data (import real or test M-PESA SMS)
3. Navigate to each screen and capture with `adb exec-out screencap -p > screenshot_N.png`
4. Optionally add phone frame mockups and caption text using a design tool

---

## 4. App Icon (512 × 512 PNG)

### Current Icon

The app uses a vector drawable ([`ic_launcher_foreground.xml`](../android/app/src/main/res/drawable/ic_launcher_foreground.xml:1)) showing a white wallet outline with a green checkmark on the adaptive icon green background.

### Play Store Requirement

Google Play requires a separate **512 × 512 px PNG** (high-res icon). This should be visually identical to the launcher icon but exported at the higher resolution.

### How to Generate

**Option A — Android Studio:** Right-click `res` → New → Image Asset → select the existing foreground + background → export at 512px.

**Option B — Manual:** Create a 512×512 canvas with `#1B5E20` (dark green) background, centered wallet+checkmark at 70% size, rounded corners (20% radius per Google spec — the Play Console will apply masking automatically).

> **Note:** The 512px icon does NOT need rounded corners — Google Play applies its own mask. Upload as a full square.

---

## 5. Feature Graphic (1024 × 500 PNG)

### Design Concept

```
┌─────────────────────────────────────────────────────────────────┐
│                                                                 │
│   [Dark green gradient background: #1B5E20 → #2E7D32]          │
│                                                                 │
│        PesaTrack                                                │
│        ─────────                                                │
│        Track M-PESA expenses                                    │
│        automatically from SMS                   [Phone mockup   │
│                                                  showing Home   │
│        🔒 100% Offline & Private                 Screen]        │
│        📊 Smart Analytics                                       │
│        💰 Budget Alerts                                         │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

**Elements:**
- Dark green gradient matching brand color (`#1B5E20` → `#2E7D32`)
- App name "PesaTrack" in white, bold, large
- 2–3 key value props as bullet points
- Phone mockup on the right showing the Home Screen or Analytics chart
- No text smaller than 24pt (it gets compressed to a small banner)

**Tool recommendation:** Canva (free tier), Figma, or AppLaunchpad feature graphic template.

---

## 6. Data Safety Form

The Data Safety section is mandatory as of July 2022. Here are the exact answers for PesaTrack:

### Overview Questions

| Question | Answer | Notes |
|----------|--------|-------|
| Does your app collect or share any of the required user data types? | **Yes** | Financial data is collected from SMS |
| Is all collected data encrypted in transit? | **N/A — No data transmitted** | App has no INTERNET permission |
| Do you provide a way for users to request that their data is deleted? | **Yes** | Clear app data or uninstall |

### Data Types Collected

| Data Type | Collected? | Shared? | Purpose | Optional? |
|-----------|-----------|---------|---------|-----------|
| **Financial info → Purchase history** | ✅ Yes | ❌ No | App functionality | Required |
| **Messages → SMS** | ✅ Yes | ❌ No | App functionality | Required |
| **App activity → App interactions** | ❌ No | — | — | — |
| **Device or other IDs** | ❌ No | — | — | — |
| **Location** | ❌ No | — | — | — |
| **Personal info** | ❌ No | — | — | — |
| **Contacts** | ❌ No | — | — | — |
| **Photos/Videos** | ❌ No | — | — | — |

### For each collected data type:

#### Financial info — Purchase history

| Question | Answer |
|----------|--------|
| Is this data collected, shared, or both? | Collected |
| Is this data processed ephemerally? | No (persisted in local database) |
| Is this data required for your app, or can users choose whether it is collected? | Required |
| Why is this user data collected? | App functionality |

#### Messages — SMS or MMS

| Question | Answer |
|----------|--------|
| Is this data collected, shared, or both? | Collected |
| Is this data processed ephemerally? | Yes — raw SMS text is parsed and discarded; only extracted fields are stored |
| Is this data required for your app, or can users choose whether it is collected? | Required |
| Why is this user data collected? | App functionality |

### Data handling summary text (shown to users):

> **No data shared with third parties.** This app collects financial transaction data from M-PESA and bank SMS messages. All data is stored locally on your device and is never transmitted to any server. The app does not have internet permission. You can delete all data by clearing app data or uninstalling.

---

## 7. SMS Permission Declaration

This is the **critical blocker** for Play Store approval. Google requires a declaration form for apps using `READ_SMS` or `RECEIVE_SMS`.

### Declaration Form Answers

**Question: Which restricted permissions does your app use?**
- `android.permission.READ_SMS`
- `android.permission.RECEIVE_SMS`

**Question: What is the core function of your app that requires these permissions?**

> PesaTrack is a passive financial expense tracker for M-PESA mobile money users in Kenya. Its core function is to automatically read incoming M-PESA and bank transaction confirmation SMS messages, parse the transaction details (amount, recipient, date, transaction code), and save them locally on the user's device for expense tracking, categorization, and budgeting.
>
> READ_SMS is needed to import historical transaction SMS from the user's inbox (user-initiated). RECEIVE_SMS is needed to detect new transactions in real time as M-PESA confirmation SMS arrive.
>
> The app ONLY reads SMS from specific financial senders (MPESA, NCBA). Personal messages are never accessed. All data stays 100% on-device — the app does not have INTERNET permission and cannot transmit data off the device.

**Question: Why is the default SMS handler role not suitable?**

> PesaTrack does not send, receive, or manage SMS messages. It does not replace the user's SMS app. It only reads M-PESA and bank transaction confirmation messages from specific senders to extract financial data for expense tracking. The default SMS handler role would give unnecessary full SMS access and confuse users who expect their regular SMS app to remain the default.

**Question: Please provide a video demonstration.**

Record a ~2 minute screen recording showing:

1. **App install and onboarding** — First launch, onboarding pages, SMS permission grant
2. **Real-time SMS detection** — Receive an M-PESA SMS → notification appears → expense auto-logged
3. **Historical import** — Navigate to Import screen → select date range → SMS imported
4. **Expense categorization** — Show categorized expenses in the list
5. **Privacy emphasis** — Show Settings screen (no internet-related settings), About screen showing "100% offline"
6. **Only financial SMS** — Show that the app only reads from "MPESA" and "NCBA" senders

> **Tip:** Use a real device with actual M-PESA messages for the video — it's more convincing to Google's review team than emulated data.

---

## 8. Content Rating (IARC Questionnaire)

| Question | Answer |
|----------|--------|
| Does the app contain violence? | No |
| Does the app contain sexual content? | No |
| Does the app contain drug references? | No |
| Does the app contain gambling? | No |
| Does the app contain user-generated content? | No |
| Does the app allow communication between users? | No |
| Does the app share the user's location? | No |
| Does the app allow purchases? | No |
| Does the app contain ads? | No |

**Expected rating:** Everyone (E) / PEGI 3

### Target Audience

| Question | Answer |
|----------|--------|
| Target age group | 18+ (financial app handling transaction data) |
| Is this app directed at children? | **No** |
| Does the app appeal to children? | **No** |

---

## 9. Additional Settings

| Setting | Value |
|---------|-------|
| **Countries** | All countries (or Kenya-only if preferred) |
| **Contains ads** | No |
| **App access** | All functionality is accessible without special access |
| **Government apps** | No |
| **Health apps** | No |
| **News apps** | No |
| **COVID-19 apps** | No |

### Countries Recommendation

Start with **Kenya only** since:
- The app is specifically built for M-PESA (Kenya's dominant mobile money)
- Bank parsers are for Kenyan banks (NCBA)
- Categories include Kenya-specific items (NHIF, NSSF, SGR, Boda Boda)
- SMS sender IDs (MPESA, NCBA) are Kenya-specific

You can expand to other countries later when more parsers are added (Tanzania, Uganda M-PESA, etc.).

---

## 10. Pre-Submission Checklist

| # | Task | Status | Notes |
|---|------|--------|-------|
| 1 | Write short description (80 chars) | ✅ Ready | See Section 2 |
| 2 | Write full description (4000 chars) | ✅ Ready | See Section 2 |
| 3 | Write release notes (What's New) | ✅ Ready | See Section 2 |
| 4 | Capture 8 phone screenshots | ⬜ Needs device | See Section 3 |
| 5 | Generate 512×512 high-res icon | ⬜ Needs export | See Section 4 |
| 6 | Create 1024×500 feature graphic | ⬜ Needs design | See Section 5 |
| 7 | Complete Data Safety form | ✅ Answers ready | See Section 6 |
| 8 | Prepare SMS Permission Declaration | ✅ Answers ready | See Section 7 |
| 9 | Record SMS demo video (~2 min) | ⬜ Needs real device | See Section 7 |
| 10 | Complete IARC content rating | ✅ Answers ready | See Section 8 |
| 11 | Privacy policy live on GitHub Pages | ✅ Done | https://j-mumo.github.io/PesaTrack/privacy-policy.html |
| 12 | Generate signed AAB | ⬜ Needs keystore | See signed-apk-playstore-plan.md |

### Items That Need YOU (the developer)

These cannot be automated and require your direct action:

1. **Screenshots** — Install on a real device with sample M-PESA data, navigate to each screen, capture
2. **Feature graphic** — Use Canva/Figma with the design concept above
3. **512×512 icon** — Export from Android Studio Image Asset wizard
4. **SMS demo video** — Screen record on a real device receiving an M-PESA SMS
5. **Signed AAB** — Generate keystore and build (see [`signed-apk-playstore-plan.md`](signed-apk-playstore-plan.md))

---

## 11. App Store Optimization (ASO) Keywords

Google Play uses the title, short description, and full description for search ranking. Key terms to include (all present in the copy above):

| Keyword | Search Volume (Kenya) | Present? |
|---------|----------------------|----------|
| M-PESA | High | ✅ |
| mpesa tracker | Medium | ✅ |
| expense tracker | High | ✅ |
| money tracker | High | ✅ (implied) |
| budget | High | ✅ |
| SMS | Medium | ✅ |
| Kenya | Medium | ✅ |
| offline | Medium | ✅ |
| privacy | Medium | ✅ |
| Safaricom | Medium | ✅ |
| NCBA | Low | ✅ |
| finance | High | ✅ (category) |
