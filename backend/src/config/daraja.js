/**
 * Daraja API Configuration
 *
 * This module exports M-PESA Daraja API configuration
 * based on environment (sandbox or production).
 *
 * Sandbox uses the default PayBill shortcode 174379.
 * Production uses Till Number (Buy Goods) shortcode 9955604.
 *
 * The environment is controlled by MPESA_ENV:
 *   - "sandbox"    → sandbox.safaricom.co.ke (default for local dev)
 *   - "production" → api.safaricom.co.ke     (set in Railway env vars)
 */

const config = {
  // Consumer credentials from Daraja portal
  consumerKey: process.env.MPESA_CONSUMER_KEY,
  consumerSecret: process.env.MPESA_CONSUMER_SECRET,
  
  // Business shortcode (sandbox: 174379, production: your Till/PayBill number)
  shortcode: process.env.MPESA_SHORTCODE || '174379',
  
  // Lipa Na M-PESA passkey (sandbox has a default, production requires your own)
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
      console.error('   Set MPESA_SHORTCODE to your production shortcode.');
    }
    if (config.passkey === 'bfb279f9aa9bdbcf158e97dd71a467cd2e0c893059b10f78e6b72ada1ed2c919') {
      console.error('🚨 PRODUCTION mode but using sandbox passkey!');
      console.error('   Set MPESA_PASSKEY to your production passkey.');
    }
  }
};

validateConfig();

module.exports = config;
