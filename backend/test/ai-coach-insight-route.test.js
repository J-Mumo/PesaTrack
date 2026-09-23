// Integration tests for POST /ai/coach-insight
//
// Exercises the whole handler chain through Express by mounting the app,
// injecting a mock AI provider, and calling the endpoint via a real HTTP
// request. We skip the rate-limit + entitlement middleware by calling the
// handler function directly through a lightweight harness — the middleware
// itself is already covered by Phase 1 tests (see plans §4.7).

'use strict';

// Env MUST be set before any `require('../src/...')` that transitively
// loads `../src/config` — config zod-validates process.env at module load
// and process.exit(1)s on failure. Both AiProvider.js and rateLimit.js /
// routes/ai.js pull in config, so a stray hoisted require would bring down
// the test process before we can override anything.
process.env.NODE_ENV = 'test';
process.env.DATABASE_URL = process.env.DATABASE_URL || 'postgres://ignored/ignored';
process.env.ENABLE_AI_ENDPOINTS = 'true';
process.env.DEV_FAKE_BILLING = 'true';

const { test, describe, before, after, beforeEach } = require('node:test');
const assert = require('node:assert/strict');
const express = require('express');

// Stub Prisma BEFORE the route module destructures `getPrisma` at load.
// The audit write is fire-and-forget, so the request path never sees this
// call fail; we still stub it to avoid a slow connection attempt against a
// nonexistent database that would keep the Node process alive after the
// tests finish.
const prismaModule = require('../src/services/prisma');
prismaModule.getPrisma = () => ({
  auditEvent: {
    create: async () => ({ id: 1 }),
  },
});

const { __setProviderForTest, __resetProvider } = require('../src/services/ai/AiProvider');
const {
  cacheClear,
  __setDenyListForTest,
  __resetDenyList,
} = require('../src/services/ai/coachInsight');
const aiRoutes = require('../src/routes/ai');
const { coachInsightHandler } = aiRoutes;

// ── Test app: skip auth + rate-limit, keep everything else ──────────────
//
// The Phase-2 spec is explicit that failure paths return HTTP 200 with
// `{ fallback: true, reason }` — not 5xx. Testing the *handler* directly
// through a real router but with entitlement + rate-limit stubbed out is
// enough to lock that contract without a full HTTP round trip.
//
// We rebuild a tiny express app that mounts only the handler under the
// same path, injecting a fake entitlement into req before dispatch.

function makeHarnessApp() {
  const app = express();
  app.use(express.json({ limit: '256kb' }));
  // Simulate what requestId + entitlement middleware would attach:
  app.use((req, _res, next) => {
    req.id = 'test-request-id';
    req.entitlement = {
      id: 1,
      purchaseTokenHash: 'a'.repeat(64),
      rawToken: 'raw-token',
      productId: 'pesatrack_pro_monthly',
      expiryTimeMillis: Date.now() + 30 * 24 * 60 * 60 * 1000,
      autoRenewing: true,
      paymentState: 'received',
    };
    next();
  });
  // Mount the handler directly, skipping the per-token rate limiters. The
  // rate limiters keep process-global state that would trip across tests
  // (1/minute burst guard) — they're separately tested through the real
  // routes wiring in a Phase-1-style smoke test against the live backend.
  app.post('/ai/coach-insight', coachInsightHandler);
  return app;
}

// ── Mock provider ───────────────────────────────────────────────────────
//
// Returns whatever `nextResponse` is set to. Records every call so tests
// can assert prompt content and call count.

function makeMockProvider(seedInsight) {
  const calls = [];
  let nextResponse = {
    json: seedInsight,
    inputTokens: 200,
    outputTokens: 100,
    latencyMs: 250,
    providerModelId: 'gpt-4.1-mini-fake',
  };
  return {
    name: () => 'mock',
    modelId: () => 'gpt-4.1-mini-fake',
    callStructured: async (input) => {
      calls.push(input);
      if (nextResponse instanceof Error) throw nextResponse;
      return nextResponse;
    },
    __setResponse: (r) => { nextResponse = r; },
    __calls: () => calls,
  };
}

function validDigest() {
  return {
    period: '2026-09',
    month_start_day: 1,
    days_elapsed: 15,
    days_total: 30,
    totals: {
      spent: 42800,
      spent_last_period: 38200,
      spent_3mo_avg: 39500,
      income_est: 85000,
      invested_this_period: 6000,
    },
    categories: [
      { id: 7, name: 'Food & Dining', spent: 12400, budget: 10000, '3mo_avg': 9800, cv: 0.18 },
    ],
    recurring: [],
    top_recipients_this_period: [
      { id: 'r1', spent: 8400, count: 12, category_id: 7, '3mo_avg': 3200 },
    ],
    anomalies_this_week: [],
  };
}

function goodInsight() {
  return {
    title: 'Food spending is up this week',
    body: 'You have spent KES 12,400 on Food & Dining so far this month, KES 2,600 above your typical pace at this point.',
    action_label: 'See Food breakdown',
    action_deeplink: 'pesatrack://category/7',
    saveable_amount_kes: 2600,
    assumptions: ['Assumes Food spending stays at current pace'],
    referenced_recipient_ids: ['r1'],
  };
}

async function postJson(app, path, body) {
  const server = app.listen(0);
  try {
    const port = server.address().port;
    const res = await fetch(`http://127.0.0.1:${port}${path}`, {
      method: 'POST',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify(body),
    });
    const json = await res.json();
    return { status: res.status, body: json };
  } finally {
    await new Promise((resolve) => server.close(resolve));
  }
}

// ── Tests ───────────────────────────────────────────────────────────────

describe('POST /ai/coach-insight', () => {
  let app;
  let mock;

  before(() => {
    __setDenyListForTest({
      substrings: ['guaranteed return'],
      brokers: ['Cytonn'],
      regexes: [],
    });
  });

  beforeEach(() => {
    cacheClear();
    mock = makeMockProvider(goodInsight());
    __setProviderForTest(mock);
    app = makeHarnessApp();
  });

  after(() => {
    __resetProvider();
    __resetDenyList();
  });

  test('happy path — insight returned, provider called once', async () => {
    const { status, body } = await postJson(app, '/ai/coach-insight', { digest: validDigest() });
    assert.equal(status, 200);
    assert.equal(body.fallback, false);
    assert.equal(body.cached, false);
    assert.equal(body.insight.title, 'Food spending is up this week');
    assert.deepEqual(body.insight.referenced_recipient_ids, ['r1']);
    assert.equal(mock.__calls().length, 1);
  });

  test('second call with the same digest returns cached insight (no provider call)', async () => {
    const digest = validDigest();
    const first = await postJson(app, '/ai/coach-insight', { digest });
    assert.equal(first.status, 200);
    assert.equal(first.body.cached, false);
    assert.equal(mock.__calls().length, 1);

    const second = await postJson(app, '/ai/coach-insight', { digest });
    assert.equal(second.status, 200);
    assert.equal(second.body.cached, true);
    assert.equal(second.body.fallback, false);
    // Provider not called again:
    assert.equal(mock.__calls().length, 1);
  });

  test('invalid digest shape returns 400 (client bug, not fallback)', async () => {
    const { status, body } = await postJson(app, '/ai/coach-insight', {
      digest: { period: '2026-09' }, // missing required fields
    });
    assert.equal(status, 400);
    assert.equal(body.error, 'invalid_digest');
  });

  test('provider error returns fallback: true with reason=provider_error', async () => {
    mock.__setResponse(new Error('upstream flaked'));
    const { status, body } = await postJson(app, '/ai/coach-insight', { digest: validDigest() });
    assert.equal(status, 200);
    assert.equal(body.fallback, true);
    assert.equal(body.reason, 'provider_error');
  });

  test('saveable without assumptions returns fallback: true, reason=saveable_no_assumptions', async () => {
    mock.__setResponse({
      json: {
        ...goodInsight(),
        saveable_amount_kes: 2600,
        assumptions: [], // <-- violates rule 1
      },
      inputTokens: 200, outputTokens: 100, latencyMs: 250, providerModelId: 'x',
    });
    const { status, body } = await postJson(app, '/ai/coach-insight', { digest: validDigest() });
    assert.equal(status, 200);
    assert.equal(body.fallback, true);
    assert.equal(body.reason, 'saveable_no_assumptions');
  });

  test('unknown recipient id returns fallback: true, reason=unknown_recipient_id', async () => {
    mock.__setResponse({
      json: { ...goodInsight(), referenced_recipient_ids: ['r99'] },
      inputTokens: 200, outputTokens: 100, latencyMs: 250, providerModelId: 'x',
    });
    const { status, body } = await postJson(app, '/ai/coach-insight', { digest: validDigest() });
    assert.equal(status, 200);
    assert.equal(body.fallback, true);
    assert.equal(body.reason, 'unknown_recipient_id');
  });

  test('deny-listed content returns fallback: true, reason=denylist', async () => {
    mock.__setResponse({
      json: {
        ...goodInsight(),
        body: 'Consider parking your savings for a guaranteed return of 8% annually — a solid move.',
      },
      inputTokens: 200, outputTokens: 100, latencyMs: 250, providerModelId: 'x',
    });
    const { status, body } = await postJson(app, '/ai/coach-insight', { digest: validDigest() });
    assert.equal(status, 200);
    assert.equal(body.fallback, true);
    assert.equal(body.reason, 'denylist');
  });

  test('stale category deeplink is soft-fixed, not a fallback', async () => {
    mock.__setResponse({
      json: {
        ...goodInsight(),
        action_deeplink: 'pesatrack://category/999', // 999 not in digest
        action_label: 'See something',
      },
      inputTokens: 200, outputTokens: 100, latencyMs: 250, providerModelId: 'x',
    });
    const { status, body } = await postJson(app, '/ai/coach-insight', { digest: validDigest() });
    assert.equal(status, 200);
    assert.equal(body.fallback, false);
    assert.equal(body.insight.action_deeplink, null);
    assert.equal(body.insight.action_label, null);
  });

  test('when ENABLE_AI_ENDPOINTS=false the endpoint returns 501', async () => {
    const config = require('../src/config');
    const previous = config.enableAiEndpoints;
    config.enableAiEndpoints = false;
    try {
      const { status, body } = await postJson(app, '/ai/coach-insight', { digest: validDigest() });
      assert.equal(status, 501);
      assert.equal(body.error, 'not_implemented');
    } finally {
      config.enableAiEndpoints = previous;
    }
  });
});
