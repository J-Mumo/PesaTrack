/**
 * Daraja API Configuration
 *
 * This module exports M-PESA Daraja API configuration
 * based on environment (sandbox or production).
 *
 * Sandbox uses the default PayBill shortcode 174379.
 * Production uses:
 *   - Head Office shortcode 9955604 (BusinessShortCode + passkey)
 *   - Till Number 4338776 (PartyB — where money is sent)
 *
 * The environment is controlled by MPESA_ENV:
 *   - "sandbox"    → sandbox.safaricom.co.ke (default for local dev)
 *   - "production" → api.safaricom.co.ke     (set in Railway env vars)
 */

const config = {
  // Consumer credentials from Daraja portal
  consumerKey: process.env.MPESA_CONSUMER_KEY,
  consumerSecret: process.env.MPESA_CONSUMER_SECRET,
  
  // Head Office / Business shortcode (used for BusinessShortCode + password generation)
  // Sandbox: 174379 (PayBill test shortcode)
  // Production: your Head Office shortcode (e.g., 9955604)
  shortcode: process.env.MPESA_SHORTCODE || '174379',
  
  // Till Number / Store Number (used as PartyB for Buy Goods transactions)
  // Only needed for Buy Goods — this is where the money goes
  // For PayBill (sandbox), PartyB = shortcode, so this defaults to shortcode
  tillNumber: process.env.MPESA_TILL_NUMBER || null,
  
  // Lipa Na M-PESA passkey (tied to the Head Office shortcode)
  // Sandbox has a default, production requires your own from Safaricom
  passkey: process.env.MPESA_PASSKEY || 'bfb279f9aa9bdbcf158e97dd71a467cd2e0c893059b10f78e6b72ada1ed2c919',
  
  // Callback URL for M-PESA responses
  callbackUrl: process.env.MPESA_CALLBACK_URL,
  
  // Environment setting
  environment: process.env.MPESA_ENV || 'sandbox',
  
  // Base URLs
  get baseUrl() {
    return this.environment === 'production'
      ? 'https://api.safaricom.co.ke'
      : 'https://sandbox.safaricom.co.ke';
  },
  
  // Transaction type: depends on shortcode type
  // Sandbox default shortcode 174379 is PayBill → CustomerPayBillOnline
  // Production Till Number (Buy Goods)          → CustomerBuyGoodsOnline
  get transactionType() {
    return process.env.MPESA_TRANSACTION_TYPE ||
      (this.environment === 'production' ? 'CustomerBuyGoodsOnline' : 'CustomerPayBillOnline');
  },
  
  // PartyB: where the money is sent
  // For Buy Goods: Till Number (store number)
  // For PayBill: same as BusinessShortCode
  get partyB() {
    if (this.transactionType === 'CustomerBuyGoodsOnline' && this.tillNumber) {
      return this.tillNumber;
    }
    return this.shortcode;
  },
  
  // API Endpoints
  get endpoints() {
    return {
      oauth: `${this.baseUrl}/oauth/v1/generate?grant_type=client_credentials`,
      stkPush: `${this.baseUrl}/mpesa/stkpush/v1/processrequest`,
      stkQuery: `${this.baseUrl}/mpesa/stkpushquery/v1/query`
    };
  }
};

// Validate required configuration
const validateConfig = () => {
  const required = ['consumerKey', 'consumerSecret', 'callbackUrl'];
  const missing = required.filter(key => !config[key]);
  
  if (missing.length > 0) {
    console.warn(`⚠️  Missing M-PESA configuration: ${missing.join(', ')}`);
    console.warn('   Please set these in your .env file');
  }

  // Production-specific warnings
  if (config.environment === 'production') {
    if (config.shortcode === '174379') {
      console.error('🚨 PRODUCTION mode but using sandbox shortcode 174379!');
      console.error('   Set MPESA_SHORTCODE to your production Head Office shortcode.');
    }
    if (config.passkey === 'bfb279f9aa9bdbcf158e97dd71a467cd2e0c893059b10f78e6b72ada1ed2c919') {
      console.error('🚨 PRODUCTION mode but using sandbox passkey!');
      console.error('   Set MPESA_PASSKEY to your production passkey.');
    }
    if (config.transactionType === 'CustomerBuyGoodsOnline' && !config.tillNumber) {
      console.error('🚨 Buy Goods mode but MPESA_TILL_NUMBER is not set!');
      console.error('   Set MPESA_TILL_NUMBER to your Till/Store number.');
    }
  }
};

validateConfig();

module.exports = config;
