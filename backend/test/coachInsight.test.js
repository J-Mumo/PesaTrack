// Unit tests for src/services/ai/coachInsight.js
//
// Covers the pure pieces: schema shape, digest Zod, postValidate rules,
// deny-list matcher, canonical hash determinism, and the in-memory cache.

'use strict';

const { test, describe, before, beforeEach } = require('node:test');
const assert = require('node:assert/strict');

const {
  coachInsightV1Schema,
  DigestSchema,
  SYSTEM_PROMPT,
  buildUserPrompt,
  denyListMatch,
  postValidate,
  canonicalStringify,
  sha256Canonical,
  cacheGet,
  cacheSet,
  cacheClear,
  cacheSize,
  CACHE_TTL_MS,
  __setDenyListForTest,
  __resetDenyList,
} = require('../src/services/ai/coachInsight');

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
      { id: 3, name: 'Transport', spent: 4200, budget: null, '3mo_avg': 3900, cv: null },
    ],
    recurring: [
      { label: 'Rent', amount: 25000, period: 'MONTHLY', confidence: 0.98 },
    ],
    top_recipients_this_period: [
      { id: 'r1', spent: 8400, count: 12, category_id: 7, '3mo_avg': 3200 },
      { id: 'r2', spent: 4200, count: 8, category_id: 3, '3mo_avg': 4000 },
    ],
    anomalies_this_week: [
      { type: 'category_spike', category_id: 16, delta_pct: 68 },
    ],
    ...overrides,
  };
}

function validInsight(overrides = {}) {
  return {
    title: 'Food spending is up this week',
    body: 'You have spent KES 12,400 on Food & Dining so far this month, KES 2,600 above your typical pace at this point.',
    action_label: 'See Food breakdown',
    action_deeplink: 'pesatrack://category/7',
    saveable_amount_kes: 2600,
    assumptions: ['Assumes Food spending stays at current pace'],
    referenced_recipient_ids: ['r1'],
    ...overrides,
  };
}

// ── Schema shape ────────────────────────────────────────────────────────

describe('coach_insight_v1 schema', () => {
  test('has strict-mode-friendly required list matching properties', () => {
    const props = Object.keys(coachInsightV1Schema.properties).sort();
    const required = [...coachInsightV1Schema.required].sort();
    // Strict mode requires every declared property to be in `required`.
    // referenced_recipient_ids is optional in the plan but still declared.
    for (const req of required) {
      assert.ok(props.includes(req), `required ${req} must be declared as a property`);
    }
  });

  test('additionalProperties is false (strict-mode requirement)', () => {
    assert.equal(coachInsightV1Schema.additionalProperties, false);
  });

  test('action_deeplink pattern rejects arbitrary URLs', () => {
    const p = new RegExp(coachInsightV1Schema.properties.action_deeplink.pattern);
    assert.ok(p.test('pesatrack://home'));
    assert.ok(p.test('pesatrack://category/7'));
    assert.ok(p.test('pesatrack://recipient/r1'));
    assert.ok(!p.test('https://example.com'));
    assert.ok(!p.test('pesatrack://elsewhere'));
    assert.ok(!p.test('pesatrack://recipient/9999')); // must be r-prefix
  });

  test('recipient id pattern requires r-prefix', () => {
    const p = new RegExp(coachInsightV1Schema.properties.referenced_recipient_ids.items.pattern);
    assert.ok(p.test('r1'));
    assert.ok(p.test('r99'));
    assert.ok(!p.test('R1'));
    assert.ok(!p.test('recipient1'));
  });
});

// ── DigestSchema (Zod) ──────────────────────────────────────────────────

describe('DigestSchema (Zod inbound validation)', () => {
  test('accepts a canonical valid digest', () => {
    const parsed = DigestSchema.safeParse(validDigest());
    assert.equal(parsed.success, true);
  });

  test('rejects unknown top-level keys (strict object)', () => {
    const bad = { ...validDigest(), sneaky_extra_field: 'oops' };
    const parsed = DigestSchema.safeParse(bad);
    assert.equal(parsed.success, false);
  });

  test('rejects out-of-range month_start_day', () => {
    assert.equal(DigestSchema.safeParse(validDigest({ month_start_day: 0 })).success, false);
    assert.equal(DigestSchema.safeParse(validDigest({ month_start_day: 29 })).success, false);
  });

  test('rejects unknown recurrence period', () => {
    const bad = validDigest();
    bad.recurring = [{ label: 'X', amount: 1000, period: 'DAILY', confidence: 0.9 }];
    assert.equal(DigestSchema.safeParse(bad).success, false);
  });

  test('rejects a recipient id that is not r-prefixed', () => {
    const bad = validDigest();
    bad.top_recipients_this_period[0].id = 'x1';
    assert.equal(DigestSchema.safeParse(bad).success, false);
  });

  test('accepts null budget and null cv', () => {
    const parsed = DigestSchema.safeParse(validDigest());
    assert.equal(parsed.success, true);
  });

  // Regression cover for the code-22 outage (see plans/ai-pro-phase2-spec.md
  // notes and _docs/releases.md for the full postmortem). Moshi on the
  // Android client used to drop null-valued keys; the client fix
  // (withNullSerialization) makes it emit them, and this defence-in-depth
  // schema change makes the server accept BOTH:
  //   - key present with value null  (matches production wire format now)
  //   - key absent entirely           (matches any older or bug-dropping client)
  // Both must pass forever.
  describe('nullable fields accept null AND absent (undefined)', () => {
    // Deep-clone helper so overrides don't leak between subtests.
    const clone = (o) => JSON.parse(JSON.stringify(o));

    test('categories[].budget accepts null', () => {
      const d = validDigest();
      d.categories[0].budget = null;
      assert.equal(DigestSchema.safeParse(d).success, true);
    });
    test('categories[].budget accepts absent', () => {
      const d = clone(validDigest());
      delete d.categories[0].budget;
      assert.equal(DigestSchema.safeParse(d).success, true);
    });

    test('categories[].id accepts null', () => {
      const d = validDigest();
      d.categories[0].id = null;
      assert.equal(DigestSchema.safeParse(d).success, true);
    });
    test('categories[].id accepts absent', () => {
      const d = clone(validDigest());
      delete d.categories[0].id;
      assert.equal(DigestSchema.safeParse(d).success, true);
    });

    test('categories[].cv accepts null', () => {
      const d = validDigest();
      d.categories[0].cv = null;
      assert.equal(DigestSchema.safeParse(d).success, true);
    });
    test('categories[].cv accepts absent', () => {
      const d = clone(validDigest());
      delete d.categories[0].cv;
      assert.equal(DigestSchema.safeParse(d).success, true);
    });

    test('top_recipients_this_period[].category_id accepts null', () => {
      const d = validDigest();
      d.top_recipients_this_period[0].category_id = null;
      assert.equal(DigestSchema.safeParse(d).success, true);
    });
    test('top_recipients_this_period[].category_id accepts absent', () => {
      const d = clone(validDigest());
      delete d.top_recipients_this_period[0].category_id;
      assert.equal(DigestSchema.safeParse(d).success, true);
    });

    test('anomalies_this_week[].category_id accepts null', () => {
      const d = validDigest();
      d.anomalies_this_week[0].category_id = null;
      assert.equal(DigestSchema.safeParse(d).success, true);
    });
    test('anomalies_this_week[].category_id accepts absent', () => {
      const d = clone(validDigest());
      delete d.anomalies_this_week[0].category_id;
      assert.equal(DigestSchema.safeParse(d).success, true);
    });

    // Real-world reproduction of the code-22 outage: a digest where the
    // first category has no budget AND the first top-recipient has no
    // primary category mapping. Before the fix Zod returned two
    // "Required" errors on `digest.categories.0.budget` and
    // `digest.top_recipients_this_period.0.category_id`. Now both must
    // be accepted, both when absent (old client) and when null (new).
    test('code-22 regression: category-without-budget AND recipient-without-mapping accepted (both absent)', () => {
      const d = clone(validDigest());
      delete d.categories[0].budget;
      delete d.top_recipients_this_period[0].category_id;
      const parsed = DigestSchema.safeParse(d);
      assert.equal(parsed.success, true, parsed.error && JSON.stringify(parsed.error.issues));
    });
    test('code-22 regression: category-without-budget AND recipient-without-mapping accepted (both null)', () => {
      const d = validDigest();
      d.categories[0].budget = null;
      d.top_recipients_this_period[0].category_id = null;
      const parsed = DigestSchema.safeParse(d);
      assert.equal(parsed.success, true, parsed.error && JSON.stringify(parsed.error.issues));
    });
  });
});

// ── Prompt building ─────────────────────────────────────────────────────

describe('buildUserPrompt', () => {
  test('embeds the whole digest JSON verbatim', () => {
    const d = validDigest();
    const prompt = buildUserPrompt(d, new Date('2026-09-20T10:00:00Z'));
    assert.match(prompt, /Today is 2026-09-20\./);
    assert.match(prompt, /day 15 of 30/);
    assert.match(prompt, /DATA_DIGEST:/);
    assert.ok(prompt.includes(JSON.stringify(d, null, 2)));
  });

  test('system prompt pins the KES + no-shame + no-securities rules', () => {
    assert.match(SYSTEM_PROMPT, /Use KES/);
    assert.match(SYSTEM_PROMPT, /Never shame/);
    assert.match(SYSTEM_PROMPT, /Never recommend specific securities/);
    assert.match(SYSTEM_PROMPT, /Never invent numbers/);
  });
});

// ── postValidate rules ─────────────────────────────────────────────────

describe('postValidate', () => {
  test('null on a clean insight against a valid digest', () => {
    assert.equal(postValidate(validInsight(), validDigest()), null);
  });

  test('rejects saveable amount without assumptions', () => {
    const bad = validInsight({ saveable_amount_kes: 2600, assumptions: [] });
    assert.equal(postValidate(bad, validDigest()), 'saveable_no_assumptions');
  });

  test('accepts saveable with assumptions', () => {
    const good = validInsight({
      saveable_amount_kes: 2600,
      assumptions: ['Assumes Food stays at current pace'],
    });
    assert.equal(postValidate(good, validDigest()), null);
  });

  test('accepts null saveable regardless of assumptions', () => {
    const good = validInsight({ saveable_amount_kes: null, assumptions: [] });
    assert.equal(postValidate(good, validDigest()), null);
  });

  test('rejects referenced recipient id not in digest', () => {
    const bad = validInsight({ referenced_recipient_ids: ['r1', 'r99'] });
    assert.equal(postValidate(bad, validDigest()), 'unknown_recipient_id');
  });

  test('rejects foreign currency mention', () => {
    const bad = validInsight({ body: 'You spent USD 10 in transport this month.' });
    assert.equal(postValidate(bad, validDigest()), 'foreign_currency');
  });

  test('does NOT reject KSh / shilling body copy', () => {
    const good = validInsight({ body: 'You spent KSh 12,400 on food this month according to your entries.' });
    assert.equal(postValidate(good, validDigest()), null);
  });

  test('soft-fixes stale category deeplink (does not reject)', () => {
    const digest = validDigest(); // categories 7 and 3
    const insight = validInsight({
      action_label: 'See Utilities',
      action_deeplink: 'pesatrack://category/999',
    });
    assert.equal(postValidate(insight, digest), null);
    assert.equal(insight.action_label, null);
    assert.equal(insight.action_deeplink, null);
  });

  test('soft-fixes stale recipient deeplink', () => {
    const digest = validDigest(); // r1, r2
    const insight = validInsight({
      action_label: 'View recipient',
      action_deeplink: 'pesatrack://recipient/r99',
    });
    assert.equal(postValidate(insight, digest), null);
    assert.equal(insight.action_label, null);
    assert.equal(insight.action_deeplink, null);
  });

  test('leaves valid category deeplink alone', () => {
    const digest = validDigest();
    const insight = validInsight({
      action_label: 'See Food',
      action_deeplink: 'pesatrack://category/7',
    });
    assert.equal(postValidate(insight, digest), null);
    assert.equal(insight.action_deeplink, 'pesatrack://category/7');
  });
});

// ── Deny-list ──────────────────────────────────────────────────────────

describe('denyListMatch', () => {
  before(() => {
    __setDenyListForTest({
      substrings: ['guaranteed return', 'risk-free', 'investment advice'],
      brokers: ['Cytonn', 'Britam'],
      regexes: [/\bbuy\s+[A-Z]{3,5}\s+shares?\b/i],
    });
  });

  test('null on clean text', () => {
    assert.equal(denyListMatch('Your Food spending is up KES 2,600 this week.'), null);
  });

  test('positive on substring (case-insensitive)', () => {
    assert.equal(denyListMatch('This gives a Guaranteed Return of 8%.'), 'guaranteed return');
  });

  test('positive on broker name', () => {
    assert.equal(denyListMatch('Consider parking savings with Cytonn.'), 'cytonn');
  });

  test('positive on regex — buy KCB shares', () => {
    const hit = denyListMatch('You could buy KCB shares with the surplus.');
    assert.ok(hit && hit.includes('buy'));
  });

  test('empty and null text return null', () => {
    assert.equal(denyListMatch(''), null);
    assert.equal(denyListMatch(null), null);
    assert.equal(denyListMatch(undefined), null);
  });
});

describe('production denylist.json loads cleanly', () => {
  test('has real entries', () => {
    __resetDenyList();
    // Force a real read from disk.
    assert.equal(denyListMatch('This is guaranteed return material.'), 'guaranteed return');
  });
});

// ── Canonical hash ─────────────────────────────────────────────────────

describe('canonicalStringify + sha256Canonical', () => {
  test('key order does not affect hash', () => {
    const a = { z: 1, a: 2, m: 3 };
    const b = { a: 2, m: 3, z: 1 };
    assert.equal(sha256Canonical(a), sha256Canonical(b));
  });

  test('nested key order does not affect hash', () => {
    const a = { outer: { z: 1, a: 2 }, list: [{ b: 1, a: 2 }] };
    const b = { list: [{ a: 2, b: 1 }], outer: { a: 2, z: 1 } };
    assert.equal(sha256Canonical(a), sha256Canonical(b));
  });

  test('array order affects hash (arrays are ordered)', () => {
    assert.notEqual(sha256Canonical([1, 2, 3]), sha256Canonical([3, 2, 1]));
  });

  test('the canonical string is deterministic and JSON-parseable', () => {
    const digest = validDigest();
    const s1 = canonicalStringify(digest);
    const s2 = canonicalStringify(digest);
    assert.equal(s1, s2);
    assert.deepEqual(JSON.parse(s1), digest);
  });
});

// ── Cache ──────────────────────────────────────────────────────────────

describe('coach-insight cache', () => {
  beforeEach(() => cacheClear());

  test('miss returns null', () => {
    assert.equal(cacheGet('nope'), null);
    assert.equal(cacheSize(), 0);
  });

  test('put and get round-trips within TTL', () => {
    const now = 1_000_000;
    cacheSet('h', { title: 't' }, now);
    assert.deepEqual(cacheGet('h', now + 1000), { title: 't' });
    assert.equal(cacheSize(), 1);
  });

  test('expired entry evicts on get', () => {
    const now = 1_000_000;
    cacheSet('h', { title: 't' }, now);
    assert.equal(cacheGet('h', now + CACHE_TTL_MS + 1), null);
    assert.equal(cacheSize(), 0);
  });

  test('clear empties the cache', () => {
    cacheSet('a', 1);
    cacheSet('b', 2);
    cacheClear();
    assert.equal(cacheSize(), 0);
  });
});
