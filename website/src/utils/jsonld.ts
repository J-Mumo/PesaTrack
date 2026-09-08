import { SITE } from '~/data/site';

/**
 * JSON-LD builders — see plans/website-full-plan.md §8.2.4.
 * All emitted server-side (some AI crawlers don't run JS).
 */

export interface Crumb {
  name: string;
  href: string;
}

/** BreadcrumbList with absolute URLs. */
export function breadcrumbList(crumbs: Crumb[]) {
  return {
    '@context': 'https://schema.org',
    '@type': 'BreadcrumbList',
    itemListElement: crumbs.map((c, i) => ({
      '@type': 'ListItem',
      position: i + 1,
      name: c.name,
      item: new URL(c.href, SITE.url).toString(),
    })),
  };
}

export interface QA {
  question: string;
  answer: string;
}

/** FAQPage — used on /faq and inline on /how-it-works, /features/* per §8.2.4. */
export function faqPage(items: QA[]) {
  return {
    '@context': 'https://schema.org',
    '@type': 'FAQPage',
    mainEntity: items.map((q) => ({
      '@type': 'Question',
      name: q.question,
      acceptedAnswer: {
        '@type': 'Answer',
        text: q.answer,
      },
    })),
  };
}

/** SoftwareApplication — landing + factsheet. */
export function softwareApplication() {
  return {
    '@context': 'https://schema.org',
    '@type': 'SoftwareApplication',
    name: SITE.name,
    applicationCategory: 'FinanceApplication',
    operatingSystem: 'Android',
    description: SITE.boilerplate,
    offers: {
      '@type': 'Offer',
      price: '0',
      priceCurrency: 'KES',
    },
    url: SITE.playStoreUrl,
  };
}

export interface ArticleMeta {
  title: string;
  description: string;
  publishedAt: Date;
  updatedAt?: Date;
  author?: string;
  url: string;
}

/** Article — blog posts. */
export function article(meta: ArticleMeta) {
  return {
    '@context': 'https://schema.org',
    '@type': 'Article',
    headline: meta.title,
    description: meta.description,
    datePublished: meta.publishedAt.toISOString(),
    dateModified: (meta.updatedAt ?? meta.publishedAt).toISOString(),
    author: {
      '@type': 'Person',
      name: meta.author ?? SITE.name,
    },
    publisher: {
      '@type': 'Organization',
      name: SITE.name,
      url: SITE.url,
    },
    mainEntityOfPage: new URL(meta.url, SITE.url).toString(),
  };
}
