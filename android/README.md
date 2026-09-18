# PesaTrack Android App

Passive, local-first M-PESA and Kenyan bank expense tracking for Android.

## Tech Stack

- **Language**: Kotlin
- **UI**: Jetpack Compose
- **Architecture**: MVVM + Clean Architecture
- **DI**: Hilt
- **Database**: Room
- **Async**: Coroutines + Flow

## Project Structure

```
app/src/main/java/com/pesatrack/
├── PesaTrackApp.kt              # Application class
├── di/                           # Dependency injection
│   └── AppModule.kt
├── data/
│   ├── local/database/          # Room database
│   │   ├── dao/                 # Data access objects
│   │   ├── entities/            # Database entities
│   │   └── PesaTrackDatabase.kt
│   └── repository/              # Repository implementations
├── domain/models/               # Domain models
├── presentation/
│   ├── MainActivity.kt          # Main activity
│   ├── navigation/              # Navigation setup
│   ├── theme/                   # Material 3 theme
│   ├── components/              # Reusable UI components
│   └── screens/                 # App screens
│       ├── home/
│       ├── expenses/
│       └── categorize/
├── services/
│   └── SmsReceiver.kt          # SMS broadcast receiver
└── utils/
    ├── SmsParser.kt            # M-PESA SMS parsing
    └── Constants.kt
```

## Setup

### Prerequisites

- Android Studio Hedgehog (2023.1.1) or later
- JDK 17
- Android SDK 35

### Building the App

1. Open the `android` folder in Android Studio
2. Sync Gradle files
3. Run on a device or emulator

### Manual Debug Build

To manually generate a debug build for testing on a physical device:

1.  **Open the Terminal:** In Android Studio, go to `View` > `Tool Windows` > `Terminal`.
2.  **Run Gradle Task:** In the terminal, execute the appropriate command for your operating system:
    - **Windows:**
      ```cmd
      gradlew.bat app:assembleDebug
      ```
    - **macOS / Linux:**
      ```bash
      ./gradlew app:assembleDebug
      ```
3.  **Locate APK:** After the build finishes, you'll find the debug APK at: `app/build/outputs/apk/debug/app-debug.apk`


### Permissions

The app declares these permissions:

- **INTERNET / ACCESS_NETWORK_STATE**: For opt-in anonymous Firebase usage analytics only; financial records and raw SMS are not transmitted
- **READ_SMS / RECEIVE_SMS**: For parsing supported M-PESA and bank transactions on-device
- **POST_NOTIFICATIONS**: For expense categorization prompts

## Features

### 1. Passive SMS parsing

For transactions made outside PesaTrack:

1. App detects M-PESA confirmation SMS
2. Parses transaction details
3. Prompts user to categorize
4. Saves expense with category

### 2. Expense and income tracking

- View all expenses
- Monthly summary
- Category-based organization
- Edit categories for uncategorized expenses

### SMS Parsing Testing

Send yourself a test SMS matching M-PESA format:
```
ABC123XYZ Confirmed. Ksh1,000.00 sent to John Doe 0712345678 on 15/1/24 at 12:34 PM. New M-PESA balance is Ksh5,000.00.
```

## Financial-service scope

PesaTrack does not initiate or process payments, hold or transfer funds, provide loans or credit, connect users to lenders, trade securities or cryptocurrency, or manage investment portfolios. The backend in the repository is not consumed by the Android app.

Investment and savings entries are categories for the user's own records. Review screens may display a mathematical future-value illustration with its assumed rate and time horizon. The app does not recommend a specific security, broker, fund, or guaranteed return.
