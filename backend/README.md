# PesaTrack Backend

Node.js/Express backend for M-PESA Daraja API integration.

## Setup

### 1. Install Dependencies

```bash
cd backend
npm install
```

### 2. Configure Environment

Copy the example environment file and add your Daraja credentials:

```bash
cp .env.example .env
```

Edit `.env` with your credentials:

```env
MPESA_CONSUMER_KEY=your_consumer_key
MPESA_CONSUMER_SECRET=your_consumer_secret
MPESA_CALLBACK_URL=https://your-domain.com/api/callback/mpesa
```

### 3. Get Daraja Credentials

1. Go to [developer.safaricom.co.ke](https://developer.safaricom.co.ke)
2. Create an account and verify email
3. Create a new app with "Lipa Na M-PESA Online" API
4. Copy Consumer Key and Consumer Secret

### 4. Set Up Callback URL

For local development, use [ngrok](https://ngrok.com) to expose your local server:

```bash
# In one terminal, start the server
npm run dev

# In another terminal, start ngrok
ngrok http 3000
```

Copy the ngrok HTTPS URL and add `/api/callback/mpesa` to your `.env`:

```env
MPESA_CALLBACK_URL=https://abc123.ngrok.io/api/callback/mpesa
```

### 5. Run the Server

```bash
# Development mode (with auto-reload)
npm run dev

# Production mode
npm start
```

## API Endpoints

### Health Check
```
GET /health
```

### Initiate Payment
```
POST /api/payment/initiate
Content-Type: application/json

{
  "phoneNumber": "254712345678",
  "amount": 100,
  "paymentType": "PAY_BILL",
  "recipient": "123456",
  "categoryId": 1,
  "notes": "Lunch"
}
```

### Query Payment Status
```
GET /api/payment/status/:checkoutRequestId
```

### Listen for Payment Result (SSE)
```
GET /api/callback/listen/:checkoutRequestId
```

### M-PESA Callback (called by Safaricom)
```
POST /api/callback/mpesa
```

## Sandbox Testing

Use the following test credentials in sandbox mode:

- **Test Phone Number**: 254708374149
- **Shortcode**: 174379 (default sandbox)
- **Passkey**: Already configured in `.env.example`

## Production Deployment

For production:

1. Apply for production API access on Daraja portal
2. Update `.env` with production credentials
3. Set `MPESA_ENV=production`
4. Deploy to a hosting service (Railway, Render, Heroku, etc.)
5. Update `MPESA_CALLBACK_URL` with your production URL
