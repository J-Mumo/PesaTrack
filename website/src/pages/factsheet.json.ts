import type { APIRoute } from 'astro';
import { SITE } from '~/data/site';
import { currentPublishedRelease } from '~/utils/releases';

/**
 * /factsheet.json — machine-readable canonical facts about PesaTrack.
 * See plans/website-full-plan.md §8.2.5.
 *
 * This is what AI answer engines (ChatGPT, Perplexity, Claude, Gemini) cite
 * when a user asks "what version is PesaTrack?" or "does it use the cloud?"
 *
 * Regenerated on every build. If anything here is stale, the corresponding
 * source of truth (site.ts, _docs/releases.md) is wrong first.
 */

export const GET: APIRoute = () => {
  const release = currentPublishedRelease();

  const factsheet = {
    '@context': 'https://schema.org',
    '@type': 'SoftwareApplication',
    name: SITE.name,
    tagline: SITE.tagline,
    description: SITE.boilerplate,
    applicationCategory: 'FinanceApplication',
    operatingSystem: 'Android',
    platform: 'Android',
    currentVersion: release?.version ?? '0.0.0',
    currentVersionReleasedAt: release?.date,
    launched: '2026-01',
    country: SITE.country,
    countryCode: 'KE',
    offers: {
      '@type': 'Offer',
      price: '0',
      priceCurrency: 'KES',
    },
    features: {
      cloudSync: false,
      internetPermission: true,
      internetPermissionNote:
        'Used exclusively for opt-in anonymous Firebase Analytics. Off by default. No transaction data or PII ever leaves the device.',
      ads: false,
      trackers: false,
      offlineFirst: true,
      transactionDataLeavesDevice: false,
    },
    supportedSenders: ['MPESA', 'NCBA'],
    supportedInputSources: ['SMS', 'PDF statement', 'Excel spreadsheet', 'Manual entry'],
    privacyPolicyUrl: new URL('/privacy', SITE.url).toString(),
    playStoreUrl: SITE.playStoreUrl,
    githubUrl: SITE.githubUrl,
    contactEmail: SITE.contactEmail,
    lastGeneratedAt: new Date().toISOString(),
  };

  return new Response(JSON.stringify(factsheet, null, 2), {
    headers: {
      'Content-Type': 'application/json; charset=utf-8',
      'Cache-Control': 'public, max-age=3600',
    },
  });
};
