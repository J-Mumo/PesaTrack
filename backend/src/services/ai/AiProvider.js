// AiProvider — provider-agnostic interface.
// See plans/ai-pro-plan.md §9 (Why OpenAI, and why still provider-agnostic).
//
// Node/JS has no first-class interface — this file documents the contract that
// every provider (OpenAiProvider, GroqProvider, GeminiProvider) must satisfy.
// Consumers require the provider via `getDefaultProvider()`.

'use strict';

const config = require('../../config');

/**
 * @typedef {Object} StructuredCallInput
 * @property {string} systemPrompt
 * @property {string} userPrompt
 * @property {object} schema                — JSON schema, strict mode
 * @property {string} schemaName            — e.g. "coach_insight_v1"
 * @property {number} [maxTokens]           — overrides provider default
 */

/**
 * @typedef {Object} StructuredCallResult
 * @property {object} json                  — parsed JSON matching schema
 * @property {number} inputTokens
 * @property {number} outputTokens
 * @property {number} latencyMs
 * @property {string} providerModelId       — e.g. "gpt-4.1-mini-2026-08-01"
 */

/**
 * The contract:
 *
 *   class AiProvider {
 *     async callStructured(input: StructuredCallInput): Promise<StructuredCallResult>
 *     async streamStructured(input: StructuredCallInput): AsyncIterable<StreamFrame>
 *     name(): string      // "openai" | "groq" | "gemini"
 *     modelId(): string
 *   }
 *
 * Phase 1 uses NONE of these — `/ai/echo` short-circuits before touching a
 * provider. This scaffold lets Phase 2 land without a package rearrangement.
 */

let cached = null;

function getDefaultProvider() {
  if (cached) return cached;
  // Phase 1: OpenAI is the only real provider. Groq/Gemini are stubs.
  // Selection happens here so we never spread provider-switch logic across
  // callers.
  const OpenAiProvider = require('./OpenAiProvider');
  cached = new OpenAiProvider({
    apiKey: config.openai.apiKey,
    model: config.openai.model,
    maxTokensOut: config.openai.maxTokensOut,
  });
  return cached;
}

module.exports = { getDefaultProvider };
