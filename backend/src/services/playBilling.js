// Google Play Billing verification service.
// See plans/ai-pro-phase1-spec.md §4.6

'use strict';

const crypto = require('node:crypto');
const fs = require('node:fs');

const config = require('../config');
const { log } = require('../middleware/logger');

// Valid Play product IDs — must match the SKUs configured in the Play Console.
const KNOWN_PRODUCT_IDS = new Set([
  'pesatrack_pro_monthly',
  'pesatrack_pro_annual',
]);

/**
 * SHA-256 hash of a purchase token, hex-encoded.
 * The full hash is stored as `purchaseTokenHash`; the first 16 chars appear
 * in logs and audit events as `token_hash`. The raw token is never persisted.
 */
function hashToken(rawToken) {
  return crypto.createHash('sha256').update(rawToken, 'utf8').digest('hex');
}

function isKnownProductId(pid) {
  return KNOWN_PRODUCT_IDS.has(pid);
}

// ---------------------------------------------------------------------------
// Google Play Developer API client (lazy-loaded — Phase 1 doesn't require it
// in dev because DEV_FAKE_BILLING short-circuits).
// ---------------------------------------------------------------------------

let cachedGoogleClient = null;

async function getGoogleClient() {
  if (cachedGoogleClient) return cachedGoogleClient;

  const { google } = require('googleapis');
  const keyPath = config.playBilling.serviceAccountJsonPath;

  if (!keyPath || !fs.existsSync(keyPath)) {
    const err = new Error('Play service-account JSON not available');
    err.code = 'billing_config_missing';
    err.status = 500;
    throw err;
  }

  const auth = new google.auth.GoogleAuth({
    keyFile: keyPath,
    scopes: ['https://www.googleapis.com/auth/androidpublisher'],
  });
  const publisher = google.androidpublisher({ version: 'v3', auth });
  cachedGoogleClient = publisher;
  return publisher;
}

/**
 * Verify a subscription purchase token against Google Play.
 * Returns a normalized entitlement snapshot.
 *
 * In DEV_FAKE_BILLING mode, returns a synthetic entitlement so Android dev
 * can exercise the pipeline without a real Play purchase.
 */
async function verifySubscription({ purchaseToken, productId }) {
  if (!KNOWN_PRODUCT_IDS.has(productId)) {
    const err = new Error('Unknown productId');
    err.code = 'unknown_product';
    err.status = 400;
    throw err;
  }

  if (config.devFakeBilling) {
    return fakeVerify({ purchaseToken, productId });
  }

  const publisher = await getGoogleClient();
  let response;
  try {
    // Using subscriptionsv2.get per Play Developer API v3.
    response = await publisher.purchases.subscriptionsv2.get({
      packageName: config.playBilling.packageName,
      token: purchaseToken,
    });
  } catch (e) {
    log.warn({
      msg: 'play_verify_failed',
      token_hash: hashToken(purchaseToken).slice(0, 16),
      status: e?.response?.status,
      error: e.message,
    });
    const err = new Error('Play verification failed');
    err.code = 'play_rejected';
    err.status = e?.response?.status === 404 ? 401 : 502;
    throw err;
  }

  const data = response.data || {};
  const lineItems = Array.isArray(data.lineItems) ? data.lineItems : [];
  const item = lineItems.find((li) => li.productId === productId) || lineItems[0];

  if (!item) {
    const err = new Error('No matching line item on subscription');
    err.code = 'no_line_item';
    err.status = 401;
    throw err;
  }

  const expiryMs = item.expiryTime ? Date.parse(item.expiryTime) : 0;
  const autoRenew = Boolean(data.autoRenewing ?? data.subscriptionState === 'SUBSCRIPTION_STATE_ACTIVE');
  const paymentState = mapSubscriptionState(data.subscriptionState);

  return {
    productId: item.productId,
    packageName: config.playBilling.packageName,
    expiryTimeMillis: expiryMs,
    autoRenewing: autoRenew,
    paymentState,
    linkedPurchaseToken: data.linkedPurchaseToken || null,
    // Raw response NOT returned — bounded projection only.
  };
}

function mapSubscriptionState(state) {
  // Per plans/ai-pro-phase1-spec.md §4.5:
  // 0=pending, 1=received, 2=free_trial, 3=pending_deferred
  switch (state) {
    case 'SUBSCRIPTION_STATE_PENDING':  return 0;
    case 'SUBSCRIPTION_STATE_ACTIVE':   return 1;
    case 'SUBSCRIPTION_STATE_IN_GRACE_PERIOD': return 1;
    case 'SUBSCRIPTION_STATE_ON_HOLD':  return 0;
    case 'SUBSCRIPTION_STATE_PAUSED':   return 0;
    case 'SUBSCRIPTION_STATE_CANCELED': return 1; // still entitled until expiry
    case 'SUBSCRIPTION_STATE_EXPIRED':  return 0;
    default:
      // Unknown — treat as received to avoid false denials; expiry is the source of truth.
      return 1;
  }
}

/**
 * Synthetic entitlement for local dev.
 * Only used when config.devFakeBilling === true (guarded against production
 * by config loader, see src/config/index.js).
 */
function fakeVerify({ purchaseToken, productId }) {
  const isAnnual = productId === 'pesatrack_pro_annual';
  const durationMs = isAnnual ? 365 * 24 * 60 * 60 * 1000 : 30 * 24 * 60 * 60 * 1000;
  log.warn({
    msg: 'fake_billing_verify',
    token_hash: hashToken(purchaseToken).slice(0, 16),
    productId,
  });
  return {
    productId,
    packageName: config.playBilling.packageName,
    expiryTimeMillis: Date.now() + durationMs,
    autoRenewing: true,
    paymentState: 1,
    linkedPurchaseToken: null,
  };
}

module.exports = {
  hashToken,
  isKnownProductId,
  verifySubscription,
  KNOWN_PRODUCT_IDS,
};
