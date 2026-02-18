/**
 * Daraja API Service
 *
 * Handles all M-PESA Daraja API interactions including:
 * - OAuth token generation
 * - STK Push initiation
 * - Transaction query
 *
 * Includes rate limiting to comply with Daraja API limits:
 * - 5 queries per 60 seconds (spike arrest)
 */

const axios = require('axios');
const config = require('../config/daraja');

// Rate limiting constants - Daraja allows 5 requests per 60 seconds
const RATE_LIMIT_MAX_TOKENS = 5;
const RATE_LIMIT_REFILL_RATE = 60000; // 60 seconds in ms
const RATE_LIMIT_REFILL_AMOUNT = 5;

// Cache TTL for STK query results (12 seconds)
const STK_QUERY_CACHE_TTL = 12000;

class DarajaService {
  constructor() {
    this.accessToken = null;
    this.tokenExpiry = null;
    
    // Rate limiting using Token Bucket algorithm
    this.rateLimitTokens = RATE_LIMIT_MAX_TOKENS;
    this.lastRefillTime = Date.now();
    
    // Cache for STK query results to avoid redundant API calls
    this.stkQueryCache = new Map();
  }

  /**
   * Check and consume a rate limit token
   * Returns { allowed: boolean, retryAfter: number (seconds) }
   */
  checkRateLimit() {
    const now = Date.now();
    const timeSinceRefill = now - this.lastRefillTime;
    
    // Refill tokens based on time elapsed
    if (timeSinceRefill >= RATE_LIMIT_REFILL_RATE) {
      this.rateLimitTokens = RATE_LIMIT_MAX_TOKENS;
      this.lastRefillTime = now;
    }
    
    if (this.rateLimitTokens > 0) {
      this.rateLimitTokens--;
      return { allowed: true, retryAfter: 0 };
    }
    
    // Calculate time until next refill
    const retryAfter = Math.ceil((RATE_LIMIT_REFILL_RATE - timeSinceRefill) / 1000);
    return { allowed: false, retryAfter };
  }

  /**
   * Get cached STK query result if still valid
   */
  getCachedSTKQuery(checkoutRequestId) {
    const cached = this.stkQueryCache.get(checkoutRequestId);
    if (cached && Date.now() - cached.timestamp < STK_QUERY_CACHE_TTL) {
      console.log(`📋 Cache hit for ${checkoutRequestId}`);
      return cached.data;
    }
    return null;
  }

  /**
   * Cache STK query result
   */
  setCachedSTKQuery(checkoutRequestId, data) {
    this.stkQueryCache.set(checkoutRequestId, {
      data,
      timestamp: Date.now()
    });
    
    // Clean up old cache entries periodically
    if (this.stkQueryCache.size > 100) {
      const now = Date.now();
      for (const [key, value] of this.stkQueryCache) {
        if (now - value.timestamp > STK_QUERY_CACHE_TTL * 2) {
          this.stkQueryCache.delete(key);
        }
      }
    }
  }

  /**
   * Generate OAuth access token
   * Tokens are valid for 1 hour, we cache them
   */
  async getAccessToken() {
    // Return cached token if still valid
    if (this.accessToken && this.tokenExpiry && Date.now() < this.tokenExpiry) {
      return this.accessToken;
    }

    const auth = Buffer.from(
      `${config.consumerKey}:${config.consumerSecret}`
    ).toString('base64');

    try {
      const response = await axios.get(config.endpoints.oauth, {
        headers: {
          Authorization: `Basic ${auth}`
        }
      });

      this.accessToken = response.data.access_token;
      // Set expiry to 50 minutes (token lasts 60 minutes)
      this.tokenExpiry = Date.now() + (50 * 60 * 1000);
      
      return this.accessToken;
    } catch (error) {
      console.error('OAuth Error:', error.response?.data || error.message);
      throw new Error('Failed to generate M-PESA access token');
    }
  }

  /**
   * Generate password for STK Push
   * Format: Base64(Shortcode + Passkey + Timestamp)
   */
  generatePassword(timestamp) {
    const data = `${config.shortcode}${config.passkey}${timestamp}`;
    return Buffer.from(data).toString('base64');
  }

  /**
   * Generate timestamp in M-PESA format
   * Format: YYYYMMDDHHmmss
   */
  generateTimestamp() {
    const now = new Date();
    return now.getFullYear().toString() +
      String(now.getMonth() + 1).padStart(2, '0') +
      String(now.getDate()).padStart(2, '0') +
      String(now.getHours()).padStart(2, '0') +
      String(now.getMinutes()).padStart(2, '0') +
      String(now.getSeconds()).padStart(2, '0');
  }

  /**
   * Initiate STK Push (Lipa Na M-PESA Online)
   * 
   * @param {Object} params - Payment parameters
   * @param {string} params.phoneNumber - Customer phone number (254...)
   * @param {number} params.amount - Amount to charge
   * @param {string} params.accountReference - Account reference (max 12 chars)
   * @param {string} params.transactionDesc - Transaction description (max 13 chars)
   * @returns {Object} STK Push response
   */
  async initiateSTKPush({ phoneNumber, amount, accountReference, transactionDesc }) {
    const token = await this.getAccessToken();
    const timestamp = this.generateTimestamp();
    const password = this.generatePassword(timestamp);

    // Format phone number (ensure it starts with 254)
    const formattedPhone = this.formatPhoneNumber(phoneNumber);

    const payload = {
      BusinessShortCode: config.shortcode,
      Password: password,
      Timestamp: timestamp,
      TransactionType: 'CustomerPayBillOnline',
      Amount: Math.round(amount), // M-PESA requires integer
      PartyA: formattedPhone,
      PartyB: config.shortcode,
      PhoneNumber: formattedPhone,
      CallBackURL: config.callbackUrl,
      AccountReference: accountReference.substring(0, 12),
      TransactionDesc: transactionDesc.substring(0, 13)
    };

    try {
      const response = await axios.post(config.endpoints.stkPush, payload, {
        headers: {
          Authorization: `Bearer ${token}`,
          'Content-Type': 'application/json'
        }
      });

      return {
        success: response.data.ResponseCode === '0',
        checkoutRequestId: response.data.CheckoutRequestID,
        merchantRequestId: response.data.MerchantRequestID,
        responseCode: response.data.ResponseCode,
        responseDescription: response.data.ResponseDescription,
        customerMessage: response.data.CustomerMessage
      };
    } catch (error) {
      console.error('STK Push Error:', error.response?.data || error.message);
      throw new Error(
        error.response?.data?.errorMessage || 
        'Failed to initiate M-PESA payment'
      );
    }
  }

  /**
   * Query STK Push transaction status
   * Includes caching and rate limiting to comply with Daraja API limits
   *
   * @param {string} checkoutRequestId - Checkout request ID from STK Push
   * @param {Object} options - Query options
   * @param {boolean} options.skipRateLimit - Skip rate limit check (for internal use)
   * @returns {Object} Transaction status with rateLimited flag if applicable
   */
  async querySTKStatus(checkoutRequestId, options = {}) {
    // Check cache first (no rate limit consumed)
    const cached = this.getCachedSTKQuery(checkoutRequestId);
    if (cached) {
      return { ...cached, fromCache: true };
    }

    // Check rate limit before making API call
    const rateLimit = this.checkRateLimit();
    if (!rateLimit.allowed && !options.skipRateLimit) {
      console.log(`⚠️ Rate limited for STK query. Retry after ${rateLimit.retryAfter}s`);
      return {
        rateLimited: true,
        retryAfter: rateLimit.retryAfter,
        success: false,
        resultCode: 'RATE_LIMITED',
        resultDesc: `Rate limited. Retry after ${rateLimit.retryAfter} seconds`
      };
    }

    const token = await this.getAccessToken();
    const timestamp = this.generateTimestamp();
    const password = this.generatePassword(timestamp);

    const payload = {
      BusinessShortCode: config.shortcode,
      Password: password,
      Timestamp: timestamp,
      CheckoutRequestID: checkoutRequestId
    };

    try {
      const response = await axios.post(config.endpoints.stkQuery, payload, {
        headers: {
          Authorization: `Bearer ${token}`,
          'Content-Type': 'application/json'
        }
      });

      const result = {
        success: response.data.ResultCode === '0',
        resultCode: response.data.ResultCode,
        resultDesc: response.data.ResultDesc,
        merchantRequestId: response.data.MerchantRequestID,
        checkoutRequestId: response.data.CheckoutRequestID
      };

      // Cache the result
      this.setCachedSTKQuery(checkoutRequestId, result);
      console.log(`✅ STK Query successful for ${checkoutRequestId}`);

      return result;
    } catch (error) {
      // Handle Daraja rate limit error specifically
      if (error.response?.data?.fault?.detail?.errorcode === 'policies.ratelimit.SpikeArrestViolation') {
        console.error('🚫 Daraja API rate limit hit:', error.response.data.fault.faultstring);
        return {
          rateLimited: true,
          retryAfter: 15, // Conservative retry time
          success: false,
          resultCode: 'RATE_LIMITED',
          resultDesc: 'Daraja API rate limit exceeded. Please try again later.'
        };
      }
      
      console.error('STK Query Error:', error.response?.data || error.message);
      throw new Error('Failed to query transaction status');
    }
  }

  /**
   * Format phone number to M-PESA format (254...)
   */
  formatPhoneNumber(phone) {
    // Remove any spaces, dashes, or plus signs
    let cleaned = phone.replace(/[\s\-\+]/g, '');
    
    // Handle different formats
    if (cleaned.startsWith('0')) {
      // 0712345678 -> 254712345678
      cleaned = '254' + cleaned.substring(1);
    } else if (cleaned.startsWith('7') || cleaned.startsWith('1')) {
      // 712345678 -> 254712345678
      cleaned = '254' + cleaned;
    }
    // If already starts with 254, keep as is
    
    return cleaned;
  }
}

module.exports = new DarajaService();
