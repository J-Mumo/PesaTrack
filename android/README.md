# PesaTrack Android App

Android expense tracking app with M-PESA integration.

## Tech Stack

- **Language**: Kotlin
- **UI**: Jetpack Compose
- **Architecture**: MVVM + Clean Architecture
- **DI**: Hilt
- **Database**: Room
- **Networking**: Retrofit

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
│   ├── remote/
│   │   ├── api/                 # Retrofit API
│   │   └── dto/                 # Data transfer objects
│   └── repository/              # Repository implementations
├── domain/models/               # Domain models
├── presentation/
│   ├── MainActivity.kt          # Main activity
│   ├── navigation/              # Navigation setup
│   ├── theme/                   # Material 3 theme
│   ├── components/              # Reusable UI components
│   └── screens/                 # App screens
│       ├── home/
│       ├── payment/
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
- Android SDK 34

### Building the App

1. Open the `android` folder in Android Studio
2. Sync Gradle files
3. Connect backend URL:
   - For emulator: Builder automatically uses `10.0.2.2:3000`
   - For physical device: Update `API_BASE_URL` in `app/build.gradle.kts`

4. Run on device or emulator

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

The app requests these permissions:

- **INTERNET**: For API communication
- **READ_SMS / RECEIVE_SMS**: For detecting external M-PESA transactions
- **POST_NOTIFICATIONS**: For expense categorization prompts

## Features

### 1. STK Push Payments

Initiate M-PESA payments with pre-selected categories:

1. Select payment type (Send Money, Buy Goods, Pay Bill)
2. Enter amount and recipient
3. Select expense category
4. Submit payment
5. Enter M-PESA PIN when prompted
6. Expense is saved automatically

### 2. SMS Parsing (Fallback)

For payments made directly in M-PESA:

1. App detects M-PESA confirmation SMS
2. Parses transaction details
3. Prompts user to categorize
4. Saves expense with category

### 3. Expense Tracking

- View all expenses
- Monthly summary
- Category-based organization
- Edit categories for uncategorized expenses

## Testing

### Sandbox Testing

1. Run backend with Daraja sandbox credentials
2. Use test phone number: 254708374149
3. STK Push will simulate payment flow

### SMS Parsing Testing

Send yourself a test SMS matching M-PESA format:
```
ABC123XYZ Confirmed. Ksh1,000.00 sent to John Doe 0712345678 on 15/1/24 at 12:34 PM. New M-PESA balance is Ksh5,000.00.
```

## Production Deployment

1. Update `API_BASE_URL` in release build config
2. Generate signed APK/AAB
3. Ensure backend is deployed with valid callback URL
4. Apply for Daraja production access
