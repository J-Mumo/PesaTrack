/**
 * Daraja API Configuration
 * 
 * This module exports M-PESA Daraja API configuration
 * based on environment (sandbox or production)
 */

const config = {
  // Consumer credentials from Daraja portal
  consumerKey: process.env.MPESA_CONSUMER_KEY,
  consumerSecret: process.env.MPESA_CONSUMER_SECRET,
  
  // Business shortcode
  shortcode: process.env.MPESA_SHORTCODE || '174379',
  
  // Lipa Na M-PESA passkey
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
};

validateConfig();

module.exports = config;
