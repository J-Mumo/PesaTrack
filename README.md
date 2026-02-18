# PesaTrack

M-PESA expense tracking app for Android. Track your expenses with automatic categorization when making payments through the app, or by detecting M-PESA SMS messages.

## Features

- **STK Push Payments**: Initiate M-PESA payments directly from the app with pre-selected expense categories
- **SMS Parsing**: Automatically detect and categorize expenses from M-PESA confirmation SMS
- **Expense Tracking**: View all expenses with monthly summaries
- **Category Management**: 8 default categories (Food, Transport, Shopping, Bills, Entertainment, Health, Rent, Other)
- **Local Storage**: All data stored locally on device

## Project Structure

```
PesaTrack/
├── android/                 # Android app (Kotlin + Jetpack Compose)
├── backend/                 # Node.js backend for M-PESA integration
├── plans/                   # Architecture documentation
└── _docs/                   # Project documentation
```

## Quick Start

### 1. Backend Setup

```bash
cd backend
npm install
cp .env.example .env
# Edit .env with your Daraja credentials
npm run dev
```

### 2. Expose Backend (for testing)

```bash
ngrok http 3000
# Update .env MPESA_CALLBACK_URL with ngrok URL
```

### 3. Android Setup

1. Open `android/` in Android Studio
2. Update `API_BASE_URL` if using physical device
3. Build and run

## Getting Daraja API Credentials

1. Go to [developer.safaricom.co.ke](https://developer.safaricom.co.ke)
2. Create an account
3. Create a new app with "Lipa Na M-PESA Online" API
4. Copy Consumer Key and Consumer Secret to `.env`

### Sandbox Testing

- Use phone number: **254708374149**
- Shortcode: **174379** (default sandbox)
- Passkey: Pre-configured in `.env.example`

## Architecture

### Android App
- **MVVM + Clean Architecture**
- **Jetpack Compose** for UI
- **Room** for local database
- **Hilt** for dependency injection
- **Retrofit** for networking

### Backend
- **Node.js + Express**
- **Daraja API** for M-PESA integration
- **SSE** for real-time payment updates

## User Flows

### Flow 1: App-initiated Payment (STK Push)

```
User opens app → Enters payment details → Selects category → 
Taps Pay → STK Push sent → User enters PIN → 
Callback received → Expense saved with category
```

### Flow 2: SMS Parsing (External Payments)

```
User pays via M-PESA menu → SMS received → 
App parses SMS → Notification shown → 
User categorizes expense → Expense saved
```

## License

MIT
