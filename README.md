# PesaTrack

Passive, local-first M-PESA and Kenyan bank expense tracking for Android. PesaTrack reads supported transaction SMS messages on-device, extracts transaction details, and helps users categorize spending, set budgets, and understand their own financial activity.

## Features

- **SMS Parsing**: Detect and categorize supported M-PESA and bank transactions
- **Expense and Income Tracking**: Review locally stored records and period summaries
- **Budgets and Analytics**: Set category budgets and understand spending patterns
- **Local Imports and Exports**: Import SMS, M-PESA PDF statements, or Excel files and export CSV
- **Privacy Controls**: Local Room storage, optional PIN lock, and opt-in anonymous usage analytics

The shipped Android app does **not** initiate or process payments, hold or transfer funds, offer loans or credit, connect users to lenders, trade investments, manage investment portfolios, or call the repository's backend. Investment figures shown in reviews are mathematical illustrations with visible assumptions, not recommendations for any security, fund, broker, or provider.

## Project Structure

```
PesaTrack/
├── android/                 # Android app (Kotlin + Jetpack Compose)
├── backend/                 # Separate service; not consumed by the Android app
├── website/                 # Public Astro website
├── plans/                   # Architecture documentation
└── _docs/                   # Project documentation
```

## Quick Start

1. Open `android/` in Android Studio
2. Use JDK 17 and configure the Android SDK in `android/local.properties`
3. Build with the Gradle wrapper: `cd android; .\gradlew.bat assembleDebug`

## Architecture

### Android App
- **MVVM + Clean Architecture**
- **Jetpack Compose** for UI
- **Room** for local database
- **Hilt** for dependency injection
- **Coroutines + Flow** for asynchronous state

### Backend
- Separate Node.js service not referenced or called by the Play Store Android app
- Legacy Daraja/STK Push code is archived and not part of the shipped user flow

## User Flows

### Passive SMS tracking

```
User transacts outside PesaTrack → Supported transaction SMS arrives →
PesaTrack parses it on-device → User reviews or categorizes the record
```

### Local import or manual entry

```
User selects an SMS date range, M-PESA statement, or Excel file →
PesaTrack parses it on-device → User reviews imported records
```

## License

MIT
