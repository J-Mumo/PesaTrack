/**
 * i18n string dictionary — small, hand-maintained.
 *
 * Scope: only the strings that live in shared chrome (Header, Footer,
 * Breadcrumbs, generic CTAs). Page bodies keep their strings inline in the
 * corresponding /sw/ page. See plans/website-full-plan.md §12.
 *
 * Adding a new language means: (1) add the code to `Lang`, (2) add a column
 * to `T`, (3) add the code to astro.config.mjs sitemap.i18n.locales.
 */

export type Lang = 'en' | 'sw';

export interface Strings {
  nav: {
    howItWorks: string;
    features: string;
    privacy: string;
    faq: string;
    blog: string;
  };
  cta: {
    getOnPlay: string;
  };
  footer: {
    product: string;
    company: string;
    legal: string;
    tagline: string;
    copyright: (year: number, holder: string) => string;
    boilerplateHeading: string;
  };
  breadcrumbs: {
    home: string;
  };
  langSwitch: {
    label: string;
    en: string;
    sw: string;
  };
  skipToContent: string;
}

const en: Strings = {
  nav: {
    howItWorks: 'How it works',
    features: 'Features',
    privacy: 'Privacy',
    faq: 'FAQ',
    blog: 'Blog',
  },
  cta: {
    getOnPlay: 'Get on Play',
  },
  footer: {
    product: 'Product',
    company: 'Company',
    legal: 'Legal',
    tagline: 'Track every M-PESA shilling. Automatically. Privately.',
    copyright: (year, holder) => `© ${year} ${holder}. All rights reserved.`,
    boilerplateHeading: 'About PesaTrack',
  },
  breadcrumbs: {
    home: 'Home',
  },
  langSwitch: {
    label: 'Language',
    en: 'English',
    sw: 'Kiswahili',
  },
  skipToContent: 'Skip to content',
};

const sw: Strings = {
  nav: {
    howItWorks: 'Inavyofanya kazi',
    features: 'Vipengele',
    privacy: 'Faragha',
    faq: 'Maswali',
    blog: 'Blogu',
  },
  cta: {
    getOnPlay: 'Pakua kwenye Play',
  },
  footer: {
    product: 'Bidhaa',
    company: 'Kampuni',
    legal: 'Kisheria',
    tagline: 'Fuatilia kila shilingi ya M-PESA. Kiotomatiki. Kwa faragha.',
    copyright: (year, holder) => `© ${year} ${holder}. Haki zote zimehifadhiwa.`,
    boilerplateHeading: 'Kuhusu PesaTrack',
  },
  breadcrumbs: {
    home: 'Nyumbani',
  },
  langSwitch: {
    label: 'Lugha',
    en: 'English',
    sw: 'Kiswahili',
  },
  skipToContent: 'Ruka kwenda kwa maudhui',
};

const dict: Record<Lang, Strings> = { en, sw };

/** Pick a Strings bundle by language code. Falls back to English. */
export function t(lang: string | undefined): Strings {
  if (lang && lang in dict) return dict[lang as Lang];
  return dict.en;
}

/** Detect the current lang from a URL pathname. Only `sw` is recognised. */
export function langFromPath(pathname: string): Lang {
  if (pathname === '/sw' || pathname.startsWith('/sw/')) return 'sw';
  return 'en';
}

/** Map an English path to its Swahili counterpart, or vice-versa. */
export function switchLang(pathname: string, to: Lang): string {
  const current = langFromPath(pathname);
  if (current === to) return pathname;
  if (to === 'sw') {
    // en → sw
    return pathname === '/' ? '/sw' : `/sw${pathname}`;
  }
  // sw → en
  const stripped = pathname.replace(/^\/sw\/?/, '/');
  return stripped === '' ? '/' : stripped;
}

/** BCP-47 tag suitable for <html lang=""> and hreflang. */
export function htmlLang(lang: Lang): string {
  return lang === 'sw' ? 'sw-KE' : 'en';
}

/**
 * Canonical set of English paths that have a Swahili mirror. Anything not
 * in this set has no `/sw/*` counterpart — Header, Footer, and the language
 * switcher use this to avoid linking to 404s.
 *
 * When you add a new Swahili page under `src/pages/sw/` (or a Swahili blog
 * post), add its English path here.
 */
export const MIRRORED_PATHS: ReadonlySet<string> = new Set([
  '/',
  '/how-it-works',
  '/privacy',
  '/faq',
  '/blog',
  '/blog/mpesa-fees-deserve-own-line',
  '/blog/pesatrack-and-your-sms',
  '/blog/month-starts-on-setting',
]);

/**
 * Does the current URL have a counterpart in the other locale?
 *
 * - Every `/sw/*` page has an English canonical (since Swahili is a strict
 *   subset mirror), so the answer is always `true` when we're on Swahili.
 * - For English pages, we check whether the path is in `MIRRORED_PATHS`.
 */
export function hasCounterpart(pathname: string): boolean {
  if (langFromPath(pathname) === 'sw') return true;
  const normalized = (pathname.replace(/\/$/, '') || '/');
  return MIRRORED_PATHS.has(normalized);
}

/**
 * Given the current pathname, return the set of English routes that should be
 * shown in the primary nav. When on Swahili, we drop items whose Swahili
 * mirror doesn't exist. When on English, everything shows.
 */
export function visibleNavHrefs(
  pathname: string,
  allHrefs: readonly string[]
): readonly string[] {
  if (langFromPath(pathname) === 'en') return allHrefs;
  return allHrefs.filter((h) => MIRRORED_PATHS.has(h));
}
