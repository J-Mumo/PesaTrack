// TODO: Groq provider — not implemented.
// See plans/ai-pro-plan.md §9 "Why we still keep the backend provider-agnostic".

'use strict';

class GroqProvider {
  name() { return 'groq'; }
  modelId() { return 'not-configured'; }
  async callStructured() {
    const err = new Error('GroqProvider not implemented');
    err.code = 'ai_not_implemented';
    err.status = 501;
    throw err;
  }
  async *streamStructured() {
    const err = new Error('GroqProvider not implemented');
    err.code = 'ai_not_implemented';
    err.status = 501;
    throw err;
  }
}

module.exports = GroqProvider;
