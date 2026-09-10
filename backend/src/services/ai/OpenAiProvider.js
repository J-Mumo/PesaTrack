// OpenAI provider — Structured Outputs strict mode.
// Phase 1: instantiated but NOT invoked (see /ai/echo).
// Phase 2 fills in real Coach Insight calls.

'use strict';

class OpenAiProvider {
  constructor({ apiKey, model, maxTokensOut }) {
    this._apiKey = apiKey;
    this._model = model;
    this._maxTokensOut = maxTokensOut;
    this._client = null;
  }

  name() {
    return 'openai';
  }

  modelId() {
    return this._model;
  }

  _lazyClient() {
    if (this._client) return this._client;
    if (!this._apiKey) {
      const err = new Error('OPENAI_API_KEY not configured');
      err.code = 'ai_provider_missing_key';
      err.status = 503;
      throw err;
    }
    const OpenAI = require('openai');
    this._client = new OpenAI({ apiKey: this._apiKey });
    return this._client;
  }

  /**
   * Non-streaming structured call.
   * Contract: see AiProvider.js
   *
   * Phase 2 implements the real call. Phase 1 stub throws if invoked so we
   * fail loudly instead of silently sending unconfigured requests.
   */
  async callStructured(_input) {
    const err = new Error('OpenAiProvider.callStructured not implemented in Phase 1');
    err.code = 'ai_not_implemented';
    err.status = 501;
    throw err;
  }

  async *streamStructured(_input) {
    const err = new Error('OpenAiProvider.streamStructured not implemented in Phase 1');
    err.code = 'ai_not_implemented';
    err.status = 501;
    throw err;
  }
}

module.exports = OpenAiProvider;
