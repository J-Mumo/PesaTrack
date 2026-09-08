/**
 * Roadmap data — three honest columns.
 *
 * Rule: nothing in `shippingNow` unless the code is merged. Nothing in
 * `considering` we don't intend to ship this year. `notPlanned` is where
 * we're honest about scope.
 */

export interface RoadmapItem {
  title: string;
  detail: string;
}

export const shippingNow: RoadmapItem[] = [
  {
    title: 'M-PESA + NCBA SMS auto-detection',
    detail: 'Live in production. Duplicate-safe via M-PESA transaction ID.',
  },
  {
    title: 'Category budgets with configurable month-start',
    detail: 'Set the day your budget month starts on so numbers align with payday.',
  },
  {
    title: 'Analytics: monthly, quarterly, year-in-review',
    detail: 'Category, trend, savings-rate, and income-vs-spend charts.',
  },
  {
    title: 'PDF statement + Excel import',
    detail: 'Backfill months of history on-device. No upload.',
  },
  {
    title: 'PIN lock + biometric unlock',
    detail: 'Optional. Salted SHA-256 PIN hash.',
  },
  {
    title: 'Income tracking with source learning',
    detail: 'Salary, business, refunds, transfers-in. Bank↔M-PESA self-transfers auto-excluded.',
  },
  {
    title: 'Encrypted backup + restore',
    detail: 'Passphrase-protected archive you control.',
  },
];

export const considering: RoadmapItem[] = [
  {
    title: 'More Kenyan bank SMS parsers',
    detail: 'Equity, KCB, Co-op, Absa, DTB — as their SMS formats stabilise and are requested.',
  },
  {
    title: 'Recurring expense detection',
    detail: 'Flag likely subscriptions and recurring bills; user confirms before it becomes a rule.',
  },
  {
    title: 'Swahili UI translation',
    detail: 'Full app + website localisation. Community-reviewed.',
  },
  {
    title: 'Widgets: today\u2019s spend and remaining budget',
    detail: 'Home-screen glance. No new permissions.',
  },
];

export const notPlanned: RoadmapItem[] = [
  {
    title: 'Cloud sync from the shipped app',
    detail:
      'The app has no INTERNET permission and we intend to keep it that way. A separate opt-in cloud product may exist in the future — see /about.',
  },
  {
    title: 'Recommending specific stocks, brokers, or funds',
    detail: 'Off-limits. All investment framing stays as illustration, with assumptions visible.',
  },
  {
    title: 'Streaks, badges, or gamification that rewards more transactions',
    detail: 'We reward awareness and saving, not spending.',
  },
  {
    title: 'Selling anonymised data',
    detail: 'Never. It would defeat the point of the product.',
  },
];
