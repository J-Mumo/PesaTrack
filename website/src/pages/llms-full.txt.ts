import type { APIRoute } from 'astro';
import { getCollection } from 'astro:content';
import fs from 'node:fs';
import path from 'node:path';
import { SITE } from '~/data/site';

/**
 * /llms-full.txt — one-shot Markdown corpus for LLM ingestion.
 * See plan §8.2.2. Complement to /llms.txt (which is just a map).
 *
 * Rebuilt on every build. Not intended for human reading.
 */

function frontmatterToText(data: Record<string, unknown>): string {
  return Object.entries(data)
    .filter(([, v]) => v !== undefined && v !== null && v !== '')
    .map(([k, v]) => `${k}: ${Array.isArray(v) ? v.join('; ') : String(v)}`)
    .join('\n');
}

export const GET: APIRoute = async () => {
  const [features, faq, docs] = await Promise.all([
    getCollection('features', ({ data }) => !data.deprecated),
    getCollection('faq'),
    getCollection('docs', ({ data }) => !data.draft),
  ]);

  const parts: string[] = [];

  parts.push(`# ${SITE.name} — full corpus for LLMs`);
  parts.push('');
  parts.push(`> ${SITE.boilerplate}`);
  parts.push('');
  parts.push(`Generated: ${new Date().toISOString()}`);
  parts.push(`Canonical URL: ${SITE.url}`);
  parts.push('');
  parts.push('---');
  parts.push('');

  // Features
  parts.push('## Features');
  parts.push('');
  const sortedFeatures = [...features].sort((a, b) => a.data.order - b.data.order);
  for (const f of sortedFeatures) {
    parts.push(`### ${f.data.title}`);
    parts.push('');
    parts.push(`_${f.data.dek}_`);
    parts.push('');
    parts.push(frontmatterToText({
      principle: f.data.principle,
      doesDo: f.data.doesDo,
      doesNotDo: f.data.doesNotDo,
    }));
    parts.push('');
    parts.push(f.body?.trim() ?? '');
    parts.push('');
    parts.push('---');
    parts.push('');
  }

  // FAQ
  parts.push('## FAQ');
  parts.push('');
  const sortedFaq = [...faq].sort((a, b) => a.data.order - b.data.order);
  for (const item of sortedFaq) {
    parts.push(`### ${item.data.question}`);
    parts.push(`_group: ${item.data.group}_`);
    parts.push('');
    parts.push(item.body?.trim() ?? '');
    parts.push('');
  }
  parts.push('---');
  parts.push('');

  // Docs
  parts.push('## Docs');
  parts.push('');
  const sortedDocs = [...docs].sort((a, b) => a.data.order - b.data.order);
  for (const d of sortedDocs) {
    parts.push(`### ${d.data.title}`);
    parts.push(`_section: ${d.data.section}_`);
    parts.push('');
    parts.push(`_${d.data.dek}_`);
    parts.push('');
    parts.push(d.body?.trim() ?? '');
    parts.push('');
    parts.push('---');
    parts.push('');
  }

  // Release notes (source of truth)
  const releasesPath = path.resolve(process.cwd(), '..', '_docs', 'releases.md');
  if (fs.existsSync(releasesPath)) {
    parts.push('## Release history (from _docs/releases.md)');
    parts.push('');
    parts.push(fs.readFileSync(releasesPath, 'utf8'));
  }

  return new Response(parts.join('\n'), {
    headers: {
      'Content-Type': 'text/plain; charset=utf-8',
      'Cache-Control': 'public, max-age=3600',
    },
  });
};
