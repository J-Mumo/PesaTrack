# Signed APK → Play Store: Full Walkthrough for PesaTrack

## Overview Flow

```
Generate Signing Keystore
    → Configure build.gradle.kts
    → Build Signed AAB
    → Create Google Play Developer Account
    → Create App Listing
    → Prepare Store Assets
    → Complete Data Safety Form
    → Submit Permission Declarations
    → Upload AAB to Internal Testing
    → Test on Real Devices
    → Promote to Closed/Open Testing
    → Submit for Production Review
    → Google Review
        → Approved → Live on Play Store
        → Rejected → Address Feedback & Resubmit
```

---

## Phase 1: Generate the Signed Build

### Step 1: Create a Signing Keystore (Upload Key)

```bash
keytool -genkey -v -keystore pesatrack-upload.jks -keyalg RSA -keysize 2048 -validity 10000 -alias pesatrack-upload
```

You'll be prompted for keystore password, key alias password, your name, org, city, and country (KE).

> **Note:** We're calling this an **upload key** (not "release key") because with Google Play App Signing enabled, this key is only used to sign the AAB you upload. Google re-signs the final APK with their own app signing key before delivering to users.

### Step 1b: Enable Google Play App Signing (Recommended)

Google Play App Signing is the **recommended approach** and is now the default for all new apps on Google Play. Here's how it works and what to do:

#### How It Works — Two Keys

```
┌─────────────────────────────────────────────────────────────────┐
│  WITHOUT Play App Signing (old way)                             │
│                                                                 │
│  You sign AAB ──[your key]──► Google Play ──[same key]──► User  │
│  ⚠️ Lose the key = can never update the app                    │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│  WITH Play App Signing (recommended)                            │
│                                                                 │
│  You sign AAB ──[upload key]──► Google Play ──[app signing      │
│                                                key]──► User     │
│  ✅ Lose upload key? Google can reset it for you                │
│  ✅ App signing key is stored securely by Google                │
└─────────────────────────────────────────────────────────────────┘
```

| Key | Who Holds It | Purpose | Replaceable? |
|-----|-------------|---------|-------------|
| **App signing key** | Google | Signs the final APK delivered to users | ❌ Never changes |
| **Upload key** | You | Signs the AAB you upload to Play Console | ✅ Can be reset if lost |

#### Steps to Enable (New App)

For a **brand new app** (first upload to Play Store), Google Play App Signing is **automatically enabled**:

1. **Generate your upload keystore** (Step 1 above — `pesatrack-upload.jks`)
2. **Sign your AAB** with this upload key (Step 3 below)
3. **Upload the AAB** to Google Play Console
4. **Done** — Google automatically:
   - Generates and securely stores the app signing key
   - Uses your upload key to verify future uploads
   - Re-signs APKs with the app signing key for distribution

There is **no extra configuration needed** — it's on by default for all new apps created after August 2021.

#### What You Get in Play Console

After your first upload, go to **Setup → App signing** in the Play Console. You'll see:

- **App signing key certificate** — the SHA-256 fingerprint Google uses to sign APKs
- **Upload key certificate** — your upload key's SHA-256 fingerprint
- **Upload key reset** button — use this if you lose your upload keystore

#### If You Lose Your Upload Key

This is the **key advantage** of Play App Signing:

1. Go to Play Console → **Setup → App signing**
2. Click **Request upload key reset**
3. Generate a new upload keystore:
   ```bash
   keytool -genkey -v -keystore pesatrack-upload-new.jks -keyalg RSA -keysize 2048 -validity 10000 -alias pesatrack-upload
   ```
4. Export the new upload certificate:
   ```bash
   keytool -export -rfc -keystore pesatrack-upload-new.jks -alias pesatrack-upload -file upload_certificate.pem
   ```
5. Upload the `.pem` file to the Play Console reset form
6. Google reviews and approves the reset (usually 1–2 business days)
7. Sign future AABs with the new keystore

Without Play App Signing, losing the key = **permanently unable to update the app**. With it, you just reset and continue.

#### Local Backup (Still Important)

Even with Play App Signing, **back up your upload keystore**:
- Store `pesatrack-upload.jks` in a password manager (e.g., 1Password, Bitwarden)
- Store the keystore password and key password alongside it
- The reset process takes 1–2 days — having a backup means zero downtime

> **⚠️ CRITICAL:** Back up `pesatrack-upload.jks` and passwords in a password manager. While Play App Signing means a lost upload key is recoverable (unlike the old model), the reset takes 1–2 business days during which you can't push updates.

### Step 2: Configure Signing in `build.gradle.kts` ✅ Already Done

The signing config is already configured in `app/build.gradle.kts`. It uses the upload key naming:

```kotlin
android {
    signingConfigs {
        create("release") {
            // Upload key for Google Play App Signing
            storeFile = file("../pesatrack-upload.jks")
            storePassword = System.getenv("KEYSTORE_PASSWORD")
                ?: project.findProperty("KEYSTORE_PASSWORD") as String? ?: ""
            keyAlias = "pesatrack-upload"
            keyPassword = System.getenv("KEY_PASSWORD")
                ?: project.findProperty("KEY_PASSWORD") as String? ?: ""
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
}
```

In `android/local.properties` (already in `.gitignore`):
```properties
KEYSTORE_PASSWORD=your_keystore_password
KEY_PASSWORD=your_key_password
```

### Step 3: Build a Signed AAB (not APK)

Google Play **requires** Android App Bundles (`.aab`), not raw APKs:

```bash
cd android && ./gradlew bundleRelease
```

Output: `android/app/build/outputs/bundle/release/app-release.aab`

> **Why AAB over APK?** Google generates optimized APKs per device configuration (ABI, screen density, language). This is especially helpful for PesaTrack since Apache POI adds significant size — AAB will strip unused resources per device.

### Step 4: Verify the Release Build

Test the release build on a device before uploading:
```bash
./gradlew assembleRelease
adb install app/build/outputs/apk/release/app-release.apk
```

Verify: SMS parsing works, ProGuard didn't strip Room entities or POI classes, notifications display, all screens navigate properly.

---

## Phase 2: Pre-Release Cleanup

### Items to Fix in the Codebase Before Submission

| Item | Current State | Action Needed |
|------|--------------|---------------|
| `android:usesCleartextTraffic="true"` | Set in `AndroidManifest.xml:33` | **Remove it.** The app makes no network calls. Google flags this. |
| `INTERNET` permission | Declared in `AndroidManifest.xml:6` | **Remove it.** PesaTrack is fully offline. Unnecessary permissions raise review flags. |
| `ACCESS_NETWORK_STATE` | Declared in `AndroidManifest.xml:7` | **Remove it.** Same reason. |
| `READ_CONTACTS` | Declared in `AndroidManifest.xml:18` | **Remove it** unless actively used. The Send Money / contact picker was removed with STK Push. Unused sensitive permissions = rejection risk. |
| ProGuard rules for Retrofit/OkHttp/Gson | In `proguard-rules.pro:17-29` | **Remove.** These libraries aren't in the dependencies. Dead rules are harmless but messy. |
| `versionCode` | `1` in `build.gradle.kts:17` | Fine for first release. Increment for every update. |
| `targetSdk` | `35` | Good — Play Store requires targetSdk ≥ 34 (as of 2025). |

---

## Phase 3: The Permissions Problem 🚨

This is the **hardest part** of getting PesaTrack approved. The app requests permissions that Google considers **highly sensitive**.

### PesaTrack's Permission Landscape

| Permission | Sensitivity | Why PesaTrack Needs It | Google's Stance |
|-----------|------------|----------------------|----------------|
| `RECEIVE_SMS` | 🔴 **Restricted** | Real-time M-PESA SMS interception via `SmsReceiver` | Requires **SMS Permission Declaration Form** |
| `READ_SMS` | 🔴 **Restricted** | Historical SMS import via `SmsImportService` | Requires **SMS Permission Declaration Form** |
| `READ_PHONE_STATE` | 🟡 **Sensitive** | SIM phone number detection via `PhoneNumberHelper` | Must justify in Data Safety |
| `READ_PHONE_NUMBERS` | 🟡 **Sensitive** | SIM phone number auto-fill | Must justify in Data Safety |
| `POST_NOTIFICATIONS` | 🟢 **Normal** (API 33+) | Expense & budget alerts | Runtime prompt sufficient |

### Google's SMS/Call Log Policy

Google Play has an [SMS/Call Log Permission Policy](https://support.google.com/googleplay/android-developer/answer/10208820) that **severely restricts** which apps can use `READ_SMS` and `RECEIVE_SMS`. Only apps whose **core function** requires these permissions are allowed.

#### Does PesaTrack Qualify?

| Allowed Category | Qualification |
|-----------------|--------------|
| **Default SMS handler** | ❌ No — PesaTrack doesn't replace the SMS app |
| **Financial transaction verification / expense tracking** | ✅ **Possibly** — PesaTrack reads financial SMS for passive expense tracking |

#### The Declaration Form Process

```
Upload AAB
    → Google detects SMS permissions
    → Permission Declaration Form required
    → Explain use case + provide video demo
    → Google Policy Team Review
        → Approved → App proceeds to content review
        → Denied → Must remove SMS permissions or appeal
            → Use alternative approach
```

**What you submit in the form:**
1. **Core functionality description:** "PesaTrack is a passive M-PESA expense tracker that reads incoming M-PESA and bank SMS messages to automatically log transaction details (amount, recipient, date). All data stays on-device. No data is transmitted to any server."
2. **Why default SMS handler isn't suitable:** "The app does not handle SMS sending/receiving. It only reads M-PESA messages (sender: MPESA) to extract expense data. The user's default SMS app is unaffected."
3. **Video demonstration** (~2 minutes showing):
   - App requesting SMS permission
   - Receiving an M-PESA SMS → expense automatically logged
   - Historical SMS import flow
   - Showing data stays local (no network calls, settings screen)

### What If Google Rejects the SMS Permissions?

This is a **real possibility**. Google has been increasingly strict. Fallback strategies, ranked:

#### Fallback A: Notification Listener Service (Best Alternative)

Instead of `READ_SMS` + `RECEIVE_SMS`, use `NotificationListenerService`:

```
M-PESA SMS arrives
    → Android shows notification
    → NotificationListenerService reads notification text
    → Parse expense from notification
    → Save to Room DB
```

**Pros:** Doesn't require SMS permissions at all. `BIND_NOTIFICATION_LISTENER_SERVICE` is user-granted in device Settings (not a Play Store restricted permission).

**Cons:**
- Requires user to manually enable in Settings → Notifications → Device & app notifications
- Notification text may be truncated (vs full SMS body)
- **Doesn't cover historical SMS import** — you lose the import feature entirely
- If user dismisses notification before the service reads it, data is lost

#### Fallback B: Manual SMS Forwarding / Copy-Paste

Remove all SMS permissions; have users paste or forward M-PESA messages manually. Worst UX but zero permission issues.

#### Recommendation

**Submit with SMS permissions first.** Financial SMS reading for expense tracking is a legitimate use case with precedent (apps like Walnut, Money Manager, mTrakr do this). But **build the Notification Listener fallback** before submission so you can pivot within days if rejected.

### READ_PHONE_STATE / READ_PHONE_NUMBERS

These are less problematic but still need justification. They're used in `PhoneNumberHelper.kt` for SIM auto-detection.

**Make this optional:** If the user denies phone permissions, let them type their number manually. This way the permission is "nice to have" rather than "blocks functionality," which is exactly how Google wants it.

---

## Phase 4: Google Play Console Setup

### Step 1: Developer Account

- **Cost:** One-time $25 USD fee
- **URL:** [play.google.com/console](https://play.google.com/console)
- **Identity verification:** Google requires ID verification for new developer accounts (takes 2–7 days)

### Step 2: Create the App

In the Play Console: **Create app** → App name: "PesaTrack", Default language: English, App type: App, Free/Paid: Free

### Step 3: Store Listing Assets

| Asset | Specification | Notes |
|-------|--------------|-------|
| App icon | 512 × 512 PNG | High-res version of launcher icon |
| Feature graphic | 1024 × 500 PNG | Banner at top of listing |
| Screenshots | Min 2, max 8 per device type | Phone (required), 7" tablet, 10" tablet |
| Short description | Max 80 chars | "Track M-PESA expenses automatically from SMS" |
| Full description | Max 4000 chars | Features, how it works, privacy emphasis |
| Privacy policy URL | **Required** | Must host a privacy policy page |
| App category | **Finance** | Primary category |

### Step 4: Data Safety Form (Mandatory)

| Question | PesaTrack's Answer |
|----------|--------------------|
| Does the app collect user data? | **Yes** |
| Data types collected | Financial info (transactions), SMS messages, Phone number |
| Is data shared with third parties? | **No** — all data is on-device |
| Is data encrypted in transit? | **N/A** — no data leaves the device |
| Is data encrypted at rest? | **No** (Room SQLite isn't encrypted — consider SQLCipher if needed) |
| Can users request data deletion? | **Yes** — clear app data or uninstall |
| Data collected is required or optional? | SMS: Required for core function; Phone: Optional (falls back to manual entry) |

### Step 5: Content Rating + Target Audience

- Answer the IARC questionnaire → should receive **Everyone** (E) rating
- **Not** targeted at children (under 13) — select **18+** since it handles financial data
- Declare the app is **not** an ads-supported app

### Step 6: Privacy Policy

Host a privacy policy covering:
- What data is collected (SMS content from MPESA/NCBA senders only, phone number, expense data)
- **All data stays entirely on-device** (no servers, no analytics, no third-party SDKs)
- No data sharing with any third party
- User can delete all data by clearing app data or uninstalling
- SMS parsing is limited to financial transaction senders

Hosting: GitHub Pages (free), a simple HTML page, or even a published Google Doc.

---

## Phase 5: Release Strategy

### Recommended Release Track Progression

```
Internal Testing (You + 5 testers)
    → Closed Testing (20-50 invited users)
    → Open Testing (Anyone can join)
    → Production (Full public launch)
```

| Track | Purpose | Min Testers | Review Time |
|-------|---------|-------------|-------------|
| **Internal testing** | Quick smoke test, no Google review | 1–100 (email list) | Instant |
| **Closed testing** | Real users, Google review begins | 20+ recommended | 1–3 days |
| **Open testing** | Public beta, full review | Open enrollment | 1–7 days |
| **Production** | Full launch | N/A | 1–7 days (first-time can take longer) |

> **Important for SMS permissions:** The Permission Declaration Form is typically triggered when you promote to **closed testing** or higher. Internal testing may not require it. Don't skip closed testing — it's where you'll encounter and resolve policy issues before going public.

---

## Summary: Action Items Checklist

| # | Task | Complexity | Blocking? |
|---|------|-----------|-----------|
| 1 | Remove unused permissions (`INTERNET`, `ACCESS_NETWORK_STATE`, `READ_CONTACTS`) and `usesCleartextTraffic` from manifest | 🟢 Easy | Yes |
| 2 | Clean up ProGuard rules (remove Retrofit/OkHttp/Gson entries) | 🟢 Easy | No |
| 3 | Generate release keystore (`keytool`) | 🟢 Easy | Yes |
| 4 | Configure `signingConfigs` in `build.gradle.kts` | 🟢 Easy | Yes |
| 5 | Build signed AAB + smoke test on device | 🟡 Medium | Yes |
| 6 | Create Google Play Developer account ($25) | 🟢 Easy | Yes |
| 7 | Write + host privacy policy | 🟡 Medium | Yes |
| 8 | Create store listing (icon, screenshots, descriptions) | 🟡 Medium | Yes |
| 9 | Complete Data Safety form | 🟡 Medium | Yes |
| 10 | Record video demo for SMS Permission Declaration Form | 🟡 Medium | **Critical** |
| 11 | Upload AAB to Internal Testing → verify installs | 🟢 Easy | No |
| 12 | Promote to Closed Testing + submit SMS permission form | 🟡 Medium | Yes |
| 13 | *(Contingency)* Build `NotificationListenerService` fallback | 🔴 Hard | Only if SMS permissions rejected |
| 14 | Promote to Production after closed test approval | 🟢 Easy | Final step |

The **single biggest risk** is item 12 — the SMS permission declaration review. Build the strongest case possible (video demo, clear privacy policy emphasizing 100% local data, well-written justification) and have the NotificationListener fallback ready so you can pivot quickly if Google says no.
