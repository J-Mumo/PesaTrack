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
   * Uses OpenAI's Structured Outputs (`response_format.type = 'json_schema'`,
   * `strict: true`) which guarantees the returned JSON matches the schema
   * or the API errors. We still parse defensively — a strict-mode success
   * can theoretically ship an empty content string on server-side content
   * filtering, so `JSON.parse` failures roll up as `ai_parse_error`.
   */
  async callStructured({
    systemPrompt,
    userPrompt,
    schema,
    schemaName,
    temperature,
    maxTokens,
  }) {
    const client = this._lazyClient();
    const started = Date.now();
    let completion;
    try {
      completion = await client.chat.completions.create({
        model: this._model,
        messages: [
          { role: 'system', content: systemPrompt },
          { role: 'user', content: userPrompt },
        ],
        response_format: {
          type: 'json_schema',
          json_schema: {
            name: schemaName,
            strict: true,
            schema,
          },
        },
        temperature: typeof temperature === 'number' ? temperature : 0.4,
        max_tokens: typeof maxTokens === 'number' ? maxTokens : this._maxTokensOut,
      });
    } catch (e) {
      const err = new Error(`openai_call_failed: ${e.message}`);
      err.code = 'ai_provider_error';
      err.status = 502;
      err.cause = e;
      throw err;
    }
    const latencyMs = Date.now() - started;

    const choice = completion.choices?.[0];
    const content = choice?.message?.content;
    if (typeof content !== 'string' || content.length === 0) {
      // Structured Outputs guarantees valid JSON on success, so an empty
      // string means safety-filter refusal or an upstream API bug.
      const err = new Error('openai_empty_content');
      err.code = 'ai_empty_content';
      err.status = 502;
      throw err;
    }
    let json;
    try {
      json = JSON.parse(content);
    } catch (e) {
      const err = new Error('openai_json_parse_failed');
      err.code = 'ai_parse_error';
      err.status = 502;
      err.cause = e;
      throw err;
    }

    return {
      json,
      inputTokens: completion.usage?.prompt_tokens || 0,
      outputTokens: completion.usage?.completion_tokens || 0,
      latencyMs,
      providerModelId: completion.model || this._model,
    };
  }

  async *streamStructured(_input) {
    const err = new Error('OpenAiProvider.streamStructured not implemented in Phase 1');
    err.code = 'ai_not_implemented';
    err.status = 501;
    throw err;
  }
}

module.exports = OpenAiProvider;
