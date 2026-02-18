I want to build an app on top of mpesa. The purpose of the app is to track expenses. When sending money or making a payment, I should be able to have an expense type.

app becomes the starting point of the payment, not the M-PESA menu.
User flow becomes:
    App → Choose Expense Type → Trigger M-PESA → Payment → Callback → Save Expense

Option 1 (BEST): STK Push–based Flow 🔥

User flow
User opens your app
Enters:
Amount
Recipient / Paybill / Till
Expense category (Food, Rent, Transport, etc.)
Optional notes
User taps Pay
Your backend triggers STK Push
User enters PIN on their phone
Safaricom sends a callback to your server
You save:
Transaction ID
Amount
Timestamp
Phone number
Expense category (from step 2)

Option 2: SMS Parsing (Fallback / Passive Tracking)

This is how many expense apps start.
How it works
User gives your app SMS read permission
App reads M-PESA confirmation SMS:
“You have sent KES 1,200 to Uber Kenya…”
You parse:
Amount
Recipient
Date
App prompts:
“Categorize this expense?”