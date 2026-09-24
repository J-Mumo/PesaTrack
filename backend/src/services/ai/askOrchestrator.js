// Ask Your Money service — the shared machinery behind POST /ai/ask.
//
// Owns:
//  - the OpenAI Structured-Outputs JSON schema `ask_response_v1`
//  - the Zod schema for the inbound `POST /ai/ask` request body
//    (digest + history + question)
//  - the pinned system prompt + user-prompt builder
//  - server-side postValidate rules that extend the Phase 2 layer with
//    chart-length checks and the imperative-past scrub
//
// The Coach Insight service exports its own `DigestSchema` (mirrors
// Android's `DataDigest.kt`) — we re-use it here so a schema drift in one
// place is caught in both endpoints.
//
// See plans/ai-pro-phase3-spec.md §5 (schema), §6 (prompts), §8 (route).

'use strict';

const { z } = require('zod');

const { DigestSchema } = require('./coachInsight');

// ── 1. Ask response schema (strict Structured Outputs) ───────────────────
//
// This is the exact object OpenAI is asked to produce. Fields, types, and
// bounds match the Android `AskResponse` data class in slice B1
// (services/ai/AskModels.kt).
//
// The whitelist regex on action_deeplink must stay in lock-step with the
// client's deep-link handler (`Screen` sealed class + NavGraph). Adding a
// new route → update BOTH sides in the same PR.

const askResponseV1Schema = {
  type: 'object',
  additionalProperties: false,
  required: ['body', 'assumptions', 'action_label', 'action_deeplink', 'chart'],
  properties: {
    body: {
      type: 'string',
      minLength: 1,
      maxLength: 800,
      description:
        'The answer. ≤ 5 sentences. Second person, present tense. Include specific KES figures. Never mention specific securities, brokers, guaranteed returns, or claim to have executed anything.',
    },
    assumptions: {
      type: 'array',
      maxItems: 5,
      items: { type: 'string', maxLength: 120 },
      description:
        'Non-empty when body includes a projection OR when chart is non-null. Otherwise empty array.',
    },
    action_label: {
      type: ['string', 'null'],
      maxLength: 40,
      description: 'Optional CTA text. Verb-first. Null if no useful action.',
    },
    action_deeplink: {
      type: ['string', 'null'],
      pattern: '^pesatrack://(home|budgets|analytics|expenses|category/[0-9]+)$',
      description:
        'Optional deep link. Must match the server route whitelist. Null if no action.',
    },
    chart: {
      type: ['object', 'null'],
      required: ['type', 'unit', 'x_labels', 'series'],
      additionalProperties: false,
      properties: {
        type: { type: 'string', enum: ['compound_growth', 'monthly_delta_bar'] },
        unit: { type: 'string', enum: ['KES'] },
        x_labels: {
          type: 'array',
          minItems: 2,
          maxItems: 60,
          items: { type: 'string', maxLength: 8 },
        },
        series: {
          type: 'array',
          minItems: 1,
          maxItems: 2,
          items: {
            type: 'object',
            required: ['label', 'values'],
            additionalProperties: false,
            properties: {
              label: { type: 'string', maxLength: 40 },
              values: {
                type: 'array',
                minItems: 2,
                maxItems: 60,
                items: {
                  type: 'integer',
                  minimum: 0,
                  maximum: 100_000_000,
                },
              },
            },
          },
        },
      },
      description:
        'Optional inline chart for projection / scenario answers. Null otherwise.',
    },
  },
};

// ── 2. Request Zod schema (validates POST /ai/ask before the LLM call) ────
//
// See plans/ai-pro-phase3-spec.md §8.2. Digest re-uses the Phase 2 schema
// so one schema drift breaks both endpoints (which is what we want).
//
// Defence-in-depth pattern (learned in Phase 2 code 22): every nullable
// field on the wire is written .nullable().optional() so an absent key
// deserialises the same as an explicit null. The Android client always
// sends explicit nulls (withNullSerialization on the Retrofit converter);
// keeping .optional() here means an older or bug-dropping client can't
// re-open the same class of silent 400.

const AskTurnSchema = z.object({
  role: z.enum(['user', 'assistant']),
  content: z.string().min(1).max(2000),
});

const askRequestBodySchema = z.object({
  digest: DigestSchema,
  history: z.array(AskTurnSchema).max(50), // client should send ≤10, we accept up to 50 defensively
  question: z.string().min(1).max(500),
});

// ── 3. Post-schema validation (server-side, before emitting done frame) ──
//
// The OpenAI Structured Outputs strict mode guarantees the JSON shape but
// not semantic correctness. Run these checks after the model responds and
// before we serialise the `done` SSE frame.
//
// Rejection returns a reason bucket (never the raw model text) — the route
// handler swaps it for `{ fallback: true, reason: bucket }` and logs.

// Projection markers — patterns in the body that mean the model made a
// hypothetical, future-looking, or scenario claim and therefore MUST
// carry supporting assumptions.
//
// Tightened 2026-09-24 after tester feedback: the previous list flagged
// ordinary English pace phrasing ("KES 4,200 per month", "over the
// year", any sentence starting with "if you") which caused factual
// answers to fail postValidate and swap to the template line. The list
// below only fires on unambiguously projection language:
//
//  - `could save` / `would save` / `would free up` — savings estimates
//  - `what if` — direct hypothetical
//  - `if you (cut|reduce|redirect|invest|stop|start|switch|save|move|halve|double)`
//    — scenario intent verbs
//  - `in a year` / `in five years` etc. — future projection when NOT
//    followed by "ago" (which would make it retrospective)
//
// Not projection: `per month`, `per week`, `per year`, `over the year`,
// `if you look`, `if you're`. Those are how normal factual answers
// naturally phrase pace and framing.
const PROJECTION_MARKERS = [
  /\bcould save\b/i,
  /\bwould save\b/i,
  /\bwould\b[^.!?]*\bfree up\b/i,
  /\bwhat if\b/i,
  /\bif you (cut|reduce|redirect|invest|stop|start|switch|save|move|halve|double)\b/i,
  /\bin (a|one|two|three|five|ten) years?\b(?!\s*ago)/i,
];

const IMPERATIVE_PAST_PATTERNS = [
  /\b(I've|I have)\s+(set|added|created|made|saved|invested|scheduled)\b/i,
  /\bI\s+(set|added|created|made|saved|invested|scheduled)\b/i,
];

/**
 * Post-validate a parsed `ask_response_v1` object against the digest that
 * generated it. Returns `null` on pass, or a bucketed reason string
 * (`chart_length_mismatch` | `projection_no_assumptions` |
 * `imperative_past`). Deny-list scrubbing is delegated to the shared
 * denyListMatch from Phase 2 in the route handler after this check.
 *
 * @param {object} insight parsed askResponseV1 object
 * @returns {string|null} reason bucket, or null on pass
 */
function postValidateAsk(insight) {
  // Rule 1: chart series[].values.length must equal x_labels.length.
  if (insight.chart) {
    const expectedLen = insight.chart.x_labels.length;
    for (const s of insight.chart.series) {
      if (!Array.isArray(s.values) || s.values.length !== expectedLen) {
        return 'chart_length_mismatch';
      }
    }
  }

  // Rule 2: if chart is non-null OR body contains projection markers,
  // assumptions must be non-empty.
  const isProjection =
    insight.chart != null || PROJECTION_MARKERS.some((rx) => rx.test(insight.body));
  if (isProjection && (!Array.isArray(insight.assumptions) || insight.assumptions.length === 0)) {
    return 'projection_no_assumptions';
  }

  // Rule 3: imperative-past scrub (advisory-scope violation — the model
  // must never claim to have done anything to the user's data).
  const flat = [insight.body, ...(insight.assumptions || [])].join(' ');
  if (IMPERATIVE_PAST_PATTERNS.some((rx) => rx.test(flat))) {
    return 'imperative_past';
  }

  return null;
}

// ── 4. System prompt (pinned) ─────────────────────────────────────────────
//
// Kept in this module so it version-locks with the schema. Fixed rules:
// second person, present tense, KES only, no securities/brokers/guarantees,
// advisory-only (no imperative-past claims), assumptions mandatory for
// projections, chart-in-chat semantics.

const SYSTEM_PROMPT = [
  'You are PesaTrack Coach, a financial Q&A assistant for a Kenyan personal',
  'finance app. You answer the user\'s question about their money using the',
  'DataDigest and the last few conversation turns.',
  '',
  'RULES',
  '- Answer in ≤ 5 short sentences. Second person, present tense.',
  '- Use KES with thousands separators (e.g. "KES 12,400"). Never other currencies.',
  '- Never shame or use fear framing. Never say "you overspent", "you are losing money",',
  '  or similar. Frame savings as opportunity, never as failure.',
  '- Never recommend specific securities, brokers, funds, or coins by name.',
  '  Never promise guaranteed returns.',
  '- Never claim to have DONE anything for the user. You are read-only. Say',
  '  "you could" or "you may want to", never "I have set" / "I added" /',
  '  "I created".',
  '- Never invent numbers. Every KES figure must be derivable from the DataDigest',
  '  or a clearly-stated assumption.',
  '',
  'PROJECTIONS / WHAT-IFS',
  '- When the user asks a hypothetical ("what if I cut X by Y") you MUST',
  '  return a non-empty `assumptions` array explaining the numeric basis.',
  '- When appropriate, return a `chart` block (type = compound_growth for',
  '  savings projections; type = monthly_delta_bar for category comparisons).',
  '  Otherwise leave chart null.',
  '- Chart series values are whole KES integers, minimum 0.',
  '',
  'ACTIONS',
  '- Optionally return an action_label + action_deeplink that helps the user',
  '  act on the answer. Deep-link must match the server whitelist',
  '  (home | budgets | analytics | expenses | category/{id}). Otherwise null.',
  '',
  'INPUT: JSON DataDigest + last N conversation turns.',
  'OUTPUT: a single JSON object matching the ask_response_v1 schema.',
].join('\n');

/**
 * Build the OpenAI user prompt from the digest + history + current question.
 *
 * Only the plain-text `body` of prior assistant turns is included —
 * chart/assumptions/action metadata is dropped to keep the context window
 * compact (see spec §6 rationale).
 *
 * @param {object} args
 * @param {object} args.digest DataDigest v1 object (already validated)
 * @param {Array<{role: string, content: string}>} args.history prior turns
 * @param {string} args.question the current user question
 * @param {Date} [args.today] injected for testing
 * @returns {string} the user prompt
 */
function buildAskUserPrompt({ digest, history, question, today = new Date() }) {
  const todayStr = today.toISOString().slice(0, 10);
  const trimmedHistory = history.slice(-10);
  const historyBlock = trimmedHistory.length
    ? trimmedHistory
        .map((t) => `${t.role.toUpperCase()}: ${t.content}`)
        .join('\n')
    : '(no prior turns)';
  return [
    `Today is ${todayStr}.`,
    `The user's current period is ${digest.period} (day ${digest.days_elapsed} of ${digest.days_total}).`,
    '',
    'DATA_DIGEST:',
    JSON.stringify(digest, null, 2),
    '',
    'PRIOR_TURNS:',
    historyBlock,
    '',
    'CURRENT_QUESTION:',
    question,
    '',
    "Produce the assistant's response.",
  ].join('\n');
}

// ── 5. Streaming body extractor ─────────────────────────────────────────
//
// OpenAI Structured Outputs with `stream: true` delivers the response as
// a sequence of `content` deltas that gradually build up a valid JSON
// object matching `askResponseV1Schema`. We want to forward the `body`
// field's characters to the SSE client as `event: text` frames the
// moment they're received (see plans/ai-pro-phase3-spec.md §8.3 step 6),
// and buffer the rest silently until the stream completes.
//
// This class is a small state machine that runs on each incoming delta:
//
//   1. Accumulate raw JSON in a buffer.
//   2. Once we spot `"body"` followed by `:` and an opening `"`, mark
//      that we're inside the body string.
//   3. While inside, consume characters one at a time, handle backslash
//      escapes (`\"`, `\\`, `\n`, etc.) so we don't false-positive on a
//      quote that's actually escaped, and emit the decoded characters
//      as text deltas. Stop at the first *unescaped* closing quote.
//   4. Ignore everything else — the final envelope will be re-parsed
//      by the caller after the stream ends.
//
// Design constraints:
//
//  - Idempotent-ish under repeated feeds: `feed(chunk)` returns whatever
//    body characters became newly visible in this chunk, or `''`.
//  - Never throws on malformed JSON — the caller decides fallback
//    behaviour on `.finalize()`.
//  - Does not attempt to parse anything past the body closing quote.
//    Chart, assumptions, action are extracted from the full buffered
//    string on `.finalize()`.
//
// Not covered by this extractor: streaming Unicode surrogate pairs
// (`\uXXXX`). If the model uses those in the body, we buffer the six
// characters of the escape sequence and emit their decoded rune once
// complete. In practice OpenAI encodes typical latin-1 punctuation as
// raw UTF-8 bytes, so this branch is rarely exercised — but we handle
// it defensively so a stray emoji doesn't break the stream.

class StreamingBodyExtractor {
  constructor() {
    this._buffer = '';
    this._sawBodyStart = false;
    this._insideBody = false;
    this._bodyDone = false;
    // Cursor into `_buffer` marking the next character to inspect.
    this._cursor = 0;
  }

  /**
   * Absorb the next delta from the model and return any newly-visible
   * body characters (decoded — with escapes resolved).
   *
   * @param {string} chunk raw JSON delta from the provider
   * @returns {string} body text delta to send to the SSE client
   */
  feed(chunk) {
    if (typeof chunk !== 'string' || chunk.length === 0) return '';
    this._buffer += chunk;
    let emit = '';

    if (!this._sawBodyStart) {
      // Look for the opening of the body string: `"body"\s*:\s*"`.
      // We don't use regex here because we may receive it split across
      // chunks; instead we peek for the key and find the first quote
      // that follows the colon.
      const keyIdx = this._buffer.indexOf('"body"');
      if (keyIdx < 0) return '';
      // Find the colon after the key.
      let i = keyIdx + '"body"'.length;
      while (i < this._buffer.length && this._buffer[i] !== ':') i++;
      if (i === this._buffer.length) return ''; // colon not yet arrived
      // Find the opening quote of the value.
      i++;
      while (i < this._buffer.length && this._buffer[i] !== '"') {
        // Skip whitespace between colon and opening quote.
        if (this._buffer[i] !== ' ' && this._buffer[i] !== '\t' && this._buffer[i] !== '\n') {
          // A non-whitespace, non-quote char here means the body value
          // is `null` or begins unexpectedly. Bail — we'll rely on the
          // final buffer parse instead of streaming.
          return '';
        }
        i++;
      }
      if (i === this._buffer.length) return ''; // opening quote not yet arrived
      this._sawBodyStart = true;
      this._insideBody = true;
      this._cursor = i + 1; // first char of body value
    }

    if (this._insideBody && !this._bodyDone) {
      // Walk from cursor emitting decoded body chars until we hit an
      // unescaped closing quote.
      while (this._cursor < this._buffer.length) {
        const c = this._buffer[this._cursor];
        if (c === '\\') {
          // Need at least one more character to know what to emit.
          if (this._cursor + 1 >= this._buffer.length) break;
          const next = this._buffer[this._cursor + 1];
          if (next === 'u') {
            // Unicode escape: need 4 more hex digits.
            if (this._cursor + 5 >= this._buffer.length) break;
            const hex = this._buffer.substr(this._cursor + 2, 4);
            emit += String.fromCodePoint(parseInt(hex, 16));
            this._cursor += 6;
          } else {
            emit += decodeSimpleEscape(next);
            this._cursor += 2;
          }
        } else if (c === '"') {
          this._insideBody = false;
          this._bodyDone = true;
          this._cursor += 1;
          break;
        } else {
          emit += c;
          this._cursor += 1;
        }
      }
    }

    return emit;
  }

  /**
   * @returns {string} the full raw JSON accumulated so far — the caller
   * uses this to re-parse the final envelope after the stream ends.
   */
  buffer() {
    return this._buffer;
  }

  /**
   * @returns {boolean} whether the body string was fully consumed.
   * Callers may want to know this to distinguish a truncated stream
   * from a well-formed one.
   */
  bodyDone() {
    return this._bodyDone;
  }
}

function decodeSimpleEscape(c) {
  switch (c) {
    case '"': return '"';
    case '\\': return '\\';
    case '/': return '/';
    case 'b': return '\b';
    case 'f': return '\f';
    case 'n': return '\n';
    case 'r': return '\r';
    case 't': return '\t';
    default: return c; // permissive — unknown escape passes through
  }
}


module.exports = {
  askResponseV1Schema,
  askRequestBodySchema,
  AskTurnSchema,
  postValidateAsk,
  SYSTEM_PROMPT,
  buildAskUserPrompt,
  StreamingBodyExtractor,
  // exposed for tests
  PROJECTION_MARKERS,
  IMPERATIVE_PAST_PATTERNS,
};
