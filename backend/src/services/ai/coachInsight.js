// Coach Insight service — the shared machinery behind POST /ai/coach-insight.
//
// This file owns:
//  - the OpenAI Structured-Outputs JSON schema `coach_insight_v1`
//  - the Zod schema for the inbound `DataDigest` (mirrors the Android
//    `DataDigest.kt` from Phase 2 Slice B1)
//  - the pinned system prompt + user-prompt builder
//  - server-side postValidate rules (§7.4 of the phase-2 spec)
//  - the deny-list matcher (loaded from src/config/denylist.json, §7.3)
//  - canonical JSON hashing for the digest-hash cache key
//  - the in-memory 24h cache
//
// It is intentionally provider-agnostic — the OpenAI call is delegated to
// `services/ai/AiProvider.js` so a future Groq/Gemini switch is a one-file
// change in that module.
//
// See plans/ai-pro-phase2-spec.md §4 (schema), §5 (prompts), §7 (backend).

'use strict';

const crypto = require('node:crypto');
const path = require('node:path');
const fs = require('node:fs');
const { z } = require('zod');

// ── 1. Coach Insight response schema (strict Structured Outputs) ─────────
//
// This is the exact object OpenAI is asked to produce. Fields, types, and
// bounds match the Android `CoachInsight` data class landing in slice B3.

const coachInsightV1Schema = {
  type: 'object',
  additionalProperties: false,
  required: [
    'title',
    'body',
    'action_label',
    'action_deeplink',
    'saveable_amount_kes',
    'assumptions',
    'referenced_recipient_ids',
  ],
  properties: {
    title: { type: 'string', minLength: 8, maxLength: 60 },
    body: { type: 'string', minLength: 40, maxLength: 400 },
    action_label: { type: ['string', 'null'], maxLength: 40 },
    action_deeplink: {
      type: ['string', 'null'],
      pattern: '^pesatrack://(home|budgets|analytics|expenses|category/[0-9]+|recipient/r[0-9]+)$',
    },
    saveable_amount_kes: { type: ['integer', 'null'], minimum: 0, maximum: 1000000 },
    assumptions: {
      type: 'array',
      maxItems: 5,
      items: { type: 'string', maxLength: 120 },
    },
    referenced_recipient_ids: {
      type: 'array',
      maxItems: 3,
      items: { type: 'string', pattern: '^r[0-9]+$' },
    },
  },
};

// ── 2. Digest Zod schema (validates request bodies before the LLM call) ──
//
// Mirrors the shape of Android's `DataDigest.kt` (Phase 2 Slice B1). We
// validate *fields*, not values — the client sends whole KES ints, we let
// them through without extra range checks so a bad claim like "spent =
// KES 900_000_000_000" gets logged as-is and the LLM's numeric grounding
// deals with it. Amount fields are bounded loosely to catch bugs.

const DigestTotalsSchema = z.object({
  spent: z.number().int().min(0).max(1_000_000_000),
  spent_last_period: z.number().int().min(0).max(1_000_000_000),
  spent_3mo_avg: z.number().int().min(0).max(1_000_000_000),
  income_est: z.number().int().min(0).max(1_000_000_000),
  invested_this_period: z.number().int().min(0).max(1_000_000_000),
});

const DigestCategorySchema = z.object({
  id: z.number().int().nullable(),
  name: z.string().min(1).max(80),
  spent: z.number().int().min(0).max(1_000_000_000),
  budget: z.number().int().min(0).max(1_000_000_000).nullable(),
  '3mo_avg': z.number().int().min(0).max(1_000_000_000),
  cv: z.number().min(0).max(100).nullable(),
});

const DigestRecurringSchema = z.object({
  label: z.string().min(1).max(120),
  amount: z.number().int().min(0).max(1_000_000_000),
  period: z.enum(['WEEKLY', 'BIWEEKLY', 'MONTHLY', 'YEARLY']),
  confidence: z.number().min(0).max(1),
});

const DigestRecipientSchema = z.object({
  id: z.string().regex(/^r\d+$/),
  spent: z.number().int().min(0).max(1_000_000_000),
  count: z.number().int().min(0).max(100_000),
  category_id: z.number().int().nullable(),
  '3mo_avg': z.number().int().min(0).max(1_000_000_000),
});

const DigestAnomalySchema = z.object({
  type: z.string().min(1).max(40),
  category_id: z.number().int().nullable(),
  delta_pct: z.number().int(),
});

const DigestSchema = z.object({
  period: z.string().min(4).max(20),
  month_start_day: z.number().int().min(1).max(28),
  days_elapsed: z.number().int().min(1).max(62),
  days_total: z.number().int().min(1).max(62),
  totals: DigestTotalsSchema,
  categories: z.array(DigestCategorySchema).max(20),
  recurring: z.array(DigestRecurringSchema).max(10),
  top_recipients_this_period: z.array(DigestRecipientSchema).max(20),
  anomalies_this_week: z.array(DigestAnomalySchema).max(10),
}).strict();

// ── 3. Pinned system prompt (§5.1) + user prompt builder (§5.2) ──────────

const SYSTEM_PROMPT = [
  'You are PesaTrack Coach, a financial insight generator for a Kenyan',
  'personal-finance app. Your job is to produce ONE useful, specific',
  "observation about the user's spending for today.",
  '',
  'RULES',
  '- Write in second person, present tense.',
  '- Use KES with thousands separators (e.g. "KES 12,400"). Never other currencies.',
  '- Never shame or use fear framing. Never say "you overspent", "you\'re losing money",',
  '  "you shouldn\'t have". Frame savings as opportunity, never as failure.',
  '- Never recommend specific securities, brokers, or funds by name.',
  '  Never promise guaranteed returns.',
  '- Never invent numbers. Every KES figure in your response must be derivable from',
  '  the DataDigest provided.',
  '- Cite at most 3 recipients by ID (r1, r2, ...). The client will replace these',
  '  with real names before showing the user. Do not invent or transliterate names.',
  "- Include an action ONLY if it's a real, useful next step. It's fine to omit.",
  '- If you include a saveable_amount_kes, you MUST list the assumptions behind it.',
  '- Aim for a specific behavioral observation the user probably didn\'t know:',
  '  a category up unexpectedly, a leak, a recurring cost the user might reconsider,',
  '  a savings opportunity grounded in what they already spend.',
  '',
  'INPUT: a DataDigest JSON with aggregated spending numbers.',
  'OUTPUT: a single JSON object matching the coach_insight_v1 schema.',
].join('\n');

function buildUserPrompt(digest, today = new Date()) {
  const todayStr = today.toISOString().slice(0, 10); // YYYY-MM-DD
  const periodLabel = digest.period;
  const daysElapsed = digest.days_elapsed;
  const daysTotal = digest.days_total;
  return [
    `Today is ${todayStr}.`,
    `The user's current period is ${periodLabel} (day ${daysElapsed} of ${daysTotal}).`,
    '',
    'DATA_DIGEST:',
    JSON.stringify(digest, null, 2),
    '',
    "Produce today's coach insight.",
  ].join('\n');
}

// ── 4. Deny-list (§7.3) ──────────────────────────────────────────────────
//
// Data-not-code — lives in src/config/denylist.json. Reloaded on container
// restart. Case-insensitive substring match on the concatenated title +
// body + assumptions text (after any recipient rehydration, but we do
// server-side rehydration only if the client sends a map — which it does
// not, so we scrub the raw model output before it goes back on the wire).

const DENYLIST_PATH = path.join(__dirname, '..', '..', 'config', 'denylist.json');

function loadDenyList() {
  try {
    const raw = fs.readFileSync(DENYLIST_PATH, 'utf8');
    const parsed = JSON.parse(raw);
    return {
      substrings: (parsed.substrings || []).map((s) => s.toLowerCase()),
      brokers: (parsed.brokers || []).map((s) => s.toLowerCase()),
      regexes: (parsed.regexes || []).map((s) => new RegExp(s, 'i')),
    };
  } catch (_) {
    // Missing / malformed deny-list is defensive-fail-open — we log
    // upstream. The tests inject a fixed list rather than reading disk.
    return { substrings: [], brokers: [], regexes: [] };
  }
}

let denyListCache = null;
function getDenyList() {
  if (denyListCache) return denyListCache;
  denyListCache = loadDenyList();
  return denyListCache;
}

/**
 * Force a specific deny list (used by unit tests).
 * @param {{substrings?: string[], brokers?: string[], regexes?: RegExp[]}} list
 */
function __setDenyListForTest(list) {
  denyListCache = {
    substrings: (list.substrings || []).map((s) => s.toLowerCase()),
    brokers: (list.brokers || []).map((s) => s.toLowerCase()),
    regexes: list.regexes || [],
  };
}
function __resetDenyList() {
  denyListCache = null;
}

/**
 * Returns the first matched term (a string), or `null` if clean.
 * Both `substrings` and `brokers` match as case-insensitive substrings;
 * `regexes` are RegExp objects.
 */
function denyListMatch(text) {
  if (!text) return null;
  const dl = getDenyList();
  const lower = text.toLowerCase();
  for (const s of dl.substrings) {
    if (lower.includes(s)) return s;
  }
  for (const s of dl.brokers) {
    if (lower.includes(s)) return s;
  }
  for (const r of dl.regexes) {
    if (r.test(text)) return r.source;
  }
  return null;
}

// ── 5. Post-validation rules (§7.4) ──────────────────────────────────────
//
// Runs after Structured Outputs has enforced the schema. Structured mode
// is strict but doesn't know anything about the digest, so we cross-check
// referenced ids + soft-fix stale deep links here.
//
// Returns `null` on success (and may mutate `insight` in place for
// soft-fixes), or a string bucket name on rejection.

function postValidate(insight, digest) {
  // Rule 1: saveable requires assumptions.
  if (insight.saveable_amount_kes != null &&
      (!Array.isArray(insight.assumptions) || insight.assumptions.length === 0)) {
    return 'saveable_no_assumptions';
  }

  // Rule 2: every referenced_recipient_ids entry must exist in the digest.
  const digestRecipientIds = new Set(
    (digest.top_recipients_this_period || []).map((r) => r.id),
  );
  for (const rid of (insight.referenced_recipient_ids || [])) {
    if (!digestRecipientIds.has(rid)) return 'unknown_recipient_id';
  }

  // Rule 3: body must not mention foreign currencies. "KSh" / "shilling"
  // are fine — we only bounce USD/EUR/GBP.
  const combined = `${insight.title || ''} ${insight.body || ''}`;
  if (/\b(USD|EUR|GBP)\b/i.test(combined)) return 'foreign_currency';

  // Rule 4: action_deeplink references must exist in the digest. Missing
  // referents are a soft-fix (strip the action) — the insight itself is
  // still useful without a tap target.
  if (insight.action_deeplink) {
    const catMatch = insight.action_deeplink.match(/^pesatrack:\/\/category\/(\d+)$/);
    if (catMatch) {
      const catId = Number(catMatch[1]);
      const known = (digest.categories || []).some((c) => c.id === catId);
      if (!known) {
        insight.action_label = null;
        insight.action_deeplink = null;
      }
    }
    const recMatch = insight.action_deeplink &&
      insight.action_deeplink.match(/^pesatrack:\/\/recipient\/(r\d+)$/);
    if (recMatch) {
      const rid = recMatch[1];
      if (!digestRecipientIds.has(rid)) {
        insight.action_label = null;
        insight.action_deeplink = null;
      }
    }
  }

  return null;
}

// ── 6. Canonical JSON hash (cache key) ───────────────────────────────────
//
// The cache is per-digest, so two clients producing the same digest share
// a single OpenAI call. Determinism matters: JSON.stringify with sorted
// keys gets us byte-identical inputs, which SHA-256 turns into a stable
// hex key.

function canonicalStringify(value) {
  if (value === null || typeof value !== 'object') return JSON.stringify(value);
  if (Array.isArray(value)) return `[${value.map(canonicalStringify).join(',')}]`;
  const keys = Object.keys(value).sort();
  const entries = keys.map((k) => `${JSON.stringify(k)}:${canonicalStringify(value[k])}`);
  return `{${entries.join(',')}}`;
}

function sha256Canonical(obj) {
  return crypto.createHash('sha256').update(canonicalStringify(obj)).digest('hex');
}

// ── 7. In-memory 24 h cache ──────────────────────────────────────────────
//
// Per-process Map. Two web replicas would produce two cache misses on the
// first request each — acceptable for the traffic scale we're at. If we
// scale beyond one host, this moves to Redis (§9 of the plan).

const CACHE_TTL_MS = 24 * 60 * 60 * 1000;
const cache = new Map();

function cacheGet(hash, nowMs = Date.now()) {
  const entry = cache.get(hash);
  if (!entry) return null;
  if (entry.expiresAt <= nowMs) {
    cache.delete(hash);
    return null;
  }
  return entry.insight;
}

function cacheSet(hash, insight, nowMs = Date.now()) {
  cache.set(hash, { insight, expiresAt: nowMs + CACHE_TTL_MS });
}

function cacheClear() {
  cache.clear();
}

function cacheSize() {
  return cache.size;
}

module.exports = {
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
};
