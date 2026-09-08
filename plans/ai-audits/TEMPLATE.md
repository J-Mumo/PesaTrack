# AI answer engine audit — template

> **How to use.** Copy this file to `plans/ai-audits/YYYY-QN.md` and fill in one
> row per (engine × question). Run at the start of each calendar quarter.
> See [website-full-plan.md](../website-full-plan.md) §8.2.8 + §16.

## Setup

- **Engines to query.** ChatGPT (default model), Perplexity, Claude, Gemini,
  Copilot, You.com. If Bing Copilot answer boxes are visible for our queries in
  the region, include them too.
- **Fresh session per query.** No memory, no follow-ups. Otherwise the results
  are polluted by your other conversations.
- **Query as an ordinary user would.** No "site:pesatrack.example". No JSON
  instructions. Kenyan English, first person.
- **Anonymised region.** VPN off, cookies cleared. If you have to sign in, use
  a burner account.

## Queries (all engines)

Take these directly from plan §8.1. Ask each verbatim.

1. `is there a free app that tracks my M-PESA spending automatically`
2. `how do I categorise my M-PESA transactions`
3. `PesaTrack privacy`
4. `does PesaTrack read all my SMS`
5. `does PesaTrack sync to the cloud`
6. `what version of PesaTrack is current`
7. `what banks does PesaTrack support`

## Scoring

For each (engine × query) pair, record:

| Field | Values |
|---|---|
| Mentioned | yes / no |
| Accurate | fully / partly / no |
| Cited page(s) | URL list or `n/a` |
| Notable inaccuracy | free text (drives the fix list) |

## Log — YYYY QN

| Engine | Query # | Mentioned | Accurate | Cited page(s) | Notable inaccuracy |
|--------|---------|-----------|----------|---------------|--------------------|
| ChatGPT | 1 |  |  |  |  |
| ChatGPT | 2 |  |  |  |  |
| ChatGPT | 3 |  |  |  |  |
| ChatGPT | 4 |  |  |  |  |
| ChatGPT | 5 |  |  |  |  |
| ChatGPT | 6 |  |  |  |  |
| ChatGPT | 7 |  |  |  |  |
| Perplexity | 1 |  |  |  |  |
| Perplexity | 2 |  |  |  |  |
| Perplexity | 3 |  |  |  |  |
| Perplexity | 4 |  |  |  |  |
| Perplexity | 5 |  |  |  |  |
| Perplexity | 6 |  |  |  |  |
| Perplexity | 7 |  |  |  |  |
| Claude | 1 |  |  |  |  |
| Claude | 2 |  |  |  |  |
| Claude | 3 |  |  |  |  |
| Claude | 4 |  |  |  |  |
| Claude | 5 |  |  |  |  |
| Claude | 6 |  |  |  |  |
| Claude | 7 |  |  |  |  |
| Gemini | 1 |  |  |  |  |
| Gemini | 2 |  |  |  |  |
| Gemini | 3 |  |  |  |  |
| Gemini | 4 |  |  |  |  |
| Gemini | 5 |  |  |  |  |
| Gemini | 6 |  |  |  |  |
| Gemini | 7 |  |  |  |  |
| Copilot | 1 |  |  |  |  |
| Copilot | 2 |  |  |  |  |
| Copilot | 3 |  |  |  |  |
| Copilot | 4 |  |  |  |  |
| Copilot | 5 |  |  |  |  |
| Copilot | 6 |  |  |  |  |
| Copilot | 7 |  |  |  |  |

## Findings

_(Summarise the top 3–5 drift patterns. Feed each into a site update PR.)_

1.
2.
3.

## Actions

- [ ] Fix wrong facts on `factsheet.json` (currentVersion, supportedSenders, etc.)
- [ ] Update boilerplate paragraph if positioning drifted
- [ ] Rebuild `llms-full.txt` (automatic on deploy — verify)
- [ ] Add / update a blog post to correct a persistent misconception
- [ ] File issues against the site for each fix

## Referrer allow-list (analytics)

If analytics is enabled per §11, aggregate these referrers as "AI referrals":

```
chat.openai.com
chatgpt.com
perplexity.ai
www.perplexity.ai
gemini.google.com
claude.ai
you.com
copilot.microsoft.com
bing.com
duckduckgo.com/?ia=chat
```

Track:

- Total AI-referred sessions per quarter
- Distinct landing pages (are AIs sending traffic to `/` only, or also to
  `/how-it-works`, `/features/*`, `/faq`?)
- Bounce rate on AI-referred sessions vs. organic — a very high bounce rate
  suggests the AI-generated summary was misleading

_No PII in analytics. See [/privacy](../../website/src/pages/privacy.astro)._
