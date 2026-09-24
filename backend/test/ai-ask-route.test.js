// Integration tests for POST /ai/ask
//
// Exercises the SSE handler through Express by mounting the app, injecting
// a mock AI provider that yields canned deltas, and calling the endpoint
// via a real HTTP request. Same harness pattern as
// ai-coach-insight-route.test.js: entitlement + rate limit stubbed so we
// isolate the streaming + postValidate contract.

'use strict';

process.env.NODE_ENV = 'test';
process.env.DATABASE_URL = process.env.DATABASE_URL || 'postgres://ignored/ignored';
process.env.ENABLE_AI_ENDPOINTS = 'true';
process.env.DEV_FAKE_BILLING = 'true';

const { test, describe, before, after, beforeEach } = require('node:test');
const assert = require('node:assert/strict');
const express = require('express');
const http = require('node:http');

// Stub Prisma before the route module loads.
const prismaModule = require('../src/services/prisma');
prismaModule.getPrisma = () => ({
  auditEvent: { create: async () => ({ id: 1 }) },
});

const { __setProviderForTest, __resetProvider } = require('../src/services/ai/AiProvider');
const {
  __setDenyListForTest,
  __resetDenyList,
} = require('../src/services/ai/coachInsight');
const aiRoutes = require('../src/routes/ai');
const { askHandler } = aiRoutes;

// ── Harness ─────────────────────────────────────────────────────────────

function makeHarnessApp() {
  const app = express();
  app.use(express.json({ limit: '256kb' }));
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
  app.post('/ai/ask', askHandler);
  return app;
}

/**
 * Starts the harness app on an ephemeral port, returns { url, close }.
 * We use a real HTTP server (not supertest) so the SSE stream reads
 * exactly like production — chunked transfer, keep-alive, no framework
 * buffering surprises.
 */
function startServer() {
  const app = makeHarnessApp();
  return new Promise((resolve) => {
    const server = http.createServer(app);
    server.listen(0, () => {
      const port = server.address().port;
      resolve({
        url: `http://127.0.0.1:${port}`,
        close: () => new Promise((r) => server.close(r)),
      });
    });
  });
}

/**
 * POST JSON to a URL and read the raw SSE stream body as text.
 * Resolves with { status, headers, body } once the connection closes.
 */
function postSse(url, path, body) {
  return new Promise((resolve, reject) => {
    const req = http.request(`${url}${path}`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Accept: 'text/event-stream',
      },
    }, (res) => {
      let raw = '';
      res.setEncoding('utf8');
      res.on('data', (c) => { raw += c; });
      res.on('end', () => resolve({
        status: res.statusCode,
        headers: res.headers,
        body: raw,
      }));
      res.on('error', reject);
    });
    req.on('error', reject);
    req.write(JSON.stringify(body));
    req.end();
  });
}

/**
 * Parse an SSE stream body into an ordered list of { event, data } frames.
 * `data` is JSON-parsed when possible; otherwise it stays as a string.
 */
function parseSseFrames(raw) {
  return raw.split('\n\n').filter((chunk) => chunk.trim().length > 0).map((chunk) => {
    const lines = chunk.split('\n');
    const evLine = lines.find((l) => l.startsWith('event: '));
    const dataLine = lines.find((l) => l.startsWith('data: '));
    const event = evLine ? evLine.slice('event: '.length) : null;
    const dataStr = dataLine ? dataLine.slice('data: '.length) : null;
    let data = dataStr;
    if (dataStr != null) {
      try { data = JSON.parse(dataStr); } catch { /* leave as string */ }
    }
    return { event, data };
  });
}

// ── Mock provider ───────────────────────────────────────────────────────
//
// Yields the canned delta sequence set via __setDeltas. Each delta is a
// string that would arrive from OpenAI Structured Outputs streaming —
// concatenated they form a valid ask_response_v1 JSON object.

function makeMockProvider() {
  const calls = [];
  let deltas = ['{}'];
  let throwOnStream = null;
  let usage = { inputTokens: 200, outputTokens: 100 };
  return {
    name: () => 'mock',
    modelId: () => 'gpt-4.1-mini-fake',
    async *streamStructured(input) {
      calls.push(input);
      if (throwOnStream) throw throwOnStream;
      for (const d of deltas) {
        yield { type: 'delta', content: d };
      }
      yield { type: 'done', usage, providerModelId: 'gpt-4.1-mini-fake' };
    },
    __setDeltas: (d) => { deltas = d; },
    __throwOnStream: (e) => { throwOnStream = e; },
    __setUsage: (u) => { usage = u; },
    __calls: () => calls,
  };
}

// ── Fixtures ────────────────────────────────────────────────────────────

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
    recurring: [{ label: 'Rent', amount: 25000, period: 'MONTHLY', confidence: 0.98 }],
    top_recipients_this_period: [
      { id: 'r1', spent: 8400, count: 12, category_id: 7, '3mo_avg': 3200 },
    ],
    anomalies_this_week: [],
  };
}

function validAskBody(overrides = {}) {
  return {
    digest: validDigest(),
    history: [],
    question: 'How much did I spend on Food & Dining this month?',
    ...overrides,
  };
}

/**
 * Given a JSON object, split its serialised form into `n` roughly-even
 * chunks so we can simulate the model streaming it a piece at a time.
 */
function splitJson(obj, n = 8) {
  const s = JSON.stringify(obj);
  const size = Math.ceil(s.length / n);
  const out = [];
  for (let i = 0; i < s.length; i += size) out.push(s.slice(i, i + size));
  return out;
}

// ── Suite ───────────────────────────────────────────────────────────────

describe('POST /ai/ask (SSE)', () => {
  let server;
  let mock;

  before(async () => {
    server = await startServer();
  });

  after(async () => {
    await server.close();
    __resetProvider();
    __resetDenyList();
  });

  beforeEach(() => {
    mock = makeMockProvider();
    __setProviderForTest(mock);
    __resetDenyList();
  });

  test('rejects an empty question with 400 before opening the stream', async () => {
    const r = await postSse(server.url, '/ai/ask', validAskBody({ question: '' }));
    assert.equal(r.status, 400);
    const parsed = JSON.parse(r.body);
    assert.equal(parsed.error, 'invalid_request');
    // Provider must not have been called at all.
    assert.equal(mock.__calls().length, 0);
  });

  test('rejects a history turn with unknown role', async () => {
    const r = await postSse(
      server.url,
      '/ai/ask',
      validAskBody({ history: [{ role: 'system', content: 'oops' }] }),
    );
    assert.equal(r.status, 400);
    assert.equal(mock.__calls().length, 0);
  });

  test('happy path — streams body text then emits done envelope', async () => {
    const insight = {
      body: 'You have spent KES 12,400 on Food & Dining this month.',
      assumptions: [],
      action_label: 'Open Food & Dining',
      action_deeplink: 'pesatrack://category/7',
      chart: null,
    };
    mock.__setDeltas(splitJson(insight, 8));

    const r = await postSse(server.url, '/ai/ask', validAskBody());
    assert.equal(r.status, 200);
    assert.match(r.headers['content-type'], /text\/event-stream/);

    const frames = parseSseFrames(r.body);
    const textFrames = frames.filter((f) => f.event === 'text');
    const doneFrames = frames.filter((f) => f.event === 'done');

    assert.ok(textFrames.length >= 1, 'must emit at least one text frame');
    // The reassembled body from the text frames must equal insight.body.
    const streamedBody = textFrames.map((f) => f.data.delta).join('');
    assert.equal(streamedBody, insight.body);

    assert.equal(doneFrames.length, 1);
    assert.equal(doneFrames[0].data.fallback, false);
    assert.equal(doneFrames[0].data.body, insight.body);
    assert.equal(doneFrames[0].data.action_label, 'Open Food & Dining');
    assert.equal(doneFrames[0].data.action_deeplink, 'pesatrack://category/7');
    assert.equal(doneFrames[0].data.chart, null);
  });

  test('projection with chart streams body and emits chart on done', async () => {
    const insight = {
      body: 'Cutting takeout by half would free up KES 54,600 in a year.',
      assumptions: ['Assumes takeout stays at current KES 1,050/wk pace'],
      action_label: 'Open Food & Dining budget',
      action_deeplink: 'pesatrack://category/7',
      chart: {
        type: 'compound_growth',
        unit: 'KES',
        x_labels: ['Wk1', 'Wk2', 'Wk3'],
        series: [{ label: 'Cumulative savings', values: [1050, 2100, 3150] }],
      },
    };
    mock.__setDeltas(splitJson(insight, 12));

    const r = await postSse(server.url, '/ai/ask', validAskBody({
      question: 'If I cut takeout by half, how much could I save in a year?',
    }));
    const frames = parseSseFrames(r.body);
    const doneFrames = frames.filter((f) => f.event === 'done');
    assert.equal(doneFrames.length, 1);
    assert.equal(doneFrames[0].data.fallback, false);
    assert.deepEqual(doneFrames[0].data.chart.x_labels, ['Wk1', 'Wk2', 'Wk3']);
    assert.deepEqual(doneFrames[0].data.assumptions, insight.assumptions);
  });

  test('falls back with reason=projection_no_assumptions when model omits them', async () => {
    const bad = {
      body: 'If you cut takeout by half you could save KES 54,600 in a year.',
      assumptions: [],
      action_label: null,
      action_deeplink: null,
      chart: null,
    };
    mock.__setDeltas(splitJson(bad, 6));

    const r = await postSse(server.url, '/ai/ask', validAskBody());
    const done = parseSseFrames(r.body).find((f) => f.event === 'done');
    assert.equal(done.data.fallback, true);
    assert.equal(done.data.reason, 'projection_no_assumptions');
  });

  test('falls back with reason=chart_length_mismatch when series/x_labels desync', async () => {
    const bad = {
      body: 'Cutting takeout by half frees KES 3,150 over three weeks.',
      assumptions: ['pace assumption'],
      action_label: null,
      action_deeplink: null,
      chart: {
        type: 'compound_growth',
        unit: 'KES',
        x_labels: ['Wk1', 'Wk2', 'Wk3'],
        series: [{ label: 'Savings', values: [1050, 2100] }], // short by one
      },
    };
    mock.__setDeltas(splitJson(bad, 6));

    const r = await postSse(server.url, '/ai/ask', validAskBody());
    const done = parseSseFrames(r.body).find((f) => f.event === 'done');
    assert.equal(done.data.fallback, true);
    assert.equal(done.data.reason, 'chart_length_mismatch');
  });

  test('falls back with reason=imperative_past when model claims to have DONE something', async () => {
    const bad = {
      body: "I've added a Food & Dining budget of KES 10,000 for you.",
      assumptions: [],
      action_label: null,
      action_deeplink: null,
      chart: null,
    };
    mock.__setDeltas(splitJson(bad, 4));

    const r = await postSse(server.url, '/ai/ask', validAskBody());
    const done = parseSseFrames(r.body).find((f) => f.event === 'done');
    assert.equal(done.data.fallback, true);
    assert.equal(done.data.reason, 'imperative_past');
  });

  test('falls back with reason=denylist when body hits deny-list term', async () => {
    __setDenyListForTest({ substrings: ['bitcoin'] });
    const bad = {
      body: 'You could invest KES 5,000 in bitcoin to compound over a year.',
      assumptions: ['assumes 10% annual'],
      action_label: null,
      action_deeplink: null,
      chart: null,
    };
    mock.__setDeltas(splitJson(bad, 4));

    const r = await postSse(server.url, '/ai/ask', validAskBody());
    const done = parseSseFrames(r.body).find((f) => f.event === 'done');
    assert.equal(done.data.fallback, true);
    assert.equal(done.data.reason, 'denylist');
  });

  test('falls back with reason=schema when the stream is malformed JSON', async () => {
    mock.__setDeltas(['{"body": "hello', ' but this JSON never closes']);
    const r = await postSse(server.url, '/ai/ask', validAskBody());
    const done = parseSseFrames(r.body).find((f) => f.event === 'done');
    assert.equal(done.data.fallback, true);
    assert.equal(done.data.reason, 'schema');
  });

  test('falls back with reason=provider_error when the provider throws mid-stream', async () => {
    const wrapped = new Error('openai boom');
    wrapped.code = 'ai_provider_error';
    wrapped.status = 502;
    wrapped.cause = { message: 'insufficient_quota', status: 429, code: 'insufficient_quota' };
    mock.__throwOnStream(wrapped);

    const r = await postSse(server.url, '/ai/ask', validAskBody());
    const done = parseSseFrames(r.body).find((f) => f.event === 'done');
    assert.equal(done.data.fallback, true);
    assert.equal(done.data.reason, 'provider_error');
  });

  test('provider receives the assembled system + user prompt with digest embedded', async () => {
    const insight = {
      body: 'You have spent KES 12,400 on Food & Dining.',
      assumptions: [],
      action_label: null,
      action_deeplink: null,
      chart: null,
    };
    mock.__setDeltas(splitJson(insight, 4));

    await postSse(server.url, '/ai/ask', validAskBody({
      question: 'How much did I spend on Food & Dining this month?',
    }));

    assert.equal(mock.__calls().length, 1);
    const call = mock.__calls()[0];
    assert.equal(call.schemaName, 'ask_response_v1');
    assert.match(call.systemPrompt, /PesaTrack Coach/);
    assert.match(call.userPrompt, /DATA_DIGEST:/);
    assert.match(call.userPrompt, /CURRENT_QUESTION:/);
    assert.match(call.userPrompt, /How much did I spend on Food & Dining this month\?/);
  });

  test('history entries appear in the assembled user prompt (labelled)', async () => {
    const insight = {
      body: 'ok',
      assumptions: [],
      action_label: null,
      action_deeplink: null,
      chart: null,
    };
    mock.__setDeltas(splitJson(insight, 2));

    await postSse(server.url, '/ai/ask', validAskBody({
      history: [
        { role: 'user', content: 'earlier question' },
        { role: 'assistant', content: 'earlier answer' },
      ],
    }));
    const call = mock.__calls()[0];
    assert.match(call.userPrompt, /USER: earlier question/);
    assert.match(call.userPrompt, /ASSISTANT: earlier answer/);
  });
});
