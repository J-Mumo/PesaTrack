// Play Billing routes: /billing/verify + /billing/entitlement
// See plans/ai-pro-phase1-spec.md §4.6

'use strict';

const express = require('express');
const { z } = require('zod');

const { getPrisma } = require('../services/prisma');
const {
  hashToken,
  verifySubscription,
  KNOWN_PRODUCT_IDS,
} = require('../services/playBilling');
const { requireEntitlement } = require('../middleware/entitlement');
const { billingVerify, billingEntitlement } = require('../middleware/rateLimit');
const { log } = require('../middleware/logger');
const config = require('../config');

const router = express.Router();

const verifyBody = z.object({
  purchaseToken: z.string().min(8).max(4096),
  productId: z.string().refine((v) => KNOWN_PRODUCT_IDS.has(v), 'unknown_product'),
});

function toClientEntitlement(row) {
  const now = Date.now();
  const expiryMs = Number(row.expiryTimeMillis);
  return {
    entitled: expiryMs > now,
    productId: row.productId,
    expiresAtEpochMs: expiryMs,
    autoRenewing: row.autoRenewing,
    // paymentState == 2 → free trial per Play docs.
    isTrialPeriod: row.paymentState === 2,
  };
}

// -------- POST /billing/verify --------
router.post('/verify', billingVerify, async (req, res, next) => {
  const parsed = verifyBody.safeParse(req.body);
  if (!parsed.success) {
    return res.status(400).json({
      error: 'bad_request',
      details: parsed.error.flatten().fieldErrors,
      request_id: req.id,
    });
  }
  const { purchaseToken, productId } = parsed.data;
  const tokenHash = hashToken(purchaseToken);

  try {
    const snap = await verifySubscription({ purchaseToken, productId });

    if (snap.packageName !== config.playBilling.packageName) {
      return res.status(401).json({ error: 'wrong_package', request_id: req.id });
    }
    if (!snap.expiryTimeMillis || snap.expiryTimeMillis <= Date.now()) {
      return res.status(401).json({ error: 'entitlement_expired', request_id: req.id });
    }

    const prisma = getPrisma();
    const linkedTokenHash = snap.linkedPurchaseToken
      ? hashToken(snap.linkedPurchaseToken)
      : null;

    const row = await prisma.entitlement.upsert({
      where: { purchaseTokenHash: tokenHash },
      create: {
        purchaseTokenHash: tokenHash,
        productId: snap.productId,
        packageName: snap.packageName,
        expiryTimeMillis: BigInt(snap.expiryTimeMillis),
        autoRenewing: snap.autoRenewing,
        paymentState: snap.paymentState,
        linkedPurchaseTokenHash: linkedTokenHash,
      },
      update: {
        productId: snap.productId,
        expiryTimeMillis: BigInt(snap.expiryTimeMillis),
        autoRenewing: snap.autoRenewing,
        paymentState: snap.paymentState,
        linkedPurchaseTokenHash: linkedTokenHash,
        lastVerifiedAt: new Date(),
      },
    });

    log.info({
      msg: 'billing_verified',
      request_id: req.id,
      token_hash: tokenHash.slice(0, 16),
      product_id: row.productId,
      expires_in_ms: Number(row.expiryTimeMillis) - Date.now(),
    });

    // Audit event — bounded metadata only.
    await prisma.auditEvent.create({
      data: {
        type: 'billing.verified',
        purchaseTokenHash: tokenHash,
        metadata: {
          product_id: row.productId,
          auto_renewing: row.autoRenewing,
          payment_state: row.paymentState,
        },
      },
    });

    res.json(toClientEntitlement(row));
  } catch (e) {
    if (typeof e.status === 'number') {
      return res.status(e.status).json({
        error: e.code || 'billing_verify_failed',
        request_id: req.id,
      });
    }
    next(e);
  }
});

// -------- GET /billing/entitlement --------
router.get('/entitlement', billingEntitlement, requireEntitlement, async (req, res, next) => {
  try {
    // The middleware already looked up the row and confirmed non-expired,
    // but we hit Prisma again to get canonical fields (avoid drift with the
    // trimmed req.entitlement projection).
    const prisma = getPrisma();
    const row = await prisma.entitlement.findUnique({
      where: { purchaseTokenHash: req.entitlement.purchaseTokenHash },
    });
    if (!row) {
      // Race: token vanished between middleware and here.
      return res.status(401).json({ error: 'unknown_entitlement', request_id: req.id });
    }
    res.json(toClientEntitlement(row));
  } catch (e) {
    next(e);
  }
});

module.exports = router;
