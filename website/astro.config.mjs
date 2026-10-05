// @ts-check
import { defineConfig } from 'astro/config';
import sitemap from '@astrojs/sitemap';
import mdx from '@astrojs/mdx';

// PesaTrack website. SITE remains overridable for preview builds, while the
// production fallback keeps canonical metadata correct outside Docker.
const SITE = process.env.SITE ?? 'https://pesatrack.jmumo.com';

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
