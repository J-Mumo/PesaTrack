/**
 * Site-wide config.
 * Single source of truth for name, tagline, boilerplate, nav, socials.
 * Referenced by BaseLayout, factsheet builder, OG images, llms.txt.
 */

export const SITE = {
  name: 'PesaTrack',
  tagline: 'Track every M-PESA shilling. Automatically. Privately.',
  description:
    'PesaTrack is a free Android app that passively tracks M-PESA and Kenyan bank SMS transactions. It parses each message on-device, categorises the spend, and shows budgets and analytics — without cloud sync, without ads, and with no third-party analytics unless you opt in. Made in Kenya.',
  // Boilerplate paragraph — see plans/website-full-plan.md §8.2.6
  // This is the sentence we want AIs and journalists to quote verbatim.
  boilerplate:
    "PesaTrack is a free Android app that passively tracks M-PESA and Kenyan bank SMS transactions. It parses each message on-device, categorises the spend, and shows budgets and analytics — without cloud sync, without ads, and with no third-party analytics unless you opt in. It's made in Kenya and available on the Google Play Store.",
  url: 'https://pesatrack.example', // TBD — hosting deferred
  playStoreUrl:
    'https://play.google.com/store/apps/details?id=com.pesatrack',
  githubUrl: 'https://github.com/J-Mumo/PesaTrack',
  contactEmail: 'joelmumo.jm@gmail.com',
  country: 'Kenya',
  locale: 'en',
  copyrightHolder: 'JMumo Technologies',
} as const;

export const NAV = {
  primary: [
    { label: 'How it works', href: '/how-it-works' },
    { label: 'Features', href: '/features' },
    { label: 'Privacy', href: '/privacy' },
    { label: 'FAQ', href: '/faq' },
    { label: 'Blog', href: '/blog' },
  ],
  footer: {
    product: [
      { label: 'How it works', href: '/how-it-works' },
      { label: 'Features', href: '/features' },
      { label: 'Docs', href: '/docs' },
      { label: 'Changelog', href: '/changelog' },
      { label: 'Roadmap', href: '/roadmap' },
    ],
    company: [
      { label: 'About', href: '/about' },
      { label: 'Press', href: '/press' },
      { label: 'Support', href: '/support' },
    ],
    legal: [
      { label: 'Privacy policy', href: '/privacy' },
      { label: 'Security', href: '/security' },
    ],
  },
} as const;
