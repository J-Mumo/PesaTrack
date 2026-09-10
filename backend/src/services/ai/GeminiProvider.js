// TODO: Gemini provider — not implemented.
// See plans/ai-pro-plan.md §9 "Why OpenAI" (Gemini token-exhaustion incident).
// Kept as a stub so the provider interface remains provider-agnostic.

'use strict';

class GeminiProvider {
  name() { return 'gemini'; }
  modelId() { return 'not-configured'; }
  async callStructured() {
    const err = new Error('GeminiProvider not implemented');
    err.code = 'ai_not_implemented';
    err.status = 501;
    throw err;
  }
  async *streamStructured() {
    const err = new Error('GeminiProvider not implemented');
    err.code = 'ai_not_implemented';
    err.status = 501;
    throw err;
  }
}

module.exports = GeminiProvider;
