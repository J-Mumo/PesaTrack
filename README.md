# PesaTrack

Passive M-PESA expense tracker for Android. Transactions are detected from M-PESA and supported bank SMS, categorized and stored locally. This repository also contains the public [PesaTrack website](https://pesatrack.jmumo.com/).

> **Repository transition:** This source repository is being prepared for private access. The public privacy policy is at https://pesatrack.jmumo.com/privacy and public support is at https://pesatrack.jmumo.com/support. See the [cutover checklist](_docs/private-repo-transition-checklist.md) before changing GitHub visibility.

## Features

- **SMS Parsing**: Automatically detect and categorize expenses from M-PESA confirmation SMS
- **Expense Tracking**: Budgets, insights, transaction fees and monthly summaries
- **Local Storage**: Transactions stay on-device; anonymous usage analytics are opt-in

## Project Structure

```
PesaTrack/
├── android/                 # Android app (Kotlin + Jetpack Compose)
├── backend/                 # Separate Node.js service; not used by the current main-branch app
├── website/                 # Astro site serving the public privacy policy
├── plans/                   # Architecture documentation
└── _docs/                   # Project documentation
```

## Maintainer setup

Open [android/](android/) in Android Studio. Use the Gradle wrapper for tests and lint. To build the public website, see [website/README.md](website/README.md). Never commit signing keys, local SDK configuration, or backend credentials.

## Architecture

### Android App
- **MVVM + Clean Architecture**
- **Jetpack Compose** for UI
- **Room** for local database
- **Hilt** for dependency injection
- **Coroutines and Flow** for local-first processing

### Backend
The [backend/](backend/) folder is separate from the Android app on `main`; do not introduce calls to it without explicit product and privacy review.

## User Flows

### SMS Parsing

```
User pays via M-PESA menu → SMS received → 
App parses SMS → Notification shown → 
User categorizes expense → Expense saved locally
```

## License

MIT

Making the repository private does not revoke rights already granted to recipients of publicly distributed copies under this license.
