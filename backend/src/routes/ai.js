// AI routes — Phase 2 adds /ai/coach-insight; /ai/echo stays for the
// pipeline-proof smoke tests.
// See plans/ai-pro-phase2-spec.md §7 for /ai/coach-insight.
// See plans/ai-pro-phase1-spec.md §4.6 for /ai/echo.

'use strict';

const express = require('express');
const { z } = require('zod');

const { requireEntitlement } = require('../middleware/entitlement');
const { aiEcho, aiCoachInsightDaily, aiCoachInsightMinute } = require('../middleware/rateLimit');
const { getPrisma } = require('../services/prisma');
const { log } = require('../middleware/logger');
const config = require('../config');
const {
  coachInsightV1Schema,
  DigestSchema,
  SYSTEM_PROMPT,
  buildUserPrompt,
  denyListMatch,
  postValidate,
  sha256Canonical,
  cacheGet,
  cacheSet,
} = require('../services/ai/coachInsight');
const { getDefaultProvider } = require('../services/ai/AiProvider');

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

// ─────────────────────────────────────────────────────────────────────────
// POST /ai/coach-insight  (Phase 2)
// ─────────────────────────────────────────────────────────────────────────
//
// Flow: entitlement → per-token rate limit (1/min + 3/day) → validate
// digest → cache lookup by canonical hash → OpenAI Structured Outputs →
// postValidate → deny-list scrub → cache put → JSON response.
//
// Failure discipline: the endpoint NEVER returns a 5xx to the client for
// LLM/provider issues — every failure surface returns
//   { fallback: true, reason: '<bucket>' }
// with an HTTP 200. That way the Android client can silently render its
// template card without needing to distinguish "network flake" from
// "denied by guardrails". Genuine client bugs (invalid digest shape,
// missing bearer) still return 4xx.

const coachInsightBody = z.object({
  digest: DigestSchema,
});

async function coachInsightHandler(req, res) {
  if (!config.enableAiEndpoints) {
    return res.status(501).json({
      error: 'not_implemented',
      hint: 'Set ENABLE_AI_ENDPOINTS=true to enable Coach Insights.',
      request_id: req.id,
    });
  }

  const parsed = coachInsightBody.safeParse(req.body);
  if (!parsed.success) {
    const flattened = parsed.error.flatten();
    log.warn({
      msg: 'coach_insight.invalid_digest',
      request_id: req.id,
      token_hash: req.entitlement?.purchaseTokenHash?.slice(0, 16),
      field_errors: flattened.fieldErrors,
      form_errors: flattened.formErrors,
      // Log the top-level keys of the body so we can spot missing/extra
      // fields without ever logging user amounts. Category names are
      // app taxonomy (non-PII) but we still keep it to keys only.
      body_keys: req.body && typeof req.body === 'object' ? Object.keys(req.body) : null,
      digest_keys: req.body?.digest && typeof req.body.digest === 'object'
        ? Object.keys(req.body.digest)
        : null,
    });
    return res.status(400).json({
      error: 'invalid_digest',
      details: flattened.fieldErrors,
      request_id: req.id,
    });
  }
  const digest = parsed.data.digest;

  // Cache hit?
  const hash = sha256Canonical(digest);
  const cached = cacheGet(hash);
  if (cached) {
    log.info({
      msg: 'coach_insight.cache_hit',
      request_id: req.id,
      token_hash: req.entitlement.purchaseTokenHash.slice(0, 16),
      digest_hash: hash.slice(0, 16),
    });
    return res.json({ fallback: false, insight: cached, cached: true, request_id: req.id });
  }

  // Call provider.
  const provider = getDefaultProvider();
  const userPrompt = buildUserPrompt(digest);
  let result;
  try {
    result = await provider.callStructured({
      systemPrompt: SYSTEM_PROMPT,
      userPrompt,
      schema: coachInsightV1Schema,
      schemaName: 'coach_insight_v1',
      temperature: config.openai.coachTemperature,
      maxTokens: config.openai.maxTokensOut,
    });
  } catch (e) {
    log.warn({
      msg: 'coach_insight.provider_error',
      request_id: req.id,
      code: e.code || 'unknown',
      status: e.status || 0,
    });
    await auditCoachInsight(req, 'coach_insight.provider_error', {
      reason: e.code || 'provider_error',
      digest_hash: hash.slice(0, 16),
    });
    return res.json({ fallback: true, reason: 'provider_error', request_id: req.id });
  }

  const insight = result.json;

  // Post-validate. May mutate `insight` for soft-fixes (stripping stale
  // action deep-links).
  const issue = postValidate(insight, digest);
  if (issue) {
    log.info({
      msg: 'coach_insight.rejected',
      request_id: req.id,
      reason: issue,
    });
    await auditCoachInsight(req, 'coach_insight.rejected', {
      reason: issue,
      digest_hash: hash.slice(0, 16),
    });
    return res.json({ fallback: true, reason: issue, request_id: req.id });
  }

  // Deny-list scrub applied to the whole flattened text (title + body +
  // assumptions joined). Applied AFTER postValidate so the reason bucket
  // reads honestly if both would have fired.
  const flat = [
    insight.title || '',
    insight.body || '',
    ...(insight.assumptions || []),
  ].join('\n');
  const denyHit = denyListMatch(flat);
  if (denyHit) {
    log.info({
      msg: 'coach_insight.denylist_hit',
      request_id: req.id,
      // The matched term is safe to log — it's from our own deny-list, not user content.
      term: denyHit.slice(0, 40),
    });
    await auditCoachInsight(req, 'coach_insight.denylist_hit', {
      digest_hash: hash.slice(0, 16),
    });
    return res.json({ fallback: true, reason: 'denylist', request_id: req.id });
  }

  cacheSet(hash, insight);

  log.info({
    msg: 'coach_insight.ok',
    request_id: req.id,
    token_hash: req.entitlement.purchaseTokenHash.slice(0, 16),
    digest_hash: hash.slice(0, 16),
    input_tokens: result.inputTokens,
    output_tokens: result.outputTokens,
    latency_ms: result.latencyMs,
    provider: provider.name(),
    model: result.providerModelId,
  });
  await auditCoachInsight(req, 'coach_insight.ok', {
    digest_hash: hash.slice(0, 16),
    input_tokens: result.inputTokens,
    output_tokens: result.outputTokens,
    latency_ms: result.latencyMs,
    model: result.providerModelId,
  });

  return res.json({ fallback: false, insight, cached: false, request_id: req.id });
}

/**
 * Fire-and-forget audit write. Never fails the request.
 * Metadata is bounded (numbers + short strings + prefixed hashes) — no
 * digest body, no insight body.
 */
async function auditCoachInsight(req, type, metadata) {
  try {
    const prisma = getPrisma();
    await prisma.auditEvent.create({
      data: {
        type,
        purchaseTokenHash: req.entitlement.purchaseTokenHash,
        metadata: { ...metadata, endpoint: '/ai/coach-insight' },
      },
    });
  } catch (e) {
    log.warn({ msg: 'audit_write_failed', request_id: req.id, error: e.message });
  }
}

router.post(
  '/coach-insight',
  aiCoachInsightMinute,
  aiCoachInsightDaily,
  requireEntitlement,
  coachInsightHandler,
);

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
// Also expose the handler so tests can mount it directly, bypassing the
// per-token rate limiters (which share process-global state across tests
// and would false-429 the second request in each run).
module.exports.coachInsightHandler = coachInsightHandler;
