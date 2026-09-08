import { OGImageRoute } from 'astro-og-canvas';
import { getCollection } from 'astro:content';
import { SITE } from '~/data/site';

/**
 * Build-time OG images.
 *
 * - /og/default.png — site-wide fallback used by BaseLayout when a page
 *   doesn't pass an `ogImage` prop.
 * - /og/<slug>.png — one per published blog post.
 *
 * canvaskit-wasm handles glyph rendering headlessly at build.
 * Explicit named exports (not destructured) so Astro's routing validator
 * detects `getStaticPaths` at parse time.
 */

const posts = await getCollection(
  'blog',
  ({ data }) => !data.draft && (data.locale ?? 'en') === 'en'
);

const pages: Record<string, { title: string; description: string }> = {
  default: {
    title: SITE.name,
    description: SITE.tagline,
  },
};

for (const post of posts) {
  const slug = post.id.replace(/\.(md|mdx)$/, '');
  pages[slug] = {
    title: post.data.title,
    description: post.data.dek,
  };
}

const route = await OGImageRoute({
  pages,
  getImageOptions: (_slug, page) => ({
    title: page.title,
    description: page.description,
    bgGradient: [
      [27, 94, 32],
      [46, 125, 50],
    ],
    border: { color: [76, 175, 80], width: 8, side: 'inline-start' },
    padding: 80,
    font: {
      title: {
        color: [255, 255, 255],
        weight: 'Bold',
        size: 56,
        lineHeight: 1.15,
      },
      description: {
        color: [230, 240, 232],
        weight: 'Normal',
        size: 28,
        lineHeight: 1.4,
      },
    },
  }),
});

export const getStaticPaths = route.getStaticPaths;
export const GET = route.GET;
