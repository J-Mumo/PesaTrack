// Unit tests for src/services/ai/askOrchestrator.js.
//
// Covers the pure pieces landing in Phase 3 slice B1:
//  - JSON schema shape (askResponseV1)
//  - request body Zod validation (askRequestBodySchema)
//  - postValidateAsk chart-length / projection-assumptions / imperative-past rules
//  - buildAskUserPrompt trims history to last 10 and stamps today
//
// SSE plumbing + provider streaming land in B2 and get their own suite.

'use strict';

const { test, describe } = require('node:test');
const assert = require('node:assert/strict');

const {
  askResponseV1Schema,
  askRequestBodySchema,
  postValidateAsk,
  SYSTEM_PROMPT,
  buildAskUserPrompt,
  StreamingBodyExtractor,
} = require('../src/services/ai/askOrchestrator');

// ── Fixtures ────────────────────────────────────────────────────────────

function validDigest(overrides = {}) {
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
    recurring: [
      { label: 'Rent', amount: 25000, period: 'MONTHLY', confidence: 0.98 },
    ],
    top_recipients_this_period: [
      { id: 'r1', spent: 8400, count: 12, category_id: 7, '3mo_avg': 3200 },
    ],
    anomalies_this_week: [
      { type: 'category_spike', category_id: 16, delta_pct: 68 },
    ],
    ...overrides,
  };
}

function validAskRequest(overrides = {}) {
  return {
    digest: validDigest(),
    history: [
      { role: 'user', content: 'How much did I spend on takeout this week?' },
      { role: 'assistant', content: 'You have spent KES 4,200 on takeout this week.' },
    ],
    question: 'If I cut it in half, how much could I save in a year?',
    ...overrides,
  };
}

/**
 * Factual (non-projection) response — no chart, no assumptions required.
 */
function validFactualInsight(overrides = {}) {
  return {
    body:
      'You have spent KES 12,400 on Food & Dining so far this month, ' +
      '20% more than your 3-month average of KES 9,800.',
    assumptions: [],
    action_label: 'Open Food & Dining',
    action_deeplink: 'pesatrack://category/7',
    chart: null,
    ...overrides,
  };
}

/**
 * Projection response — carries a chart AND non-empty assumptions.
 */
function validProjectionInsight(overrides = {}) {
  return {
    body:
      'Cutting takeout by half would free up about KES 1,050 per week, ' +
      'or KES 54,600 in a year.',
    assumptions: ['Assumes takeout spending stays at current KES 2,100/wk pace'],
    action_label: 'Open Food & Dining budget',
    action_deeplink: 'pesatrack://category/7',
    chart: {
      type: 'compound_growth',
      unit: 'KES',
      x_labels: ['Wk1', 'Wk2', 'Wk3'],
      series: [
        { label: 'Cumulative savings', values: [1050, 2100, 3150] },
      ],
    },
    ...overrides,
  };
}

// ── Schema shape (askResponseV1) ────────────────────────────────────────

describe('ask_response_v1 schema', () => {
  test('required list matches property list (strict-mode friendly)', () => {
    const props = Object.keys(askResponseV1Schema.properties).sort();
    const req = [...askResponseV1Schema.required].sort();
    assert.deepEqual(props, req);
  });

  test('additionalProperties is false (strict-mode requirement)', () => {
    assert.equal(askResponseV1Schema.additionalProperties, false);
  });

  test('action_deeplink pattern rejects arbitrary URLs and off-whitelist routes', () => {
    const p = new RegExp(askResponseV1Schema.properties.action_deeplink.pattern);
    assert.ok(p.test('pesatrack://home'));
    assert.ok(p.test('pesatrack://category/7'));
    assert.ok(p.test('pesatrack://expenses'));
    assert.ok(!p.test('https://example.com'));
    assert.ok(!p.test('pesatrack://elsewhere'));
    // Ask-Your-Money-specific: recipient/{id} is a Coach-Insight route
    // only — the Ask schema deliberately does not expose it (recipient
    // deep-links are noisy in a chat context; users can filter by
    // category instead).
    assert.ok(!p.test('pesatrack://recipient/r1'));
  });

  test('chart type enum is exactly [compound_growth, monthly_delta_bar]', () => {
    assert.deepEqual(
      askResponseV1Schema.properties.chart.properties.type.enum,
      ['compound_growth', 'monthly_delta_bar'],
    );
  });

  test('chart unit enum is exactly [KES]', () => {
    assert.deepEqual(askResponseV1Schema.properties.chart.properties.unit.enum, ['KES']);
  });

  test('chart series is bounded 1..2 entries', () => {
    assert.equal(askResponseV1Schema.properties.chart.properties.series.minItems, 1);
    assert.equal(askResponseV1Schema.properties.chart.properties.series.maxItems, 2);
  });

  test('body is bounded 1..800 chars (matches spec §5)', () => {
    assert.equal(askResponseV1Schema.properties.body.minLength, 1);
    assert.equal(askResponseV1Schema.properties.body.maxLength, 800);
  });
});

// ── Request Zod schema (askRequestBodySchema) ───────────────────────────

describe('askRequestBodySchema (Zod inbound validation)', () => {
  test('accepts a canonical valid request', () => {
    assert.equal(askRequestBodySchema.safeParse(validAskRequest()).success, true);
  });

  test('rejects an empty question', () => {
    const parsed = askRequestBodySchema.safeParse(validAskRequest({ question: '' }));
    assert.equal(parsed.success, false);
  });

  test('rejects a question > 500 chars', () => {
    const long = 'x'.repeat(501);
    assert.equal(askRequestBodySchema.safeParse(validAskRequest({ question: long })).success, false);
  });

  test('accepts empty history array (first turn)', () => {
    assert.equal(askRequestBodySchema.safeParse(validAskRequest({ history: [] })).success, true);
  });

  test('rejects a history turn with unknown role', () => {
    const bad = validAskRequest({
      history: [{ role: 'system', content: 'oops' }],
    });
    assert.equal(askRequestBodySchema.safeParse(bad).success, false);
  });

  test('rejects a history turn with empty content', () => {
    const bad = validAskRequest({
      history: [{ role: 'user', content: '' }],
    });
    assert.equal(askRequestBodySchema.safeParse(bad).success, false);
  });

  test('accepts up to 50 history turns (defensive cap; client trims to 10)', () => {
    const many = Array.from({ length: 50 }, (_, i) => ({
      role: i % 2 === 0 ? 'user' : 'assistant',
      content: `turn ${i}`,
    }));
    assert.equal(askRequestBodySchema.safeParse(validAskRequest({ history: many })).success, true);
  });

  test('rejects > 50 history turns', () => {
    const way_too_many = Array.from({ length: 51 }, (_, i) => ({
      role: 'user',
      content: `turn ${i}`,
    }));
    assert.equal(
      askRequestBodySchema.safeParse(validAskRequest({ history: way_too_many })).success,
      false,
    );
  });

  test('rejects a malformed digest (missing required top-level key)', () => {
    const bad = validAskRequest();
    delete bad.digest.totals;
    assert.equal(askRequestBodySchema.safeParse(bad).success, false);
  });
});

// ── postValidateAsk ─────────────────────────────────────────────────────

describe('postValidateAsk', () => {
  test('null on a clean factual insight', () => {
    assert.equal(postValidateAsk(validFactualInsight()), null);
  });

  test('null on a clean projection insight with chart + assumptions', () => {
    assert.equal(postValidateAsk(validProjectionInsight()), null);
  });

  // ── Rule 1: chart length ──
  test('chart_length_mismatch when series.values.length != x_labels.length', () => {
    const bad = validProjectionInsight({
      chart: {
        type: 'compound_growth',
        unit: 'KES',
        x_labels: ['Wk1', 'Wk2', 'Wk3'],
        series: [{ label: 'Savings', values: [1050, 2100] }],
      },
    });
    assert.equal(postValidateAsk(bad), 'chart_length_mismatch');
  });

  test('chart_length_mismatch when any of multiple series is wrong length', () => {
    const bad = validProjectionInsight({
      chart: {
        type: 'monthly_delta_bar',
        unit: 'KES',
        x_labels: ['Jan', 'Feb', 'Mar'],
        series: [
          { label: 'Current', values: [1000, 2000, 3000] },
          { label: 'Projected', values: [800, 1500] }, // short
        ],
      },
    });
    assert.equal(postValidateAsk(bad), 'chart_length_mismatch');
  });

  // ── Rule 2: projection → assumptions required ──
  test('projection_no_assumptions when chart is present but assumptions is empty', () => {
    const bad = validProjectionInsight({ assumptions: [] });
    assert.equal(postValidateAsk(bad), 'projection_no_assumptions');
  });

  test('projection_no_assumptions when body says "could save" but assumptions empty and no chart', () => {
    const bad = validFactualInsight({
      body: 'You could save KES 5,000 per month by trimming takeout.',
    });
    assert.equal(postValidateAsk(bad), 'projection_no_assumptions');
  });

  test('projection_no_assumptions when body says "if you cut X"', () => {
    const bad = validFactualInsight({
      body: 'If you cut takeout by half you would spend less.',
    });
    assert.equal(postValidateAsk(bad), 'projection_no_assumptions');
  });

  test('null when body is purely factual (no projection markers) even with no assumptions', () => {
    const fine = validFactualInsight({
      body: 'You have spent KES 12,400 on Food & Dining this month.',
      assumptions: [],
    });
    assert.equal(postValidateAsk(fine), null);
  });

  // Tester-fed regressions (2026-09-24) — the previous marker list
  // flagged these as projections and swapped in the template line.
  // The tightened list must accept them as factual.

  test('null on "you are paying r1 KES X per month" (factual pace phrasing)', () => {
    const fine = validFactualInsight({
      body: 'You are paying r1 the most this month at KES 4,200 per month, followed by r2 at KES 2,100.',
      assumptions: [],
    });
    assert.equal(postValidateAsk(fine), null);
  });

  test('null on "over the year" retrospective phrasing', () => {
    const fine = validFactualInsight({
      body: 'Over the year you have spent KES 68,000 on Food & Dining, or about KES 5,700 per month on average.',
      assumptions: [],
    });
    assert.equal(postValidateAsk(fine), null);
  });

  test('null on "if you look at your top recipients" framing (not a projection intent verb)', () => {
    const fine = validFactualInsight({
      body: 'If you look at your top recipients this month, r1 tops the list at KES 4,200.',
      assumptions: [],
    });
    assert.equal(postValidateAsk(fine), null);
  });

  test("null on \"if you're wondering\" framing", () => {
    const fine = validFactualInsight({
      body: "If you're wondering where the largest transfer went, it was r1 with KES 12,400.",
      assumptions: [],
    });
    assert.equal(postValidateAsk(fine), null);
  });

  test('projection_no_assumptions still fires on "in a year" future projection with empty assumptions', () => {
    const bad = validFactualInsight({
      body: 'Trimming takeout by half frees up about KES 54,600 in a year.',
      assumptions: [],
    });
    assert.equal(postValidateAsk(bad), 'projection_no_assumptions');
  });

  // ── Rule 3: imperative-past scrub ──
  test('imperative_past when body claims "I set" a budget', () => {
    const bad = validFactualInsight({
      body: 'I set your Food & Dining budget to KES 10,000.',
    });
    assert.equal(postValidateAsk(bad), 'imperative_past');
  });

  test('imperative_past when body claims "I have saved"', () => {
    const bad = validFactualInsight({
      body: 'I have saved KES 5,000 to your Savings & Investments group.',
    });
    assert.equal(postValidateAsk(bad), 'imperative_past');
  });

  test("imperative_past when body claims \"I've added\" a category", () => {
    const bad = validFactualInsight({
      body: "I've added a new Transport sub-category for you.",
    });
    assert.equal(postValidateAsk(bad), 'imperative_past');
  });

  test('null on advisory phrasing "you could" / "you may want to"', () => {
    const fine = validFactualInsight({
      body: 'You could set a Food & Dining budget of KES 10,000 to keep pace with average months.',
    });
    // "you could" alone triggers the projection-marker path — but no
    // chart AND explicit non-empty assumptions would still pass. Here
    // we just check the imperative-past rule doesn't fire.
    fine.assumptions = ['Assumes you keep current spend distribution'];
    assert.equal(postValidateAsk(fine), null);
  });
});

// ── System prompt integrity ─────────────────────────────────────────────

describe('SYSTEM_PROMPT', () => {
  test('pins KES-only + no shame + no securities + advisory-only rules', () => {
    assert.match(SYSTEM_PROMPT, /KES [\d,]+/); // shows the KES 12,400 formatting example
    assert.match(SYSTEM_PROMPT, /Never shame/);
    assert.match(SYSTEM_PROMPT, /Never recommend specific securities/);
    assert.match(SYSTEM_PROMPT, /Never claim to have DONE anything/);
    assert.match(SYSTEM_PROMPT, /Never invent numbers/);
  });

  test('pins projection → assumptions rule', () => {
    assert.match(SYSTEM_PROMPT, /assumptions/);
    assert.match(SYSTEM_PROMPT, /hypothetical/i);
  });

  test('pins depth-scaling instruction (short factual vs long structured)', () => {
    assert.match(SYSTEM_PROMPT, /Factual \/ narrow questions/);
    assert.match(SYSTEM_PROMPT, /Deep \/ analytical questions/);
    assert.match(SYSTEM_PROMPT, /markdown/i);
    assert.match(SYSTEM_PROMPT, /pipe (tables|syntax)/i);
  });

  test('locks the ##-heading convention (no h1) so client renderer stays simple', () => {
    assert.match(SYSTEM_PROMPT, /## Heading/);
    assert.match(SYSTEM_PROMPT, /never `#`/);
  });
});

// ── User prompt builder ─────────────────────────────────────────────────

describe('buildAskUserPrompt', () => {
  test('embeds the whole digest JSON verbatim', () => {
    const d = validDigest();
    const prompt = buildAskUserPrompt({
      digest: d,
      history: [],
      question: 'How much did I spend?',
      today: new Date('2026-09-20T10:00:00Z'),
    });
    assert.match(prompt, /Today is 2026-09-20\./);
    assert.match(prompt, /day 15 of 30/);
    assert.match(prompt, /DATA_DIGEST:/);
    assert.match(prompt, /CURRENT_QUESTION:/);
    assert.ok(prompt.includes(JSON.stringify(d, null, 2)));
  });

  test('stamps "(no prior turns)" for empty history', () => {
    const p = buildAskUserPrompt({
      digest: validDigest(),
      history: [],
      question: 'q',
    });
    assert.match(p, /PRIOR_TURNS:\n\(no prior turns\)/);
  });

  test('emits USER / ASSISTANT labelled lines for prior history', () => {
    const p = buildAskUserPrompt({
      digest: validDigest(),
      history: [
        { role: 'user', content: 'Hello' },
        { role: 'assistant', content: 'Hi there' },
      ],
      question: 'Follow-up',
    });
    assert.match(p, /USER: Hello/);
    assert.match(p, /ASSISTANT: Hi there/);
  });

  test('trims history to the last 10 turns (12-turn input → 10-turn output)', () => {
    // Zero-pad the token so substring collisions can't fool the test
    // (naïve `msg1` is a substring of `msg11`).
    const twelve = Array.from({ length: 12 }, (_, i) => ({
      role: i % 2 === 0 ? 'user' : 'assistant',
      content: `msg_${String(i).padStart(2, '0')}`,
    }));
    const p = buildAskUserPrompt({
      digest: validDigest(),
      history: twelve,
      question: 'Latest',
    });
    // msg_00 and msg_01 (oldest two) must be trimmed; msg_02..msg_11 kept.
    assert.ok(!p.includes('msg_00'), 'oldest turn should be trimmed');
    assert.ok(!p.includes('msg_01'), 'second-oldest turn should be trimmed');
    assert.ok(p.includes('msg_02'), 'third-oldest turn should survive');
    assert.ok(p.includes('msg_11'), 'newest turn should survive');
  });
});

// ── StreamingBodyExtractor ──────────────────────────────────────────────

describe('StreamingBodyExtractor', () => {
  // A canonical happy-path response, split many different ways.
  const RESPONSE = {
    body: 'You have spent KES 12,400 on Food & Dining this month.',
    assumptions: [],
    action_label: null,
    action_deeplink: null,
    chart: null,
  };
  const RESPONSE_STR = JSON.stringify(RESPONSE);

  function feedInChunks(str, chunkSize) {
    const ext = new StreamingBodyExtractor();
    let out = '';
    for (let i = 0; i < str.length; i += chunkSize) {
      out += ext.feed(str.slice(i, i + chunkSize));
    }
    return { out, ext };
  }

  test('emits nothing until the body value opening quote is seen', () => {
    const ext = new StreamingBodyExtractor();
    assert.equal(ext.feed('{"assumptions":[],"body":'), '');
    assert.equal(ext.feed('"'), ''); // opening quote alone still emits nothing
  });

  test('emits body characters as they arrive when fed one char at a time', () => {
    const { out } = feedInChunks(RESPONSE_STR, 1);
    assert.equal(out, RESPONSE.body);
  });

  test('emits body characters when fed in 3-char chunks', () => {
    const { out } = feedInChunks(RESPONSE_STR, 3);
    assert.equal(out, RESPONSE.body);
  });

  test('emits body characters when fed as one giant chunk', () => {
    const { out } = feedInChunks(RESPONSE_STR, RESPONSE_STR.length);
    assert.equal(out, RESPONSE.body);
  });

  test('marks bodyDone once the closing quote is consumed', () => {
    const { ext } = feedInChunks(RESPONSE_STR, 5);
    assert.equal(ext.bodyDone(), true);
  });

  test('stops emitting after the closing quote — chart data does not leak', () => {
    const response = {
      body: 'hi',
      assumptions: ['x'],
      action_label: null,
      action_deeplink: null,
      chart: { type: 'compound_growth', unit: 'KES', x_labels: ['A'], series: [] },
    };
    const s = JSON.stringify(response);
    const { out } = feedInChunks(s, 2);
    assert.equal(out, 'hi'); // NOT "hi[\"x\"]nullnull{…"
  });

  test('decodes \\" escape correctly (emits a literal quote)', () => {
    // body value is `He said "hi"` — JSON-encodes as `"He said \"hi\""`.
    const response = {
      body: 'He said "hi"',
      assumptions: [],
      action_label: null,
      action_deeplink: null,
      chart: null,
    };
    const s = JSON.stringify(response);
    const { out } = feedInChunks(s, 4);
    assert.equal(out, 'He said "hi"');
  });

  test('decodes \\\\ escape correctly (emits a literal backslash)', () => {
    const response = {
      body: 'path C:\\Users',
      assumptions: [],
      action_label: null,
      action_deeplink: null,
      chart: null,
    };
    const s = JSON.stringify(response);
    const { out } = feedInChunks(s, 3);
    assert.equal(out, 'path C:\\Users');
  });

  test('decodes \\n escape correctly (emits a literal newline)', () => {
    const response = {
      body: 'line one\nline two',
      assumptions: [],
      action_label: null,
      action_deeplink: null,
      chart: null,
    };
    const s = JSON.stringify(response);
    const { out } = feedInChunks(s, 5);
    assert.equal(out, 'line one\nline two');
  });

  test('does NOT stop at an escaped quote spanning two feed() calls', () => {
    // Simulate the classic streaming pitfall: the `\` and the `"` after
    // it arrive in separate chunks. A naive extractor treats the `"` as
    // the end-of-body marker and truncates. The state machine must
    // buffer the incomplete escape.
    const ext = new StreamingBodyExtractor();
    const out1 = ext.feed('{"body":"before ');
    const out2 = ext.feed('\\'); // dangling backslash
    const out3 = ext.feed('"after"}');
    const combined = out1 + out2 + out3;
    assert.equal(combined, 'before "after');
    assert.equal(ext.bodyDone(), true);
  });

  test('exposes the full raw buffer for post-stream JSON.parse', () => {
    const { ext } = feedInChunks(RESPONSE_STR, 7);
    assert.equal(ext.buffer(), RESPONSE_STR);
    // Sanity: the buffer should re-parse to the original object.
    assert.deepEqual(JSON.parse(ext.buffer()), RESPONSE);
  });

  test('handles field order variation (body key not first)', () => {
    const response = {
      chart: null,
      action_label: null,
      action_deeplink: null,
      assumptions: [],
      body: 'body appears last in the object',
    };
    const s = JSON.stringify(response);
    const { out } = feedInChunks(s, 4);
    assert.equal(out, 'body appears last in the object');
  });
});
