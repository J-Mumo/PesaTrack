# Cloud Sync — Impact on Play Store Production Distribution

> **Context:** PesaTrack is initially launching as a fully offline app (all data on-device). Cloud sync (backup/restore across devices) is listed as a pending Phase 2 feature. This document covers what changes when cloud sync is added to an app already live on the Play Store.

---

## Key Areas Affected

### 1. Data Safety Form — Must Be Updated

The initial Data Safety declaration states all data stays on-device. Cloud sync changes this fundamentally.

| Field | Before Cloud Sync | After Cloud Sync |
|-------|-------------------|------------------|
| Data shared with third parties | **No** | Depends on backend — own server = still No; Firebase/Google Drive = technically Yes (Google is a third party) |
| Data encrypted in transit | **N/A** | Must be **Yes** (HTTPS/TLS required) |
| Data leaves device | **No** | **Yes** — financial transaction data is transmitted |
| Data stored on server | **No** | **Yes** — must disclose retention policy |

**Action:** Update the Data Safety form in the Play Console **before** (or alongside) the update that adds cloud sync. If the update goes live and the form doesn't match, Google can suspend the app.

---

### 2. Privacy Policy — Must Be Revised

The initial privacy policy says "all data stays on-device." Once cloud sync exists, it must cover:

- **What data is synced** (expense records, categories, budgets — but NOT raw SMS text)
- **Where it's stored** (Railway server, Firebase, Google Drive — be specific)
- **Encryption in transit and at rest**
- **Data retention** — how long server-side data is kept
- **Account deletion** — Google requires a way for users to request deletion of cloud data (mandatory since Dec 2023)
- **Data portability** — can users export their cloud data?

> If using Firebase or any Google service, also link to *their* privacy policy.

---

### 3. Permissions — INTERNET Is Back

`INTERNET` and `ACCESS_NETWORK_STATE` were removed from `AndroidManifest.xml` during pre-release cleanup. Cloud sync requires re-adding them:

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```

These are **normal** permissions (auto-granted, no runtime prompt). Low friction, but users who check permissions before updating will notice the addition in the store listing.

**Keep `usesCleartextTraffic` off.** Ensure the backend endpoint uses HTTPS only. Railway's `pesatrack-production.up.railway.app` already has HTTPS.

---

### 4. SMS Permissions — Unaffected

The SMS permission approval **carries forward**. Google only re-examines restricted permissions when you add *new* restricted ones (e.g., `ACCESS_FINE_LOCATION`). Cloud sync doesn't touch SMS permissions, so no re-declaration is needed.

---

### 5. Account Deletion Requirement

Since December 2023, Google Play requires that if your app supports account creation or cloud data storage, you must:

1. **Provide an in-app option** to delete the account and associated cloud data (e.g., a "Delete my cloud data" button in Settings)
2. **Provide a web-based option** (URL submitted in Play Console) for users to request deletion without the app installed

This means the cloud sync feature needs:
- A "Delete my cloud data" button in Settings
- A web page or API endpoint that accepts deletion requests

> **Exception:** If using Google Drive as the backup destination, the user owns and controls their data directly in their Drive. This may sidestep the requirement since there's no account on *your* server.

---

### 6. Google Play Review — What Changes

| Aspect | First Release (no sync) | Update with Cloud Sync |
|--------|------------------------|------------------------|
| Review scope | Full review + SMS permission declaration | **Incremental review** — faster, but Google re-checks Data Safety changes |
| SMS permissions | Reviewed and approved | **Unchanged** — no re-declaration needed |
| New policy concerns | None | **Account deletion**, data handling disclosure |
| Review time | 3–7 days | 1–3 days (updates are faster) |

---

### 7. Sync Backend Options & Policy Implications

| Option | Data Safety Impact | Policy Risk | Complexity |
|--------|-------------------|-------------|------------|
| **Own server (Railway)** | Low — full control over data | Low — no third parties | Medium |
| **Firebase Firestore/RTDB** | Medium — must disclose Google as data processor | Low — Google's own service | Low |
| **Google Drive API** | Low — data stays in user's own Google account | Very low — user controls their data | Medium |
| **E2E encrypted custom** | Very low — you can't read user data | Very low | High |

### Recommendation

**Google Drive backup** is the most privacy-friendly option for PesaTrack:
- Data lives in the user's own Google Drive
- You never store it on your server
- The Data Safety story remains strong ("your data is stored only in your own Google Drive account")
- Sidesteps the account deletion requirement since there's no account on *your* server
- Requires Google Sign-In integration

---

### 8. Version & Rollout Strategy

When shipping the cloud sync update:

- **Increment `versionCode`** in `build.gradle.kts` (e.g., from 1 to 2)
- **Update `versionName`** (e.g., from "1.0.0" to "1.1.0")
- Use **staged rollout** (e.g., 10% → 50% → 100%) to catch sync-related crashes before full deployment
- Cloud sync should be **opt-in** — don't auto-enable it. This avoids surprising existing users and gives a clean consent moment for data transmission

---

## Summary

| Area | Impact | When to Handle |
|------|--------|----------------|
| Data Safety form | **Must update** before or with the release | Before uploading the AAB |
| Privacy policy | **Must revise** to cover cloud data handling | Before uploading |
| Permissions | Add back `INTERNET` + `ACCESS_NETWORK_STATE` (low friction) | In the update code |
| Account deletion | **Required** if storing data on your server | Build alongside sync feature |
| SMS permissions | **No change** — already approved | Nothing to do |
| Store listing | Update description to mention sync/backup | Alongside release |
| Review time | 1–3 days for updates (faster than initial) | After upload |
| Rollout strategy | Use staged rollout (10% → 50% → 100%) | In Play Console release settings |

**Bottom line:** Cloud sync is a **medium-impact change** from a Play Store policy perspective. The biggest work items are updating the Data Safety form, revising the privacy policy, and (if using your own server) implementing the account deletion flow. None are blockers — they're mandatory checkboxes alongside the feature development.
