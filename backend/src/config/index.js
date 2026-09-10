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
