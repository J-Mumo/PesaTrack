// Per-purchase-token rate limits.
// See plans/ai-pro-phase1-spec.md §4.7

'use strict';

const rateLimit = require('express-rate-limit');
const { hashToken } = require('../services/playBilling');

// Key function: prefer entitlement (set by entitlement middleware); fall back
// to hashed Authorization header (verify endpoint runs BEFORE the entitlement
// middleware because that's what establishes the entitlement in the first place).
function keyByToken(req) {
  if (req.entitlement?.purchaseTokenHash) return req.entitlement.purchaseTokenHash;
  const auth = req.headers.authorization;
  if (typeof auth === 'string' && auth.startsWith('Bearer ')) {
    return hashToken(auth.slice(7));
  }
  return req.ip; // last resort
}

function makeLimiter({ windowMs, max, name }) {
  return rateLimit({
    windowMs,
    max,
    standardHeaders: true,
    legacyHeaders: false,
    keyGenerator: keyByToken,
    handler: (req, res) => {
      res.status(429).json({
        error: 'rate_limited',
        limit: name,
        request_id: req.id,
      });
    },
  });
}

const oneMinute = 60 * 1000;
const oneHour = 60 * 60 * 1000;
const oneDay = 24 * oneHour;

module.exports = {
  billingVerify: makeLimiter({ windowMs: oneHour, max: 20, name: 'billing.verify.hourly' }),
  billingEntitlement: makeLimiter({ windowMs: oneHour, max: 60, name: 'billing.entitlement.hourly' }),
  aiEcho: makeLimiter({ windowMs: oneDay, max: 100, name: 'ai.echo.daily' }),
  // /ai/coach-insight: 3/day headroom over the once-daily intent (pull-to-refresh + a rare recompute),
  // plus a 1/minute burst guard against double-tap flooding. See plans/ai-pro-phase2-spec.md §9.
  aiCoachInsightDaily: makeLimiter({ windowMs: oneDay, max: 3, name: 'ai.coach_insight.daily' }),
  aiCoachInsightMinute: makeLimiter({ windowMs: oneMinute, max: 1, name: 'ai.coach_insight.minute' }),
};
