// @ts-check
import { defineConfig } from 'astro/config';
import sitemap from '@astrojs/sitemap';
import mdx from '@astrojs/mdx';

// PesaTrack website
// Site URL: TBD (hosting deferred per plans/website-full-plan.md §14)
// Until a domain is chosen, keep this as a placeholder — override via SITE env var.
const SITE = process.env.SITE ?? 'https://pesatrack.example';

// https://astro.build/config
export default defineConfig({
  site: SITE,
  trailingSlash: 'never',
  build: {
    format: 'file',
  },
  vite: {
    build: {
      rollupOptions: {
        // Pagefind writes /_pagefind/pagefind.js after astro build.
        // Vite must not try to bundle it — treat as an external runtime import.
        external: [/^\/_pagefind\/.*/],
      },
    },
  },
  integrations: [
    mdx(),
    sitemap({
      // Skip generated non-HTML routes. The Swahili mirror (/sw) IS included
      // now that M3 has shipped it.
      filter: (page) =>
        !page.includes('/og/') &&
        !page.endsWith('/factsheet.json') &&
        !page.endsWith('/llms-full.txt'),
      i18n: {
        defaultLocale: 'en',
        locales: {
          en: 'en',
          sw: 'sw-KE',
        },
      },
    }),
  ],
});
