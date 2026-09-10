// AI routes — Phase 1: only /ai/echo is exposed.
// See plans/ai-pro-phase1-spec.md §4.6

'use strict';

const express = require('express');
const { z } = require('zod');

const { requireEntitlement } = require('../middleware/entitlement');
const { aiEcho } = require('../middleware/rateLimit');
const { getPrisma } = require('../services/prisma');
const { log } = require('../middleware/logger');
const config = require('../config');

const router = express.Router();

// Digest v1 — validated shape-only (fields, not values). We deliberately
// don't lock the schema strictly here; that's Phase 2's job. The point of
// /ai/echo is to prove:
//   1. entitlement middleware works end-to-end
//   2. JSON body arrives intact
//   3. we can respond without ever leaking digest content to logs
const echoBody = z.object({
  digest: z.object({
    period: z.string().min(4).max(20),
  }).passthrough(),
});

router.post('/echo', aiEcho, requireEntitlement, async (req, res) => {
  if (!config.enableAiEcho) {
    return res.status(404).json({ error: 'not_found', request_id: req.id });
  }

  const parsed = echoBody.safeParse(req.body);
  if (!parsed.success) {
    return res.status(400).json({
      error: 'bad_request',
      details: parsed.error.flatten().fieldErrors,
      request_id: req.id,
    });
  }

  const digest = parsed.data.digest;
  const fieldCount = Object.keys(digest).length;

  log.info({
    msg: 'ai_echo_called',
    request_id: req.id,
    token_hash: req.entitlement.purchaseTokenHash.slice(0, 16),
    digest_field_count: fieldCount,
    digest_period: digest.period,
    // NOTE: digest body itself is NEVER logged.
  });

  // Fire-and-forget audit event (bounded metadata only).
  try {
    const prisma = getPrisma();
    await prisma.auditEvent.create({
      data: {
        type: 'ai.echo_called',
        purchaseTokenHash: req.entitlement.purchaseTokenHash,
        metadata: {
          digest_field_count: fieldCount,
          digest_period: digest.period,
          endpoint: '/ai/echo',
        },
      },
    });
  } catch (e) {
    // Audit failure never fails the request.
    log.warn({ msg: 'audit_write_failed', request_id: req.id, error: e.message });
  }

  res.json({
    received_at: new Date().toISOString(),
    digest_field_count: fieldCount,
    period: digest.period,
    provider: 'none', // Phase 1 doesn't touch the LLM.
    request_id: req.id,
  });
});

// Phase 2+ endpoints are gated behind ENABLE_AI_ENDPOINTS and are not defined
// yet. Explicit 501 so a misconfigured client learns immediately.
if (!config.enableAiEndpoints) {
  router.use((req, res) => {
    res.status(501).json({
      error: 'not_implemented',
      hint: 'AI endpoints ship in Phase 2 (Coach Insights).',
      request_id: req.id,
    });
  });
}

module.exports = router;
