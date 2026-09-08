import { defineCollection, z } from 'astro:content';
import { glob } from 'astro/loaders';

/**
 * Content collections
 * See plans/website-full-plan.md §6 (repo layout) and §7 (content sourcing).
 *
 * Blog:     src/content/blog/*.md  → /blog/<slug>
 * Features: src/content/features/*.md → /features/<slug>
 * FAQ:      src/content/faq/*.md     → grouped on /faq
 */

const blog = defineCollection({
  loader: glob({ pattern: '**/*.{md,mdx}', base: './src/content/blog' }),
  schema: z.object({
    title: z.string().max(80),
    dek: z.string().max(200), // 1-line summary shown on index cards + <meta description>
    publishedAt: z.coerce.date(),
    updatedAt: z.coerce.date().optional(),
    tags: z
      .array(
        z.enum(['awareness', 'privacy', 'guides', 'changelog', 'behind-the-build'])
      )
      .default([]),
    author: z.string().default('PesaTrack'),
    draft: z.boolean().default(false),
    // AI-discoverability: TL;DR block shown at top of every post (§8.2.3)
    tldr: z.string().max(400).optional(),
    // Language of the post. `sw` posts live under src/content/blog/sw/ and
    // render at /sw/blog/<slug>. Default = English.
    locale: z.enum(['en', 'sw']).default('en'),
    // Slug of the counterpart in the other locale, used to emit hreflang.
    // e.g. for /blog/mpesa-fees the sw post sets `translationOf: mpesa-fees`.
    translationOf: z.string().optional(),
  }),
});

const features = defineCollection({
  loader: glob({ pattern: '**/*.{md,mdx}', base: './src/content/features' }),
  schema: z.object({
    title: z.string().max(60),
    dek: z.string().max(200),
    order: z.number().int().default(100),
    icon: z.string().optional(), // slug into local icon set
    // "What it does / doesn't do" — enforced by feature page template
    doesDo: z.array(z.string()).default([]),
    doesNotDo: z.array(z.string()).default([]),
    principle: z
      .enum([
        'awareness-before-action',
        'nudge-not-nag',
        'save-and-invest',
        'privacy',
        'honest-numbers',
        'local-first',
      ])
      .optional(),
    // Deprecated features stay on the site but get a banner (§16.1 trigger table)
    deprecated: z.boolean().default(false),
  }),
});

const faq = defineCollection({
  loader: glob({ pattern: '**/*.{md,mdx}', base: './src/content/faq' }),
  schema: z.object({
    question: z.string().max(160),
    group: z.enum([
      'sms-permissions',
      'privacy-data',
      'budgets-categories',
      'imports',
      'troubleshooting',
    ]),
    order: z.number().int().default(100),
    // Stable anchor for AI citations (§8.2.3 point 8) — auto-slugged if omitted
    anchor: z.string().optional(),
  }),
});

const docs = defineCollection({
  loader: glob({ pattern: '**/*.{md,mdx}', base: './src/content/docs' }),
  schema: z.object({
    title: z.string().max(80),
    dek: z.string().max(200),
    section: z.enum(['getting-started', 'using', 'imports', 'privacy-security', 'troubleshooting']),
    order: z.number().int().default(100),
    updatedAt: z.coerce.date().optional(),
    draft: z.boolean().default(false),
  }),
});

export const collections = { blog, features, faq, docs };
