# PesaTrack Website

Static marketing site for PesaTrack — the passive M-PESA and Kenyan bank SMS
expense tracker.

> **Status:** M0 foundation. Real content lands in M1.
> See [`plans/website-full-plan.md`](../plans/website-full-plan.md) for the full plan.

---

## Stack

- **[Astro 5](https://astro.build/)** — static-first, zero JS by default.
- **TypeScript strict** — everything type-checked including content collections.
- **Vanilla CSS + tokens** — no CSS-in-JS, no Tailwind. See `src/styles/tokens.css`.
- **MDX** for content authoring.
- **`@astrojs/sitemap`** for `sitemap-index.xml`.
- **pnpm 12** as the package manager.

---

## Prerequisites

- **Node.js** — see `.nvmrc` (currently 22, LTS). Use `nvm use` or install directly.
- **pnpm 10+** — install via `npm i -g pnpm` (or corepack if you have admin rights).

---

## Local development

```powershell
cd website
pnpm install                # first run — install deps
pnpm approve-builds esbuild sharp -y   # only needed once (see gotchas below)
pnpm dev                    # http://localhost:4321
```

Other scripts:

```powershell
pnpm build       # → dist/
pnpm preview     # serve the built dist locally
pnpm check       # astro type + content check
pnpm a11y        # pa11y-ci over the sitemap (requires the server running)
```

---

## Repo layout

```
website/
├── astro.config.mjs            Astro + sitemap + mdx wiring
├── package.json                deps + scripts
├── pnpm-workspace.yaml         pnpm 10+ settings (onlyBuiltDependencies)
├── tsconfig.json               strict TS, "~" path alias → src/
├── .nvmrc                      Node version pin
├── public/
│   ├── favicon.svg
│   ├── robots.txt              LLM allow-list — see plan §8.2.1
│   └── llms.txt                LLM-facing sitemap — see plan §8.2.2
├── src/
│   ├── content.config.ts       Content collection schemas (blog, features, faq)
│   ├── content/                Markdown/MDX authoring
│   │   ├── blog/               land in M2
│   │   ├── features/           land in M1
│   │   └── faq/                land in M1
│   ├── data/
│   │   └── site.ts             SITE + NAV — single source of truth
│   ├── layouts/
│   │   └── BaseLayout.astro    <head>, JSON-LD, skip-link, header, main, footer
│   ├── components/
│   │   ├── Header.astro
│   │   └── Footer.astro
│   ├── pages/
│   │   ├── index.astro         landing (M0 placeholder)
│   │   ├── 404.astro
│   │   └── factsheet.json.ts   → /factsheet.json (§8.2.5, machine-readable canonical facts)
│   └── styles/
│       ├── tokens.css          CSS custom properties — palette, type, spacing
│       └── global.css          minimal reset + base
└── dist/                       build output (gitignored)
```

---

## AI-discoverability files

The site is designed to be cited accurately by AI answer engines
(ChatGPT Search, Perplexity, Google AI Overviews, Claude, Copilot).
See [plan §8.2](../plans/website-full-plan.md#82-ai--llm-discoverability).

| File | Purpose |
|---|---|
| `public/robots.txt` | Explicit allow-list for GPTBot, ClaudeBot, PerplexityBot, Google-Extended, CCBot, Applebot-Extended, Amazonbot, etc. |
| `public/llms.txt` | Markdown site map written for LLMs. |
| `src/pages/factsheet.json.ts` | `/factsheet.json` — canonical facts (current version, platform, supported SMS senders, cloud sync = false). Regenerated per build. |
| `SITE.boilerplate` in `src/data/site.ts` | The one-paragraph description we want AIs and journalists to quote verbatim. Rendered into `<meta description>`, footer, and (future) `/press`. |
| JSON-LD in `BaseLayout.astro` | Organization + WebSite server-rendered on every page. `SoftwareApplication` on `/`. Extend per-page via the `jsonLd` prop. |

**Don't remove or de-emphasise these files without reading plan §8.2 first.**

---

## Design tokens

All colours, type sizes, spacing, radii live in `src/styles/tokens.css` as CSS
custom properties. Dark mode (v1.1+) is a token-swap, not a rewrite.

**Palette:** primary `#1b5e20`, accent `#4caf50`, warning `#c62828`
(reserved for `/security` only — no fear framing anywhere else, per
[product principle #2](../plans/product-principles.md)).

---

## Content collections

Defined in `src/content.config.ts` with `zod` schemas. Adding a blog post,
feature page, or FAQ entry is just creating a Markdown file with matching
frontmatter — the schema fails the build if fields are missing or wrong.

---

## CI

`.github/workflows/website.yml` runs on every PR that touches `website/**` and
weekly on schedule:

- **build** — `pnpm check` + `pnpm build`
- **a11y** — axe-core against the built site (blocks merge on PR)
- **links** — linkinator broken-link scan (informational on PR, hard-fail on schedule)
- **file-issue-on-failure** — on scheduled failure, auto-opens a GitHub issue tagged
  `website` + `ci-failure`. Dedupes against any existing open issue with the same title stem.
  See [plan §19 Q5](../plans/website-full-plan.md#19-open-questions).

---

## Gotchas

### pnpm 10+ blocks build scripts by default

pnpm 10 requires explicit approval for packages that run install scripts.
esbuild and sharp are legitimately needed by Astro. If `pnpm install` fails
with `ERR_PNPM_IGNORED_BUILDS`, run:

```powershell
pnpm approve-builds esbuild sharp -y
```

The allow-list also lives in `pnpm-workspace.yaml` (`onlyBuiltDependencies`).

### `SITE` URL is a placeholder

`astro.config.mjs` and `src/data/site.ts` both use `https://pesatrack.example`
until hosting is decided (see [plan §14](../plans/website-full-plan.md#14-hosting-decisions-deferred)).
Override at build time with `SITE=https://real-domain.tld pnpm build`.

### `factsheet.json`'s `currentVersion` is a manual TODO

Right now it's hardcoded in `src/pages/factsheet.json.ts`. In M3 we wire it to
`_docs/releases.md` per the sync check ([plan §16.1](../plans/website-full-plan.md#161-product-to-website-sync-check)).

---

## Contributing

- **No guest posts** — see [plan §4.12](../plans/website-full-plan.md#412-blog).
- **No third-party trackers** — see [plan §11](../plans/website-full-plan.md#11-analytics--privacy-respecting).
- **No investment claims without visible assumptions** — see
  [product principle #5](../plans/product-principles.md).
- Every PR that changes user-visible copy should show the render — screenshot in the PR body.
- Every app-side change that a user could learn about from the site must run the
  **product-to-website sync check** — see
  [plan §16.1](../plans/website-full-plan.md#161-product-to-website-sync-check).
