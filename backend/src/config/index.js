// Centralized env → typed config loader.
// See plans/ai-pro-phase1-spec.md §4.4

'use strict';

const { z } = require('zod');

const bool = z.union([z.literal('true'), z.literal('false')])
  .transform((v) => v === 'true');

const schema = z.object({
  NODE_ENV: z.enum(['development', 'test', 'production']).default('development'),
  PORT: z.coerce.number().int().positive().default(3000),
  LOG_LEVEL: z.enum(['trace', 'debug', 'info', 'warn', 'error']).default('info'),
  SERVICE_NAME: z.string().default('pesatrack-api'),
  SERVICE_VERSION: z.string().default('0.1.0'),

  DATABASE_URL: z.string().min(1),

  OPENAI_API_KEY: z.string().optional().default(''),
  OPENAI_MODEL: z.string().default('gpt-4.1-mini'),
  OPENAI_MAX_TOKENS_OUT: z.coerce.number().int().positive().default(800),
  AI_COACH_TEMPERATURE: z.coerce.number().min(0).max(2).default(0.4),

  // /ai/ask (Ask Your Money) gets its own model + token budget knobs
  // because chat has meaningfully different constraints from the
  // once-daily Coach Insight card:
  //  - depth-scaling responses (short factual OR long structured
  //    markdown) benefit from a stronger reasoning model,
  //  - the higher token cap accommodates the markdown-heavy structured
  //    answers users get on deep-analysis questions,
  //  - temperature is lower than Coach because we want the analytical
  //    voice to be crisp, not creative.
  // All three keep the OPENAI_ prefix and default to the safe values so
  // an existing .env keeps working; ops overrides them at the container
  // level when we bump.
  OPENAI_ASK_MODEL: z.string().default('gpt-4.1'),
  OPENAI_ASK_MAX_TOKENS_OUT: z.coerce.number().int().positive().default(4000),
  AI_ASK_TEMPERATURE: z.coerce.number().min(0).max(2).default(0.3),

  GOOGLE_PLAY_PACKAGE_NAME: z.string().default('com.pesatrack'),
  GOOGLE_PLAY_SERVICE_ACCOUNT_JSON: z.string().optional().default(''),

  DEV_FAKE_BILLING: bool.default('false'),
  ENABLE_AI_ENDPOINTS: bool.default('false'),
  ENABLE_AI_ECHO: bool.default('false'),

  ALLOWED_ORIGIN_WEB: z.string().url().default('https://pesatrack.jmumo.com'),
});

const parsed = schema.safeParse(process.env);
if (!parsed.success) {
  // eslint-disable-next-line no-console
  console.error('ENV validation failed:', parsed.error.flatten().fieldErrors);
  process.exit(1);
}
const env = parsed.data;

// Safety rail: fake billing must never be on in production.
if (env.NODE_ENV === 'production' && env.DEV_FAKE_BILLING) {
  // eslint-disable-next-line no-console
  console.error('FATAL: DEV_FAKE_BILLING=true is not permitted when NODE_ENV=production');
  process.exit(1);
}

module.exports = {
  nodeEnv: env.NODE_ENV,
  port: env.PORT,
  logLevel: env.LOG_LEVEL,
  serviceName: env.SERVICE_NAME,
  serviceVersion: env.SERVICE_VERSION,

  databaseUrl: env.DATABASE_URL,

  openai: {
    apiKey: env.OPENAI_API_KEY,
    model: env.OPENAI_MODEL,
    maxTokensOut: env.OPENAI_MAX_TOKENS_OUT,
    coachTemperature: env.AI_COACH_TEMPERATURE,
    // Ask-specific overrides. See ENV block above for rationale.
    askModel: env.OPENAI_ASK_MODEL,
    askMaxTokensOut: env.OPENAI_ASK_MAX_TOKENS_OUT,
    askTemperature: env.AI_ASK_TEMPERATURE,
  },

  playBilling: {
    packageName: env.GOOGLE_PLAY_PACKAGE_NAME,
    serviceAccountJsonPath: env.GOOGLE_PLAY_SERVICE_ACCOUNT_JSON,
  },

  devFakeBilling: env.DEV_FAKE_BILLING,
  enableAiEndpoints: env.ENABLE_AI_ENDPOINTS,
  enableAiEcho: env.ENABLE_AI_ECHO,

  allowedOriginWeb: env.ALLOWED_ORIGIN_WEB,
};
