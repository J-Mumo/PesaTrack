// Structured JSON logger + PII-scrubbing HTTP access log.
// See plans/ai-pro-phase1-spec.md §4.8

'use strict';

const config = require('../config');

const LEVELS = { trace: 10, debug: 20, info: 30, warn: 40, error: 50 };
const active = LEVELS[config.logLevel] ?? LEVELS.info;

function emit(level, obj) {
  if (LEVELS[level] < active) return;
  const record = {
    ts: new Date().toISOString(),
    level,
    service: config.serviceName,
    version: config.serviceVersion,
    ...obj,
  };
  // Structured JSON to stdout — Docker captures.
  // eslint-disable-next-line no-console
  console.log(JSON.stringify(record));
}

const log = {
  trace: (o) => emit('trace', o),
  debug: (o) => emit('debug', o),
  info: (o) => emit('info', o),
  warn: (o) => emit('warn', o),
  error: (o) => emit('error', o),
};

// PII scrub — belt-and-braces. Callers must not put PII in the record;
// this is a fail-safe.
const PII_KEYS = new Set([
  'purchaseToken', 'purchase_token', 'token',
  'authorization', 'cookie', 'set-cookie',
  'digest', 'body',
  'phone', 'phoneNumber', 'phone_number',
  'email', 'address', 'name',
  'openai_api_key', 'apiKey', 'api_key',
  'password', 'pin',
]);

function scrub(value) {
  if (value === null || value === undefined) return value;
  if (Array.isArray(value)) return value.map(scrub);
  if (typeof value === 'object') {
    const out = {};
    for (const [k, v] of Object.entries(value)) {
      out[k] = PII_KEYS.has(k.toLowerCase()) ? '[redacted]' : scrub(v);
    }
    return out;
  }
  return value;
}

function httpLogger(req, res, next) {
  const start = process.hrtime.bigint();
  res.on('finish', () => {
    const durationMs = Number(process.hrtime.bigint() - start) / 1_000_000;
    log.info({
      msg: 'http',
      request_id: req.id,
      method: req.method,
      path: req.path,
      status: res.statusCode,
      duration_ms: Number(durationMs.toFixed(2)),
      // NEVER log body, headers.authorization, headers.cookie, or query strings
      // (query strings could contain purchase tokens).
    });
  });
  next();
}

module.exports = { log, httpLogger, scrub };
