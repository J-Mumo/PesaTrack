// Entitlement middleware — verifies Authorization: Bearer <purchaseToken>
// against the local Entitlement table (which the client established via
// POST /billing/verify). Populates req.entitlement on success.
//
// See plans/ai-pro-phase1-spec.md §4.6

'use strict';

const { getPrisma } = require('../services/prisma');
const { hashToken } = require('../services/playBilling');
const { log } = require('./logger');

const SIX_HOURS_MS = 6 * 60 * 60 * 1000;

async function requireEntitlement(req, res, next) {
  const auth = req.headers.authorization;
  if (typeof auth !== 'string' || !auth.startsWith('Bearer ')) {
    return res.status(401).json({ error: 'missing_bearer_token', request_id: req.id });
  }
  const rawToken = auth.slice(7);
  if (rawToken.length < 8) {
    return res.status(401).json({ error: 'invalid_bearer_token', request_id: req.id });
  }

  const tokenHash = hashToken(rawToken);
  const prisma = getPrisma();
  const row = await prisma.entitlement.findUnique({
    where: { purchaseTokenHash: tokenHash },
  });

  if (!row) {
    return res.status(401).json({ error: 'unknown_entitlement', request_id: req.id });
  }

  const now = Date.now();
  const expiryMs = Number(row.expiryTimeMillis);
  if (expiryMs <= now) {
    return res.status(402).json({ error: 'entitlement_expired', request_id: req.id });
  }

  // Attach for downstream handlers and rate limiter key.
  req.entitlement = {
    id: row.id,
    purchaseTokenHash: tokenHash,
    rawToken, // never logged; kept only for on-demand re-verify calls to Google.
    productId: row.productId,
    expiryTimeMillis: expiryMs,
    autoRenewing: row.autoRenewing,
    paymentState: row.paymentState,
  };

  // If we're within 6h of expiry, schedule a background revalidation (fire-and-forget).
  // Full impl lands in Phase 2; here we just record it in logs.
  if (expiryMs - now < SIX_HOURS_MS) {
    log.info({
      msg: 'entitlement_near_expiry',
      request_id: req.id,
      token_hash: tokenHash.slice(0, 16),
      expires_in_ms: expiryMs - now,
    });
  }

  next();
}

module.exports = { requireEntitlement };
